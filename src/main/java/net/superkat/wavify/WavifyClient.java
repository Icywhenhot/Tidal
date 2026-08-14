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
import org.joml.Matrix4fStack;

@Mod(value = Wavify.MOD_ID, dist = Dist.CLIENT)
public class WavifyClient {
    public static WavifySpriteHandler WAVIFY_SPRITE_HANDLER = new WavifySpriteHandler();
    public static final WaveAmbientSoundManager SOUND_MANAGER = new WaveAmbientSoundManager();

    // entityTranslucent maps to gbuffers_entities_translucent under shaderpacks, which blends far more predictably than weather
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
        event.registerSpriteSet(WavifyParticles.WHITE_SPRAY_PARTICLE.get(), WhiteSprayParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.SPLASH_PARTICLE.get(), SplashParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.BIG_SPLASH_PARTICLE.get(), BigSplashParticle.Factory::new);

        event.registerSpriteSet(WavifyParticles.DEBUG_WATERBODY_PARTICLE.get(), DebugWaterParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.DEBUG_SHORELINE_PARTICLE.get(), DebugShoreParticle.Factory::new);
        event.registerSpriteSet(WavifyParticles.DEBUG_WAVEMOVEMENT_PARTICLE.get(), DebugWaveMovementParticle.Factory::new);
    }

    private void registerReloadListeners(AddClientReloadListenersEvent event) {
        Identifier key = Identifier.fromNamespaceAndPath(Wavify.MOD_ID, "wave_sprites");
        event.addListener(key, WAVIFY_SPRITE_HANDLER);
        event.addDependency(VanillaClientListeners.TEXTURES, key);
        event.addDependency(key, VanillaClientListeners.LEVEL_EXTRACTOR);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            SOUND_MANAGER.hardReset();
            return;
        }
        SOUND_MANAGER.tick();
    }

    private void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ((WavifyWorld) level).wavify$wavifyWaveHandler().tick();
        }
    }

    private void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ((WavifyWorld) level).wavify$wavifyWaveHandler().reloadNearbyChunks();
            SOUND_MANAGER.hardReset();
        }
    }

    private void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            SOUND_MANAGER.hardReset();
        }
    }

    private void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ((WavifyWorld) level).wavify$wavifyWaveHandler().waterHandler.loadChunk(event.getChunk());
        }
    }

    private void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ((WavifyWorld) level).wavify$wavifyWaveHandler().waterHandler.unloadChunk(event.getChunk());
        }
    }

    // drawing after translucent terrain keeps waves on top of water instead of punching a hole through it
    private void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        WavifyWorld wavifyWorld = (WavifyWorld) mc.level;
        RenderType layer = getWaveRenderLayer();
        StagedVertexBuffer buffer = getWaveBuffer();

        StagedVertexBuffer.Draw draw = buffer.appendDraw(layer.format(), layer.primitiveTopology());
        VertexConsumer consumer = buffer.getVertexBuilder(draw);

        wavifyWorld.wavify$wavifyWaveHandler().render(consumer);

        // upload() sets the draw's vertex count, so it has to run before isEmpty() reads it
        buffer.upload();
        if (!draw.isEmpty()) {
            // the camera matrix is already popped by this stage, so restore it or the vertices project to nowhere
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
        SOUND_MANAGER.hardReset();
    }

    private void onGameShuttingDown(GameShuttingDownEvent event) {
        WAVIFY_SPRITE_HANDLER.clearAtlas();
        if (waveBuffer != null) {
            waveBuffer.close();
            waveBuffer = null;
        }
    }
}
