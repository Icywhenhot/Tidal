package net.superkat.tidal;

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
import net.superkat.tidal.duck.TidalWorld;
import net.superkat.tidal.event.ClientBlockUpdateEvent;
import net.superkat.tidal.particles.BigSplashParticle;
import net.superkat.tidal.particles.TidalSplashParticle;
import net.superkat.tidal.particles.SprayParticle;
import net.superkat.tidal.particles.WhiteSprayParticle;
import net.superkat.tidal.particles.debug.DebugShoreParticle;
import net.superkat.tidal.particles.debug.DebugWaterParticle;
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;
import net.superkat.tidal.sprite.TidalSpriteHandler;

public class TidalClient implements ClientModInitializer {

    public static TidalSpriteHandler TIDAL_SPRITE_HANDLER = new TidalSpriteHandler();

    // Reusing vanilla's "weather" render layer: translucent textured quads with
    // depth-write disabled, which lets overlapping wave quads blend cleanly
    // (the same property the old tripwire shader provided in 1.21.1).
    private static RenderType waveRenderLayer;

    private static RenderType getWaveRenderLayer() {
        if (waveRenderLayer == null) {
            waveRenderLayer = RenderTypes.entityTranslucent(TidalSpriteHandler.WAVE_ATLAS_ID, false);
        }
        return waveRenderLayer;
    }

    @Override
    public void onInitializeClient() {
        ParticleProviderRegistry.getInstance().register(TidalParticles.SPRAY_PARTICLE, SprayParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(TidalParticles.WHITE_SPRAY_PARTICLE, WhiteSprayParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(TidalParticles.SPLASH_PARTICLE, TidalSplashParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(TidalParticles.BIG_SPLASH_PARTICLE, BigSplashParticle.Factory::new);

        ParticleProviderRegistry.getInstance().register(TidalParticles.DEBUG_WATERBODY_PARTICLE, DebugWaterParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(TidalParticles.DEBUG_SHORELINE_PARTICLE, DebugShoreParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(TidalParticles.DEBUG_WAVEMOVEMENT_PARTICLE, DebugWaveMovementParticle.Factory::new);

        //Called after joining a world, or changing dimensions
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) -> {
            TidalWorld tidalWorld = (TidalWorld) world;
            tidalWorld.tidal$tidalWaveHandler().reloadNearbyChunks();
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            TidalWorld tidalWorld = (TidalWorld) world;
            tidalWorld.tidal$tidalWaveHandler().waterHandler.loadChunk(chunk);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            TidalWorld tidalWorld = (TidalWorld) world;
            tidalWorld.tidal$tidalWaveHandler().waterHandler.unloadChunk(chunk);
        });

        //Called when an individual block is updated(placed, broken, state changed, etc.)
        ClientBlockUpdateEvent.BLOCK_UPDATE.register((pos, state) -> {
            Minecraft client = Minecraft.getInstance();
            if(client.level == null || client.player == null) return;
            TidalWorld tidalWorld = (TidalWorld) client.level;
            tidalWorld.tidal$tidalWaveHandler().waterHandler.onBlockUpdate(pos, state);
        });

        //Called when the chunks are reloaded(f3+a, resource pack change, etc.)
        InvalidateRenderStateCallback.EVENT.register(() -> {
            //actually have to check for null stuff here because this could be in the title screen I think
            Minecraft client = Minecraft.getInstance();
            if(client.level == null || client.player == null) return;
            TidalWorld tidalWorld = (TidalWorld) client.level;
            tidalWorld.tidal$tidalWaveHandler().reloadNearbyChunks();
            tidalWorld.tidal$tidalWaveHandler().waterHandler.rebuild();
        });

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if(mc.level == null) return;
            TidalWorld tidalWorld = (TidalWorld) mc.level;
            RenderType layer = getWaveRenderLayer();
            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder buffer = tessellator.begin(layer.mode(), layer.format());

            tidalWorld.tidal$tidalWaveHandler().render(buffer, context);

            MeshData builtBuffer = buffer.build();
            if(builtBuffer == null) return;

            layer.draw(builtBuffer);
        });

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(TIDAL_SPRITE_HANDLER);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            TIDAL_SPRITE_HANDLER.clearAtlas();
        });

    }

}
