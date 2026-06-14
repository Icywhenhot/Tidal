package net.superkat.wavify.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import net.superkat.wavify.WavifyClient;
import net.superkat.wavify.compat.IrisCompat;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.sprite.WavifySpriteHandler;
import net.superkat.wavify.sprite.WavifySprites;
import net.superkat.wavify.wave.RiverWave;
import net.superkat.wavify.wave.WavifyWaveHandler;
import net.superkat.wavify.wave.Wave;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Set;

/**
 * THE WAVES AREN'T ENTITIES!!!!!!!!!!!!!!!!!!!!!!!!!!!
 */
public class WaveRenderer {
    private static final float WAVE_FOAM_Y_OFFSET = 0.08f;
    // Render-only Y sink for ocean waves so the visible body sits flusher with
    // the water surface. Does NOT touch collision Y — keeping the wave's actual
    // position high lets it transition into the washing-up phase and bounce
    // instead of crashing sideways into the shore block. River waves are
    // unaffected.
    private static final float OCEAN_RENDER_Y_SINK = -0.5f;

    // When an Iris/Oculus shaderpack is active, sink the colored wave body so
    // vanilla water still covers the wave footprint. The shaderpack then
    // renders its water reflections/refractions on top of (and tinted by) the
    // wave color, giving the wave the same shader-water look as the rest of
    // the surface. The foam quad stays at WAVE_FOAM_Y_OFFSET above the body so
    // the white crest still pokes through the water surface.
    //
    // Sink amount is user-tunable via WavifyConfig.shaderWaveYSink. Cached per
    // frame: isShaderPackActive() reflects into Iris, cheap but not free.
    //
    // frameBodyYOffset = waveYOffset + (shaderWaveYSink if shaders else 0)
    // frameFoamYOffset = waveYOffset
    // Foam never gets the shader sink; under shaders we WANT it at the water
    // surface so the shaderpack's water shading covers it.
    private float frameBodyYOffset = 0f;
    private float frameFoamYOffset = 0f;

    public WavifyWaveHandler handler;
    public WavifySpriteHandler spriteHandler;
    public ClientLevel level;

    public WaveRenderer(WavifyWaveHandler handler, ClientLevel level) {
        this.handler = handler;
        this.spriteHandler = WavifyClient.WAVIFY_SPRITE_HANDLER;
        this.level = level;
    }

