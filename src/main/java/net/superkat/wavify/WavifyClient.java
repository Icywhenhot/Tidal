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
import net.superkat.wavify.duck.WavifyWorld;
import net.superkat.wavify.event.ClientBlockUpdateEvent;
import net.superkat.wavify.particles.BigSplashParticle;
import net.superkat.wavify.particles.SplashParticle;
import net.superkat.wavify.particles.SprayParticle;
import net.superkat.wavify.particles.WhiteSprayParticle;
import net.superkat.wavify.particles.debug.DebugShoreParticle;
import net.superkat.wavify.particles.debug.DebugWaterParticle;
import net.superkat.wavify.particles.debug.DebugWaveMovementParticle;
import net.superkat.wavify.sound.WaveAmbientSoundManager;
import net.superkat.wavify.sprite.WavifySpriteHandler;

public class WavifyClient implements ClientModInitializer {

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

    // 26.2 removed immediate-mode Tesselator/RenderType.draw, and the submit-node
    // pipeline (submitCustomGeometry) only ever draws BEFORE translucent water — so
    // waves submitted that way get hidden by / punch holes in the water. Instead we
    // draw the waves immediately at AFTER_TRANSLUCENT_TERRAIN via a StagedVertexBuffer
    // + RenderType.prepare().drawFromBuffer(), which is the direct replacement for the
    // old RenderType.draw(MeshData) and reproduces the proven "depth-write entity
    // translucent, drawn after water" behaviour: waves sit on top of the water.
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
        ParticleProviderRegistry.getInstance().register(WavifyParticles.WHITE_SPRAY_PARTICLE, WhiteSprayParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.SPLASH_PARTICLE, SplashParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.BIG_SPLASH_PARTICLE, BigSplashParticle.Factory::new);

        ParticleProviderRegistry.getInstance().register(WavifyParticles.DEBUG_WATERBODY_PARTICLE, DebugWaterParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.DEBUG_SHORELINE_PARTICLE, DebugShoreParticle.Factory::new);
        ParticleProviderRegistry.getInstance().register(WavifyParticles.DEBUG_WAVEMOVEMENT_PARTICLE, DebugWaveMovementParticle.Factory::new);

        //Called after joining a world, or changing dimensions
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) -> {
            WavifyWorld wavifyWorld = (WavifyWorld) world;
            wavifyWorld.wavify$wavifyWaveHandler().reloadNearbyChunks();
            SOUND_MANAGER.hardReset();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) {
                SOUND_MANAGER.hardReset();
                return;
            }
            SOUND_MANAGER.tick();
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            WavifyWorld wavifyWorld = (WavifyWorld) world;
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.loadChunk(chunk);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            WavifyWorld wavifyWorld = (WavifyWorld) world;
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.unloadChunk(chunk);
        });

        //Called when an individual block is updated(placed, broken, state changed, etc.)
        ClientBlockUpdateEvent.BLOCK_UPDATE.register((pos, state) -> {
            Minecraft client = Minecraft.getInstance();
            if(client.level == null || client.player == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) client.level;
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.onBlockUpdate(pos, state);
        });

        //Called when the chunks are reloaded(f3+a, resource pack change, etc.)
        InvalidateRenderStateCallback.EVENT.register(() -> {
            //actually have to check for null stuff here because this could be in the title screen I think
            Minecraft client = Minecraft.getInstance();
            if(client.level == null || client.player == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) client.level;
            wavifyWorld.wavify$wavifyWaveHandler().reloadNearbyChunks();
            wavifyWorld.wavify$wavifyWaveHandler().waterHandler.rebuild();
        });

        // Render at AFTER_TRANSLUCENT_TERRAIN (after the main world pass, including
        // translucent water) so waves overlay water properly. entityTranslucent
        // writes depth; drawing after water keeps the wave on top of the surface.
        //
        // Immediate draw via StagedVertexBuffer is the 26.2 replacement for the old
        // RenderType.draw(MeshData): build a draw, fill it through a VertexConsumer,
        // upload, then prepare().drawFromBuffer(). endFrame() recycles the GPU buffer
        // pools each frame.
        //
        // prepare() snapshots RenderSystem's modelview, but by AFTER_TRANSLUCENT_TERRAIN
        // the camera matrix LevelRenderer pushed onto the modelview stack has already
        // been popped, so it reads as (effectively) identity and the camera-relative
        // wave vertices project to nowhere — completely invisible. We restore the exact
        // matrix terrain/water was drawn with — CameraRenderState.viewRotationMatrix —
        // onto the modelview stack for our draw, then pop it back.
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if(mc.level == null) return;
            WavifyWorld wavifyWorld = (WavifyWorld) mc.level;
            RenderType layer = getWaveRenderLayer();
            StagedVertexBuffer buffer = getWaveBuffer();

            StagedVertexBuffer.Draw draw = buffer.appendDraw(layer.format(), layer.primitiveTopology());
            VertexConsumer consumer = buffer.getVertexBuilder(draw);

            wavifyWorld.wavify$wavifyWaveHandler().render(consumer, context);

            // upload() finalizes the vertex builder and sets the draw's vertex count.
            // It MUST run before draw.isEmpty(), which reads that count — otherwise the
            // count is still 0, the draw looks empty, and nothing ever renders.
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

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(WAVIFY_SPRITE_HANDLER);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            WAVIFY_SPRITE_HANDLER.clearAtlas();
            if (waveBuffer != null) {
                waveBuffer.close();
                waveBuffer = null;
            }
        });

    }

}
