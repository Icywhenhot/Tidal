package net.superkat.wavify;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhase;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.server.packs.PackType;
import net.superkat.wavify.event.ClientBlockUpdateEvent;
import net.superkat.wavify.particles.BigSplashParticle;
import net.superkat.wavify.particles.SplashParticle;
import net.superkat.wavify.particles.SprayParticle;
import net.superkat.wavify.sprite.WavifySpriteHandler;

public class WavifyClient implements ClientModInitializer {

    private static final SubmitRenderPhase<CustomFeatureRenderer.Submit> AFTER_TERRAIN =
            new SubmitRenderPhase<>(collection -> collection.afterTerrain);

    private static RenderType waveRenderLayer;

    private static RenderType getWaveRenderLayer() {
        if (waveRenderLayer == null) {
            waveRenderLayer = RenderTypes.entityTranslucent(WavifySpriteHandler.WAVE_ATLAS_ID, false);
        }
        return waveRenderLayer;
    }

    @Override
    public void onInitializeClient() {
        ParticleProviderRegistry.getInstance().register(WavifyParticles.SPRAY_PARTICLE, SprayParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.WHITE_SPRAY_PARTICLE, SprayParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.SPLASH_PARTICLE, SplashParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.BIG_SPLASH_PARTICLE, BigSplashParticle.Factory::new);

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) -> {
            ClientState.worldChanged(world);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) {
                ClientState.SOUND.hardReset();
                return;
            }
            ClientState.SOUND.tick();
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            ClientState.wavesIn(world).waterHandler.loadChunk(chunk);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            ClientState.wavesIn(world).waterHandler.unloadChunk(chunk);
        });

        ClientBlockUpdateEvent.BLOCK_UPDATE.register((pos, state) -> {
            Minecraft client = Minecraft.getInstance();
            if(client.level == null || client.player == null) return;
            ClientState.wavesIn(client.level).waterHandler.onBlockUpdate(pos, state);
        });

        InvalidateRenderStateCallback.EVENT.register(() -> {
            Minecraft client = Minecraft.getInstance();
            if(client.level == null || client.player == null) return;
            ClientState.cachesInvalidated(client.level);
        });

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if(mc.level == null) return;
            context.submitNodeCollector().submitCustom(AFTER_TERRAIN, new CustomFeatureRenderer.Submit(
                    context.poseStack().last().copy(), getWaveRenderLayer(),
                    (pose, consumer) -> ClientState.wavesIn(mc.level).render(consumer, context)));
        });

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(ClientState.SPRITES);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ClientState.SPRITES.clearAtlas();
        });

    }

}