    public void render(BufferBuilder buffer, LevelRenderContext context) {
        List<Wave> waves = this.handler.getWaves();
        if (waves == null || waves.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Camera camera = mc.gameRenderer.getMainCamera();

        float shaderSink = IrisCompat.isShaderPackActive() ? (float) WavifyConfig.shaderWaveYSink : 0f;
        float baseOffset = (float) WavifyConfig.waveYOffset;
        this.frameFoamYOffset = baseOffset;
        this.frameBodyYOffset = baseOffset + shaderSink;

        for (Wave wave : waves) {
            renderWave(buffer, camera, wave, tickDelta);
        }

        renderOverlays(buffer, camera, handler.coveredBlocks);
    }

    public void renderWave(BufferBuilder buffer, Camera camera, Wave wave, float delta) {
        if (wave == null) return;

        PoseStack matrices = new PoseStack();
        matrices.pushPose();

        Vec3 center = new Vec3(wave.getX(delta), wave.getY(delta), wave.getZ(delta));
        Vec3 cameraPos = camera.position();
        Vec3 transPos = center.subtract(cameraPos);

        matrices.pushPose();
        matrices.translate(transPos.x, transPos.y, transPos.z); // offsets to the wave's position
        matrices.mulPose(Axis.YP.rotationDegrees(-wave.getYaw(delta) + 90)); // rotate wave left/right
        matrices.mulPose(Axis.XP.rotationDegrees(wave.pitch));
        float scale = wave.scale;
        matrices.scale(scale, 1, scale);

        Matrix4f posMatrix = matrices.last().pose();

        boolean washingUp = wave.isWashingUp();
        TextureAtlasSprite colorableSprite = washingUp ? getTopWashingSprite() : getMovingSprite();
        TextureAtlasSprite whiteSprite = washingUp ? getTopWashingWhiteSprite() : getMovingWhiteSprite();

        int light = wave.getLight();

        float transparency = (float) WavifyConfig.transparency;
        float red = wave.red;
        float green = wave.green;
        float blue = wave.blue;
        float alpha = wave.alpha * transparency;
        float foamAlpha = WavifyConfig.applyTransparencyToFoam ? alpha : wave.alpha;

        int age = wave.getAge();
        int maxAge = wave.getMaxAge();

        boolean isOcean = !(wave instanceof RiverWave);
        float oceanSink = isOcean ? OCEAN_RENDER_Y_SINK : 0f;
        float bodyYOffset = frameBodyYOffset + oceanSink;
        float foamYOffset = frameFoamYOffset + oceanSink;

        renderWaveColumns(posMatrix, buffer, wave, colorableSprite, whiteSprite, age, maxAge, red, green, blue, alpha, foamAlpha, light, bodyYOffset, foamYOffset);

        // beneath wave texture after hitting shore
        if (washingUp && wave.bigWave) {
            TextureAtlasSprite washingColorableSprite = getBottomWashingSprite();
            TextureAtlasSprite washingWhiteSprite = getBottomWashingWhiteSprite();

            // this is beyond cursed but i'm really frustrated right now so its fine
            float ageDelta = (float) age / maxAge;
            float turnBackDelta = 0.5f;
            float washingLength = ageDelta > turnBackDelta ? Mth.lerp((ageDelta - turnBackDelta) * 2, 2f, 3f) : 2f;
            float washingZ = ageDelta > turnBackDelta ? Mth.lerp((ageDelta - turnBackDelta) * 2, 1.35f, 0) : 1.35f;
            matrices.scale(1.25f, 1, 1);
            for (int i = 0; i < wave.width; i++) {
                waveQuad(posMatrix, buffer, washingColorableSprite, age, maxAge, i - 0.15f, -0.05f + bodyYOffset, washingZ, 1, washingLength, red, green, blue, alpha, light);
                waveQuad(posMatrix, buffer, washingWhiteSprite, age, maxAge, i - 0.15f, -0.01f + foamYOffset, washingZ, 1, washingLength, 1f, 1f, 1f, foamAlpha, light);
            }
        }

        matrices.popPose();
    }

    private void renderWaveColumns(Matrix4f posMatrix, BufferBuilder buffer, Wave wave, TextureAtlasSprite colorableSprite, TextureAtlasSprite whiteSprite, int age, int maxAge, float red, float green, float blue, float alpha, float foamAlpha, int light, float bodyYOffset, float foamYOffset) {
        int columnCount = wave.getRenderColumnCount();

        for (int i = 0; i < columnCount; i++) {
            float x = wave.getRenderColumnLateralOffset(i, columnCount);
            float y = wave.getRenderColumnVerticalOffset(i, columnCount);
            float z = wave.getRenderColumnForwardOffset(i, columnCount);
            float width = wave.getRenderColumnWidth(i, columnCount);
            float length = wave.getRenderColumnLength(i, columnCount);
            waveQuad(posMatrix, buffer, colorableSprite, age, maxAge, x, y + bodyYOffset, z, width, length, red, green, blue, alpha, light);
            waveQuad(posMatrix, buffer, whiteSprite, age, maxAge, x, y + WAVE_FOAM_Y_OFFSET + foamYOffset, z, width, length, 1f, 1f, 1f, foamAlpha, light);
        }
    }

    private void waveQuad(Matrix4f matrix4f, BufferBuilder buffer, TextureAtlasSprite sprite, int waveAge, int waveMaxAge, float x, float y, float z, float width, float length, float red, float green, float blue, float alpha, int light) {
        float halfWidth = width / 2f;
        float halfLength = length / 2f;

        int frame = WavifySprites.getFrameFromAge(sprite, waveAge, waveMaxAge);
        float u0 = WavifySprites.getMinU(sprite);
        float u1 = WavifySprites.getMaxU(sprite);
        float v0 = WavifySprites.getMinV(sprite, frame);
        float v1 = WavifySprites.getMaxV(sprite, frame);

        buffer.addVertex(matrix4f, x - halfWidth, y, z - halfLength)
                .setColor(red, green, blue, alpha).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

        buffer.addVertex(matrix4f, x - halfWidth, y, z + halfLength)
                .setColor(red, green, blue, alpha).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

        buffer.addVertex(matrix4f, x + halfWidth, y, z + halfLength)
                .setColor(red, green, blue, alpha).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

        buffer.addVertex(matrix4f, x + halfWidth, y, z - halfLength)
                .setColor(red, green, blue, alpha).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);
    }

