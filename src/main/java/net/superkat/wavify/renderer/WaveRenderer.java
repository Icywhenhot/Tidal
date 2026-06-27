package net.superkat.wavify.renderer;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LightTexture;
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

public class WaveRenderer {
    private static final float WAVE_FOAM_Y_OFFSET = 0.08f;
    private static final float OCEAN_RENDER_Y_SINK = -0.5f;

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

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, RenderType layer) {
        List<Wave> waves = this.handler.getWaves();
        if (waves == null || waves.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        float tickDelta = mc.getFrameTime();
        Camera camera = mc.gameRenderer.getMainCamera();
        VertexConsumer buffer = bufferSource.getBuffer(layer);

        float shaderSink = IrisCompat.isShaderPackActive() ? (float) WavifyConfig.shaderWaveYSink : 0f;
        float baseOffset = (float) WavifyConfig.waveYOffset;
        this.frameFoamYOffset = baseOffset;
        this.frameBodyYOffset = baseOffset + shaderSink;

        for (Wave wave : waves) {
            renderWave(poseStack, buffer, camera, wave, tickDelta);
        }

        if (WavifyConfig.enableWetOverlay) renderOverlays(poseStack, buffer, camera, handler.coveredBlocks);
    }

    public void renderWave(PoseStack matrices, VertexConsumer buffer, Camera camera, Wave wave, float delta) {
        if (wave == null) return;

        Vec3 center = new Vec3(wave.getX(delta), wave.getY(delta), wave.getZ(delta));
        Vec3 cameraPos = camera.getPosition();
        Vec3 transPos = center.subtract(cameraPos);

        matrices.pushPose();
        matrices.translate(transPos.x, transPos.y, transPos.z);
        matrices.mulPose(Axis.YP.rotationDegrees(-wave.getYaw(delta) + 90));
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

        if (washingUp && wave.bigWave) {
            TextureAtlasSprite washingColorableSprite = getBottomWashingSprite();
            TextureAtlasSprite washingWhiteSprite = getBottomWashingWhiteSprite();

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

    private void renderWaveColumns(Matrix4f posMatrix, VertexConsumer buffer, Wave wave, TextureAtlasSprite colorableSprite, TextureAtlasSprite whiteSprite, int age, int maxAge, float red, float green, float blue, float alpha, float foamAlpha, int light, float bodyYOffset, float foamYOffset) {
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

    private void waveQuad(Matrix4f matrix4f, VertexConsumer buffer, TextureAtlasSprite sprite, int waveAge, int waveMaxAge, float x, float y, float z, float width, float length, float red, float green, float blue, float alpha, int light) {
        float halfWidth = width / 2f;
        float halfLength = length / 2f;

        int frame = WavifySprites.getFrameFromAge(sprite, waveAge, waveMaxAge);
        float u0 = WavifySprites.getMinU(sprite);
        float u1 = WavifySprites.getMaxU(sprite);
        float v0 = WavifySprites.getMinV(sprite, frame);
        float v1 = WavifySprites.getMaxV(sprite, frame);

        buffer.vertex(matrix4f, x - halfWidth, y, z - halfLength)
                .color(red, green, blue, alpha).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();

        buffer.vertex(matrix4f, x - halfWidth, y, z + halfLength)
                .color(red, green, blue, alpha).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();

        buffer.vertex(matrix4f, x + halfWidth, y, z + halfLength)
                .color(red, green, blue, alpha).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();

        buffer.vertex(matrix4f, x + halfWidth, y, z - halfLength)
                .color(red, green, blue, alpha).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();
    }

    public void renderOverlays(PoseStack poseStack, VertexConsumer buffer, Camera camera, Set<BlockPos> coveredBlocks) {
        for (BlockPos covered : coveredBlocks) {
            renderCoverOverlay(poseStack, buffer, camera, covered);
        }
    }

    public void renderCoverOverlay(PoseStack matrices, VertexConsumer buffer, Camera camera, BlockPos pos) {
        Vec3 cameraPos = camera.getPosition();
        Vec3 transPos = Vec3.atBottomCenterOf(pos).subtract(cameraPos);

        TextureAtlasSprite sprite = getWetOverlaySprite();
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        int light = LightTexture.pack(0, 0);

        matrices.pushPose();
        matrices.translate(transPos.x - 0.5, transPos.y + 1.01, transPos.z - 0.5);
        Matrix4f matrix4f = matrices.last().pose();

        buffer.vertex(matrix4f, 0f, 0f, 0f)
                .color(0.1f, 0.1f, 0.25f, 0.25f).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();

        buffer.vertex(matrix4f, 0f, 0f, 1f)
                .color(0.1f, 0.1f, 0.25f, 0.25f).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();

        buffer.vertex(matrix4f, 1f, 0f, 1f)
                .color(0.1f, 0.1f, 0.25f, 0.25f).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();

        buffer.vertex(matrix4f, 1f, 0f, 0f)
                .color(0.1f, 0.1f, 0.25f, 0.25f).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();

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
