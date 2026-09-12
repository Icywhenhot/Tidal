package net.superkat.wavify;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fStack;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.server.packs.PackType;
import net.superkat.wavify.event.ClientBlockUpdateEvent;
import net.superkat.wavify.particles.BigSplashParticle;
import net.superkat.wavify.particles.SplashParticle;
import net.superkat.wavify.particles.SprayParticle;
import net.superkat.wavify.sprite.WavifySpriteHandler;

public class WavifyClient implements ClientModInitializer {

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

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if(mc.level == null) return;
            RenderType layer = getWaveRenderLayer();
            StagedVertexBuffer buffer = getWaveBuffer();

            StagedVertexBuffer.Draw draw = buffer.appendDraw(layer.format(), layer.primitiveTopology());
            VertexConsumer consumer = buffer.getVertexBuilder(draw);

            ClientState.wavesIn(mc.level).render(consumer, context);

            buffer.upload();
            if(!draw.isEmpty()) {
                CameraRenderState camera = context.levelState().cameraRenderState;
                Matrix4fStack mvStack = RenderSystem.getModelViewStack();
                mvStack.pushMatrix();
                mvStack.set(camera.viewRotationMatrix);
                layer.prepare().drawFromBuffer(buffer.getExecuteInfo(draw));
                mvStack.popMatrix();
            }
            buffer.endFrame();
        });

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(ClientState.SPRITES);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ClientState.SPRITES.clearAtlas();
        });

    }

}