    public void renderOverlays(BufferBuilder buffer, Camera camera, Set<BlockPos> coveredBlocks) {
        for (BlockPos covered : coveredBlocks) {
            renderCoverOverlay(buffer, camera, covered);
        }
    }

    public void renderCoverOverlay(BufferBuilder buffer, Camera camera, BlockPos pos) {
        PoseStack matrices = new PoseStack();
        Vec3 cameraPos = camera.position();
        Vec3 transPos = pos.getBottomCenter().subtract(cameraPos);

        TextureAtlasSprite sprite = getWetOverlaySprite();
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        int light = LightCoordsUtil.pack(0, 0);

        matrices.pushPose();
        matrices.translate(transPos.x - 0.5, transPos.y + 1.01, transPos.z - 0.5);
        Matrix4f matrix4f = matrices.last().pose();

        buffer.addVertex(matrix4f, 0, 0, 0)
                .setColor(0.1f, 0.1f, 0.25f, 0.25f).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

        buffer.addVertex(matrix4f, 0, 0, 1)
                .setColor(0.1f, 0.1f, 0.25f, 0.25f).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

        buffer.addVertex(matrix4f, 1, 0, 1)
                .setColor(0.1f, 0.1f, 0.25f, 0.25f).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

        buffer.addVertex(matrix4f, 1, 0, 0)
                .setColor(0.1f, 0.1f, 0.25f, 0.25f).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

        matrices.popPose();
    }

    public TextureAtlasSprite getMovingSprite() {
        return spriteHandler.getSprite(WavifySprites.MOVING_TEXTURE_ID);
    }

    public TextureAtlasSprite getMovingWhiteSprite() {
        return spriteHandler.getSprite(WavifySprites.MOVING_WHITE_TEXTURE_ID);
    }

    public TextureAtlasSprite getTopWashingSprite() {
        return spriteHandler.getSprite(WavifySprites.TOP_WASHING_ID);
    }

    public TextureAtlasSprite getTopWashingWhiteSprite() {
        return spriteHandler.getSprite(WavifySprites.TOP_WASHING_WHITE_ID);
    }

    public TextureAtlasSprite getBottomWashingSprite() {
        return spriteHandler.getSprite(WavifySprites.BOTTOM_WASHING_ID);
    }

    public TextureAtlasSprite getBottomWashingWhiteSprite() {
        return spriteHandler.getSprite(WavifySprites.BOTTOM_WASHING_WHITE_ID);
    }

    public TextureAtlasSprite getWashedSprite() {
        return spriteHandler.getSprite(WavifySprites.WASHING_TEXTURE_ID);
    }

    public TextureAtlasSprite getWashedWhiteSprite() {
        return spriteHandler.getSprite(WavifySprites.WASHING_WHITE_TEXTURE_ID);
    }

    public TextureAtlasSprite getWetOverlaySprite() {
        return spriteHandler.getSprite(WavifySprites.WET_OVERLAY_TEXTURE_ID);
    }

}
