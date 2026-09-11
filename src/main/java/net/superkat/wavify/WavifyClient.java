package net.superkat.wavify;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
import net.superkat.wavify.particles.BigSplashParticle;
import net.superkat.wavify.particles.SplashParticle;
import net.superkat.wavify.particles.SprayParticle;
import net.superkat.wavify.sprite.WavifySpriteHandler;

public class WavifyClient {

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
        event.registerSpriteSet(WavifyParticles.WHITE_SPRAY_PARTICLE.get(), SprayParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.SPLASH_PARTICLE.get(), SplashParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.BIG_SPLASH_PARTICLE.get(), BigSplashParticle.Factory::new);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ClientState.SPRITES);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            ClientState.SOUND.hardReset();
            return;
        }
        ClientState.SOUND.tick();
    }

    private static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().player == null) return;
        if (event.level instanceof ClientLevel level) {
            ClientState.wavesIn(level).tick();
        }
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientState.worldChanged(level);
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            ClientState.disconnected();
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientState.wavesIn(level).waterHandler.loadChunk(event.getChunk());
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientState.wavesIn(level).waterHandler.unloadChunk(event.getChunk());
        }
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RenderType layer = getWaveRenderLayer();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        buffer.begin(layer.mode(), layer.format());

        ClientState.wavesIn(mc.level).render(event.getPoseStack(), buffer);

        BufferBuilder.RenderedBuffer builtBuffer = buffer.endOrDiscardIfEmpty();
        if (builtBuffer == null) return;

        layer.setupRenderState();
        BufferUploader.drawWithShader(builtBuffer);
        layer.clearRenderState();
    }

    private static void onPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.disconnected();
    }
}
