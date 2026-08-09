package net.superkat.wavify;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.resource.ResourceType;
import net.superkat.wavify.duck.WavifyWorld;
import net.superkat.wavify.event.ClientBlockUpdateEvent;
import net.superkat.wavify.particles.BigSplashParticle;
import net.superkat.wavify.particles.SplashParticle;
import net.superkat.wavify.particles.SprayParticle;
import net.superkat.wavify.particles.WhiteSprayParticle;
import net.superkat.wavify.particles.debug.DebugShoreParticle;
import net.superkat.wavify.particles.debug.DebugWaterParticle;
import net.superkat.wavify.particles.debug.DebugWaveMovementParticle;
import net.superkat.wavify.sound.WaveAmbientSoundManager;
import net.superkat.wavify.sound.WavifySounds;
import net.superkat.wavify.sprite.WavifySpriteHandler;

public class WavifyClient implements ClientModInitializer {

    public static WavifySpriteHandler WAVIFY_SPRITE_HANDLER = new WavifySpriteHandler();
    public static final WaveAmbientSoundManager SOUND_MANAGER = new WaveAmbientSoundManager();

    // entityTranslucent maps to gbuffers_entities_translucent under shaderpacks and blends consistently
    // picked over weather because packs like Continuum, Helian and Photon treat weather really differently
    // don't swap this for a terrain program like tripwire, those are chunk layers
    // their vertex shader does pos = Position + ChunkOffset and wants the full block vertex format
    // we hand over camera relative positions, which only works while ChunkOffset is zero
    // vanilla zeroes it after the terrain pass so you get away with it, shaderpacks don't
    // iris gives back the pack's real terrain program, which reads mc_Entity and at_midBlock and friends
    // our buffer never binds those, and complementary waves blocks around based on them
    // that's what threw the wave quads into the sky
    private static RenderLayer waveRenderLayer;

    private static RenderLayer getWaveRenderLayer() {
        if (waveRenderLayer == null) {
            waveRenderLayer = RenderLayer.getEntityTranslucent(WavifySpriteHandler.WAVE_ATLAS_ID);
        }
        return waveRenderLayer;
    }

    // the shader specific bit happens per vertex over in WaveRenderer
    // it drops the wave a little when a pack is on so vanilla water covers it and gets shaded normally
    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.SPRAY_PARTICLE, SprayParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.WHITE_SPRAY_PARTICLE, WhiteSprayParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.SPLASH_PARTICLE, SplashParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.BIG_SPLASH_PARTICLE, BigSplashParticle.Factory::new);

        ParticleFactoryRegistry.getInstance().register(WavifyParticles.DEBUG_WATERBODY_PARTICLE, DebugWaterParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.DEBUG_SHORELINE_PARTICLE, DebugShoreParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.DEBUG_WAVEMOVEMENT_PARTICLE, DebugWaveMovementParticle.Factory::new);

        // joined a world or swapped dimensions
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            WavifyWorld wavifyWorld = (WavifyWorld) world;
            wavifyWorld.wavify$wavifyWaveHandler().reloadNearbyChunks();
            SOUND_MANAGER.hardReset();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) {
                SOUND_MANAGER.hardReset();
                return;
            }
            SOUND_MANAGER.tick();
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            WavifyWorld wavifyWorld = (WavifyWorld) world;
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.loadChunk(chunk);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            WavifyWorld wavifyWorld = (WavifyWorld) world;
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.unloadChunk(chunk);
        });

        // some block got placed, broken or otherwise changed
        ClientBlockUpdateEvent.BLOCK_UPDATE.register((pos, state) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if(client.world == null || client.player == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) client.world;
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.onBlockUpdate(pos, state);
        });

        // chunks got reloaded, f3+a or a resource pack swap
        InvalidateRenderStateCallback.EVENT.register(() -> {
            // need the null checks here, this can fire on the title screen i think
            MinecraftClient client = MinecraftClient.getInstance();
            if(client.world == null || client.player == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) client.world;
            wavifyWorld.wavify$wavifyWaveHandler().reloadNearbyChunks();
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.rebuild();
        });

        // render after the main world pass including translucent water so waves sit on top properly
        // entityTranslucent writes depth, and going before water made fade in quads punch a hole through it
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if(context.world() == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) context.world();
            RenderLayer layer = getWaveRenderLayer();
            Tessellator tessellator = Tessellator.getInstance();
            // pull draw mode and format off the layer so the buffer can't drift from the program it binds
            BufferBuilder buffer = tessellator.begin(layer.getDrawMode(), layer.getVertexFormat());

            wavifyWorld.wavify$wavifyWaveHandler().render(buffer, context);

            BuiltBuffer builtBuffer = buffer.endNullable();
            if(builtBuffer == null) return;

            // does shader, texture, blending, depth, lightmap and overlay setup for us
            layer.draw(builtBuffer);
        });

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(WAVIFY_SPRITE_HANDLER);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            WAVIFY_SPRITE_HANDLER.clearAtlas();
        });

    }

}
