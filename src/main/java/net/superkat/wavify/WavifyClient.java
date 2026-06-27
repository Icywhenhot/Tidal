package net.superkat.wavify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
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

public class WavifyClient {

    public static WavifySpriteHandler WAVIFY_SPRITE_HANDLER = new WavifySpriteHandler();
    public static final WaveAmbientSoundManager SOUND_MANAGER = new WaveAmbientSoundManager();

    private static RenderType waveRenderLayer;

    public static RenderType getWaveRenderLayer() {
        if (waveRenderLayer == null) {
            waveRenderLayer = RenderType.entityTranslucent(WavifySpriteHandler.WAVE_ATLAS_ID);
        }
        return waveRenderLayer;
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(WavifyClient::registerParticleProviders);
        modEventBus.addListener(WavifyClient::registerReloadListeners);

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener(WavifyClient::onClientTick);
        forgeBus.addListener(WavifyClient::onLevelTick);
        forgeBus.addListener(WavifyClient::onLevelLoad);
        forgeBus.addListener(WavifyClient::onLevelUnload);
        forgeBus.addListener(WavifyClient::onChunkLoad);
        forgeBus.addListener(WavifyClient::onChunkUnload);
        forgeBus.addListener(WavifyClient::onRenderLevelStage);
        forgeBus.addListener(WavifyClient::onPlayerLoggingOut);
    }

    private static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(WavifyParticles.SPRAY_PARTICLE.get(), SprayParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.WHITE_SPRAY_PARTICLE.get(), WhiteSprayParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.SPLASH_PARTICLE.get(), SplashParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.BIG_SPLASH_PARTICLE.get(), BigSplashParticle.Factory::new);

        event.registerSpriteSet(WavifyParticles.DEBUG_WATERBODY_PARTICLE.get(), DebugWaterParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.DEBUG_SHORELINE_PARTICLE.get(), DebugShoreParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.DEBUG_WAVEMOVEMENT_PARTICLE.get(), DebugWaveMovementParticle.Factory::new);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(WAVIFY_SPRITE_HANDLER);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            SOUND_MANAGER.hardReset();
            return;
        }
        SOUND_MANAGER.tick();
    }

    private static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level instanceof ClientLevel level) {
            ((WavifyWorld) level).wavify$wavifyWaveHandler().tick();
        }
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ((WavifyWorld) level).wavify$wavifyWaveHandler().reloadNearbyChunks();
            SOUND_MANAGER.hardReset();
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            SOUND_MANAGER.hardReset();
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ((WavifyWorld) level).wavify$wavifyWaveHandler().waterHandler.loadChunk(event.getChunk());
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ((WavifyWorld) level).wavify$wavifyWaveHandler().waterHandler.unloadChunk(event.getChunk());
        }
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        WavifyWorld wavifyWorld = (WavifyWorld) mc.level;
        RenderType layer = getWaveRenderLayer();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        wavifyWorld.wavify$wavifyWaveHandler().render(event.getPoseStack(), bufferSource, layer);

        bufferSource.endBatch(layer);
    }

    private static void onPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SOUND_MANAGER.hardReset();
    }
}
