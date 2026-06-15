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
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
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

    // Using entityTranslucent: maps to gbuffers_entities_translucent under
    // shaderpacks, gives consistent blending + depth-test-on/write-off. Picked
    // over weather (gbuffers_weather) because several packs (Continuum, Helian,
    // Photon) treat weather very differently from translucent geometry.
    //
    // Shader-mode behavior is handled at vertex level in WaveRenderer:
    // IrisCompat.isShaderPackActive() lowers wave Y slightly so vanilla water
    // (which the shaderpack reflects/refracts) renders on top of the wave,
    // giving the wave color/foam the same shader-water treatment as the rest
    // of the surface. See WaveRenderer#shaderYOffset.
    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.SPRAY_PARTICLE, SprayParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.WHITE_SPRAY_PARTICLE, WhiteSprayParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.SPLASH_PARTICLE, SplashParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.BIG_SPLASH_PARTICLE, BigSplashParticle.Factory::new);

        ParticleFactoryRegistry.getInstance().register(WavifyParticles.DEBUG_WATERBODY_PARTICLE, DebugWaterParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.DEBUG_SHORELINE_PARTICLE, DebugShoreParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.DEBUG_WAVEMOVEMENT_PARTICLE, DebugWaveMovementParticle.Factory::new);

        //Called after joining a world, or changing dimensions
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

        //Called when an individual block is updated(placed, broken, state changed, etc.)
        ClientBlockUpdateEvent.BLOCK_UPDATE.register((pos, state) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if(client.world == null || client.player == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) client.world;
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.onBlockUpdate(pos, state);
        });

        //Called when the chunks are reloaded(f3+a, resource pack change, etc.)
        InvalidateRenderStateCallback.EVENT.register(() -> {
            //actually have to check for null stuff here because this could be in the title screen I think
            MinecraftClient client = MinecraftClient.getInstance();
            if(client.world == null || client.player == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) client.world;
            wavifyWorld.wavify$wavifyWaveHandler().reloadNearbyChunks();
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.rebuild();
        });

        // Render at END_MAIN (after the main world pass, including translucent
        // water) so waves overlay water properly. The new entityTranslucent
        // layer writes depth, and rendering before water caused fade-in quads
        // to punch a hole through the water surface.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if(context.world() == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) context.world();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);

            wavifyWorld.wavify$wavifyWaveHandler().render(buffer, context);

            BuiltBuffer builtBuffer = buffer.endNullable();
            if(builtBuffer == null) return;

            LightmapTextureManager lightmapTextureManager = MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager();
            lightmapTextureManager.enable();

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.setShader(GameRenderer::getRenderTypeTripwireProgram);
            RenderSystem.setShaderTexture(0, WavifySpriteHandler.WAVE_ATLAS_ID);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            BufferRenderer.drawWithGlobalProgram(builtBuffer);

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            lightmapTextureManager.disable();
        });

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(WAVIFY_SPRITE_HANDLER);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            WAVIFY_SPRITE_HANDLER.clearAtlas();
        });

    }

}
