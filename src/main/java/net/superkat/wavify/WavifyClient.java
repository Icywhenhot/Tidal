package net.superkat.wavify;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.server.packs.PackType;
import net.superkat.wavify.duck.WavifyWorld;
import net.superkat.wavify.event.ClientBlockUpdateEvent;
import net.superkat.wavify.particles.BigSplashParticle;
import net.superkat.wavify.particles.WavifySplashParticle;
import net.superkat.wavify.particles.SprayParticle;
import net.superkat.wavify.particles.WhiteSprayParticle;
import net.superkat.wavify.particles.debug.DebugShoreParticle;
import net.superkat.wavify.particles.debug.DebugWaterParticle;
import net.superkat.wavify.particles.debug.DebugWaveMovementParticle;
import net.superkat.wavify.sprite.WavifySpriteHandler;

public class WavifyClient implements ClientModInitializer {

    public static WavifySpriteHandler WAVIFY_SPRITE_HANDLER = new WavifySpriteHandler();

    // Reusing vanilla's "weather" render layer: translucent textured quads with
    // depth-write disabled, which lets overlapping wave quads blend cleanly
    // (the same property the old tripwire shader provided in 1.21.1).
    private static RenderType waveRenderLayer;

    private static RenderType getWaveRenderLayer() {
        if (waveRenderLayer == null) {
            waveRenderLayer = RenderTypes.entityTranslucent(WavifySpriteHandler.WAVE_ATLAS_ID, false);
        }
        return waveRenderLayer;
    }

    @Override
    public void onInitializeClient() {
        ParticleProviderRegistry.getInstance().register(WavifyParticles.SPRAY_PARTICLE, SprayParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.WHITE_SPRAY_PARTICLE, WhiteSprayParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.SPLASH_PARTICLE, WavifySplashParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.BIG_SPLASH_PARTICLE, BigSplashParticle.Factory::new);

        ParticleProviderRegistry.getInstance().register(WavifyParticles.DEBUG_WATERBODY_PARTICLE, DebugWaterParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.DEBUG_SHORELINE_PARTICLE, DebugShoreParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.DEBUG_WAVEMOVEMENT_PARTICLE, DebugWaveMovementParticle.Factory::new);

        //Called after joining a world, or changing dimensions
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) -> {
            WavifyWorld wavifyWorld = (WavifyWorld) world;
            wavifyWorld.wavify$wavifyWaveHandler().reloadNearbyChunks();
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            WavifyWorld wavifyWorld = (WavifyWorld) world;
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.loadChunk(chunk);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            WavifyWorld wavifyWorld = (WavifyWorld) world;
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.unloadChunk(chunk);
        });

        //Called when an individual block is updated(placed, broken, state changed, etc.)
        ClientBlockUpdateEvent.BLOCK_UPDATE.register((pos, state) -> {
            Minecraft client = Minecraft.getInstance();
            if(client.level == null || client.player == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) client.level;
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.onBlockUpdate(pos, state);
        });

        //Called when the chunks are reloaded(f3+a, resource pack change, etc.)
        InvalidateRenderStateCallback.EVENT.register(() -> {
            //actually have to check for null stuff here because this could be in the title screen I think
            Minecraft client = Minecraft.getInstance();
            if(client.level == null || client.player == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) client.level;
            wavifyWorld.wavify$wavifyWaveHandler().reloadNearbyChunks();
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.rebuild();
        });

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if(mc.level == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) mc.level;
            RenderType layer = getWaveRenderLayer();
            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder buffer = tessellator.begin(layer.mode(), layer.format());

            wavifyWorld.wavify$wavifyWaveHandler().render(buffer, context);

            MeshData builtBuffer = buffer.build();
            if(builtBuffer == null) return;

            layer.draw(builtBuffer);
        });

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(WAVIFY_SPRITE_HANDLER);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            WAVIFY_SPRITE_HANDLER.clearAtlas();
        });

    }

}
