package net.superkat.wavify;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.superkat.wavify.sprite.WavifySpriteHandler;
import net.superkat.wavify.particles.BigSplashParticle;
import net.superkat.wavify.particles.SplashParticle;
import net.superkat.wavify.particles.SprayParticle;
import org.joml.Matrix4fStack;

@Mod(value = Wavify.MOD_ID, dist = Dist.CLIENT)
public class WavifyClient {

    private static RenderType waveRenderLayer;

    private static StagedVertexBuffer waveBuffer;

    private static RenderType getWaveRenderLayer() {
        if (waveRenderLayer == null) {
            waveRenderLayer = RenderTypes.entityTranslucent(WavifySpriteHandler.WAVE_ATLAS_ID, false);
        }
        return waveRenderLayer;
    }

    private static StagedVertexBuffer getWaveBuffer() {
        if (waveBuffer == null) {
            waveBuffer = new StagedVertexBuffer(() -> "wavify_waves", RenderType.TRANSIENT_BUFFER_SIZE);
        }
        return waveBuffer;
    }

    public WavifyClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(this::registerParticleProviders);
        modEventBus.addListener(this::registerReloadListeners);

        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onLevelTick);
        NeoForge.EVENT_BUS.addListener(this::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(this::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(this::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(this::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(this::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggingOut);
        NeoForge.EVENT_BUS.addListener(this::onGameShuttingDown);
    }

    private void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(WavifyParticles.SPRAY_PARTICLE.get(), SprayParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.WHITE_SPRAY_PARTICLE.get(), SprayParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.SPLASH_PARTICLE.get(), SplashParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.BIG_SPLASH_PARTICLE.get(), BigSplashParticle.Factory::new);

    }

    private void registerReloadListeners(AddClientReloadListenersEvent event) {
        Identifier key = Identifier.fromNamespaceAndPath(Wavify.MOD_ID, "wave_sprites");
        event.addListener(key, ClientState.SPRITES);
        event.addDependency(VanillaClientListeners.TEXTURES, key);
        event.addDependency(key, VanillaClientListeners.LEVEL_EXTRACTOR);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            ClientState.SOUND.hardReset();
            return;
        }
        ClientState.SOUND.tick();
    }

    private void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientState.wavesIn(level).tick();
        }
    }

    private void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientState.wavesIn(level).reloadNearbyChunks();
            ClientState.SOUND.hardReset();
        }
    }

    private void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            ClientState.SOUND.hardReset();
        }
    }

    private void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientState.wavesIn(level).waterHandler.loadChunk(event.getChunk());
        }
    }

    private void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientState.wavesIn(level).waterHandler.unloadChunk(event.getChunk());
        }
    }

    private void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RenderType layer = getWaveRenderLayer();
        StagedVertexBuffer buffer = getWaveBuffer();

        StagedVertexBuffer.Draw draw = buffer.appendDraw(layer.format(), layer.primitiveTopology());
        VertexConsumer consumer = buffer.getVertexBuilder(draw);

        ClientState.wavesIn(mc.level).render(consumer);

        buffer.upload();
        if (!draw.isEmpty()) {
            CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
            Matrix4fStack mvStack = RenderSystem.getModelViewStack();
            mvStack.pushMatrix();
            mvStack.set(camera.viewRotationMatrix);
            layer.prepare().drawFromBuffer(buffer.getExecuteInfo(draw));
            mvStack.popMatrix();
        }
        buffer.endFrame();
    }

    private void onPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.SOUND.hardReset();
    }

    private void onGameShuttingDown(GameShuttingDownEvent event) {
        ClientState.SPRITES.clearAtlas();
        if (waveBuffer != null) {
            waveBuffer.close();
            waveBuffer = null;
        }
    }
}
