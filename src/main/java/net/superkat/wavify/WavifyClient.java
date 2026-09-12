package net.superkat.wavify;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.Tessellator;
import net.minecraft.resource.ResourceType;
import net.superkat.wavify.duck.WavifyWorld;
import net.superkat.wavify.event.ClientBlockUpdateEvent;
import net.superkat.wavify.particles.BigSplashParticle;
import net.superkat.wavify.particles.SplashParticle;
import net.superkat.wavify.particles.SprayParticle;
import net.superkat.wavify.sound.WavifySounds;
import net.superkat.wavify.sprite.WavifySpriteHandler;

public class WavifyClient implements ClientModInitializer {

    private static RenderLayer waveRenderLayer;

    private static RenderLayer getWaveRenderLayer() {
        if (waveRenderLayer == null) {
            waveRenderLayer = RenderLayers.entityTranslucent(WavifySpriteHandler.WAVE_ATLAS_ID, false);
        }
        return waveRenderLayer;
    }

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.SPRAY_PARTICLE, SprayParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.WHITE_SPRAY_PARTICLE, SprayParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.SPLASH_PARTICLE, SplashParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.BIG_SPLASH_PARTICLE, BigSplashParticle.Factory::new);

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> ClientState.worldChanged(world));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) {
                ClientState.SOUND.hardReset();
                return;
            }
            ClientState.SOUND.tick();
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> ClientState.wavesIn(world).waterHandler.loadChunk(chunk));

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> ClientState.wavesIn(world).waterHandler.unloadChunk(chunk));

        ClientBlockUpdateEvent.BLOCK_UPDATE.register((pos, state) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if(client.world == null || client.player == null) return;
            ClientState.wavesIn(client.world).waterHandler.onBlockUpdate(pos, state);
        });

        InvalidateRenderStateCallback.EVENT.register(() -> {

            MinecraftClient client = MinecraftClient.getInstance();
            if(client.world == null || client.player == null) return;
            ClientState.cachesInvalidated(client.world);
        });

        WorldRenderEvents.END_MAIN.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if(mc.world == null) return;
            RenderLayer layer = getWaveRenderLayer();
            Tessellator tessellator = Tessellator.getInstance();

            BufferBuilder buffer = tessellator.begin(layer.getDrawMode(), layer.getVertexFormat());

            ClientState.wavesIn(mc.world).render(buffer, context);

            BuiltBuffer builtBuffer = buffer.endNullable();
            if(builtBuffer == null) return;

            layer.draw(builtBuffer);
        });

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(ClientState.SPRITES);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ClientState.SPRITES.clearAtlas();
        });

    }

}
