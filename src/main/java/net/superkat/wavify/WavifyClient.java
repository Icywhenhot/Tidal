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
import net.superkat.wavify.particles.BigSplashParticle;
import net.superkat.wavify.particles.SplashParticle;
import net.superkat.wavify.particles.SprayParticle;
import net.superkat.wavify.sprite.WavifySpriteHandler;

public class WavifyClient implements ClientModInitializer {

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
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.WHITE_SPRAY_PARTICLE, SprayParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.SPLASH_PARTICLE, SplashParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(WavifyParticles.BIG_SPLASH_PARTICLE, BigSplashParticle.Factory::new);


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != lastLevel) {
                lastLevel = client.level;
                ClientState.worldChanged(client.level);
            }

            if (client.level == null || client.player == null) {
                ClientState.SOUND.hardReset();
                return;
            }
            ClientState.SOUND.tick();
        });

        ClientTickEvents.END_WORLD_TICK.register(level -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            ClientState.wavesIn(level).tick();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.disconnected());

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
                ClientState.wavesIn(level).waterHandler.loadChunk(chunk));

        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                ClientState.wavesIn(level).waterHandler.unloadChunk(chunk));

        InvalidateRenderStateCallback.EVENT.register(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.player == null) return;
            ClientState.cachesInvalidated(client.level);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (context.world() == null) return;

            RenderType layer = getWaveRenderLayer();

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer = tesselator.getBuilder();
            buffer.begin(layer.mode(), layer.format());

            ClientState.wavesIn(context.world()).render(context.matrixStack(), buffer);

            BufferBuilder.RenderedBuffer builtBuffer = buffer.endOrDiscardIfEmpty();
            if (builtBuffer == null) return;

            layer.setupRenderState();
            BufferUploader.drawWithShader(builtBuffer);
            layer.clearRenderState();
        });

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(ClientState.SPRITES);
    }
}
