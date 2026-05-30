package net.superkat.wavify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.Tesselator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.resources.Identifier;
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

@Mod(value = Wavify.MOD_ID, dist = Dist.CLIENT)
public class WavifyClient {

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
    private static RenderType waveRenderLayer;

    private static RenderType getWaveRenderLayer() {
        if (waveRenderLayer == null) {
            waveRenderLayer = RenderTypes.entityTranslucent(WavifySpriteHandler.WAVE_ATLAS_ID, false);
        }
        return waveRenderLayer;
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
        event.addDependency(key, VanillaClientListeners.LEVEL_RENDERER);
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

    private void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        WavifyWorld wavifyWorld = (WavifyWorld) mc.level;
        RenderType layer = getWaveRenderLayer();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.begin(layer.mode(), layer.format());

        wavifyWorld.wavify$wavifyWaveHandler().render(buffer);

        MeshData builtBuffer = buffer.build();
        if (builtBuffer == null) return;

        layer.draw(builtBuffer);
    }

    private void onPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SOUND_MANAGER.hardReset();
    }
}
