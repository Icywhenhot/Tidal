package net.superkat.wavify;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.packs.PackType;
import net.superkat.wavify.duck.WavifyWorld;
import net.superkat.wavify.particles.BigSplashParticle;
import net.superkat.wavify.particles.SplashParticle;
import net.superkat.wavify.particles.SprayParticle;
import net.superkat.wavify.particles.WhiteSprayParticle;
import net.superkat.wavify.particles.debug.DebugShoreParticle;
import net.superkat.wavify.particles.debug.DebugWaterParticle;
import net.superkat.wavify.particles.debug.DebugWaveMovementParticle;
import net.superkat.wavify.sound.WaveAmbientSoundManager;
import net.superkat.wavify.sprite.WavifySpriteHandler;

public class WavifyClient implements ClientModInitializer {

    public static WavifySpriteHandler WAVIFY_SPRITE_HANDLER = new WavifySpriteHandler();
    public static final WaveAmbientSoundManager SOUND_MANAGER = new WaveAmbientSoundManager();

    private static RenderType waveRenderLayer;
    private static ClientLevel lastLevel;

    public static RenderType getWaveRenderLayer() {
        if (waveRenderLayer == null) {
            waveRenderLayer = RenderType.entityTranslucent(WavifySpriteHandler.WAVE_ATLAS_ID);
        }
        return waveRenderLayer;
    }

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.SPRAY_PARTICLE, SprayParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.WHITE_SPRAY_PARTICLE, WhiteSprayParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.SPLASH_PARTICLE, SplashParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.BIG_SPLASH_PARTICLE, BigSplashParticle.Factory::new);

        ParticleFactoryRegistry.getInstance().register(WavifyParticles.DEBUG_WATERBODY_PARTICLE, DebugWaterParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.DEBUG_SHORELINE_PARTICLE, DebugShoreParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.DEBUG_WAVEMOVEMENT_PARTICLE, DebugWaveMovementParticle.Factory::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != lastLevel) {
                lastLevel = client.level;
                SOUND_MANAGER.hardReset();
                if (client.level != null) {
                    ((WavifyWorld) client.level).wavify$wavifyWaveHandler().reloadNearbyChunks();
                }
            }

            if (client.level == null || client.player == null) {
                SOUND_MANAGER.hardReset();
                return;
            }
            SOUND_MANAGER.tick();
        });

        ClientTickEvents.END_WORLD_TICK.register(level -> ((WavifyWorld) level).wavify$wavifyWaveHandler().tick());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SOUND_MANAGER.hardReset());

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
                ((WavifyWorld) level).wavify$wavifyWaveHandler().waterHandler.loadChunk(chunk));

        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                ((WavifyWorld) level).wavify$wavifyWaveHandler().waterHandler.unloadChunk(chunk));

        InvalidateRenderStateCallback.EVENT.register(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.player == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) client.level;
            wavifyWorld.wavify$wavifyWaveHandler().reloadNearbyChunks();
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.rebuild();
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (context.world() == null) return;

            WavifyWorld wavifyWorld = (WavifyWorld) context.world();
            RenderType layer = getWaveRenderLayer();

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer = tesselator.getBuilder();
            buffer.begin(layer.mode(), layer.format());

            wavifyWorld.wavify$wavifyWaveHandler().render(context.matrixStack(), buffer);

            BufferBuilder.RenderedBuffer builtBuffer = buffer.endOrDiscardIfEmpty();
            if (builtBuffer == null) return;

            layer.setupRenderState();
            BufferUploader.drawWithShader(builtBuffer);
            layer.clearRenderState();
        });

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(WAVIFY_SPRITE_HANDLER);
    }
}
