package net.superkat.tidal;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceType;
import net.superkat.tidal.duck.TidalWorld;
import net.superkat.tidal.event.ClientBlockUpdateEvent;
import net.superkat.tidal.particles.SprayParticle;
import net.superkat.tidal.particles.WhiteSprayParticle;
import net.superkat.tidal.particles.debug.DebugShoreParticle;
import net.superkat.tidal.particles.debug.DebugWaterParticle;
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;
import net.superkat.tidal.particles.old.WaveParticle;
import net.superkat.tidal.particles.old.WhiteWaveParticle;
import net.superkat.tidal.sprite.TidalSpriteHandler;

public class TidalClient implements ClientModInitializer {

    public static TidalSpriteHandler TIDAL_SPRITE_HANDLER = new TidalSpriteHandler();

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(TidalParticles.SPRAY_PARTICLE, SprayParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(TidalParticles.WHITE_SPRAY_PARTICLE, WhiteSprayParticle.Factory::new);


        ParticleFactoryRegistry.getInstance().register(TidalParticles.WAVE_PARTICLE, WaveParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(TidalParticles.WHITE_WAVE_PARTICLE, WhiteWaveParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(TidalParticles.DEBUG_WATERBODY_PARTICLE, DebugWaterParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(TidalParticles.DEBUG_SHORELINE_PARTICLE, DebugShoreParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(TidalParticles.DEBUG_WAVEMOVEMENT_PARTICLE, DebugWaveMovementParticle.Factory::new);

        //Called after joining a world, or changing dimensions
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
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
            MinecraftClient client = MinecraftClient.getInstance();
            if(client.world == null || client.player == null) return;
            TidalWorld tidalWorld = (TidalWorld) client.world;
            tidalWorld.tidal$tidalWaveHandler().waterHandler.onBlockUpdate(pos, state);
        });

        //Called when the chunks are reloaded(f3+a, resource pack change, etc.)
        InvalidateRenderStateCallback.EVENT.register(() -> {
            //actually have to check for null stuff here because this could be in the title screen I think
            MinecraftClient client = MinecraftClient.getInstance();
            if(client.world == null || client.player == null) return;
            TidalWorld tidalWorld = (TidalWorld) client.world;
            tidalWorld.tidal$tidalWaveHandler().reloadNearbyChunks();
            tidalWorld.tidal$tidalWaveHandler().waterHandler.rebuild();
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if(context.world() == null) return;
            TidalWorld tidalWorld = (TidalWorld) context.world();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);

            tidalWorld.tidal$tidalWaveHandler().render(buffer, context);

            BuiltBuffer builtBuffer = buffer.endNullable();
            if(builtBuffer == null) return;

            LightmapTextureManager lightmapTextureManager = MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager();

            lightmapTextureManager.enable();

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.setShader(GameRenderer::getRenderTypeTripwireProgram);
            RenderSystem.setShaderTexture(0, TidalSpriteHandler.WAVE_ATLAS_ID);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            BufferRenderer.drawWithGlobalProgram(builtBuffer);

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            lightmapTextureManager.disable();
        });

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(TIDAL_SPRITE_HANDLER);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            TIDAL_SPRITE_HANDLER.clearAtlas();
        });

    }

}
