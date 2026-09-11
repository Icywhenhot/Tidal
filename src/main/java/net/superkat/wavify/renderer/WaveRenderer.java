package net.superkat.wavify.renderer;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.superkat.wavify.ClientState;
import net.superkat.wavify.compat.IrisCompat;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.sprite.WaveSprite;
import net.superkat.wavify.sprite.WavifySpriteHandler;
import net.superkat.wavify.sprite.WavifySprites;
import net.superkat.wavify.wave.WavifyWaveHandler;
import net.superkat.wavify.wave.Wave;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Set;

public class WaveRenderer {
    private static final float WAVE_FOAM_Y_OFFSET = 0.08f;

    public WavifyWaveHandler handler;
    public WavifySpriteHandler spriteHandler;

    private record Sheets(WaveSprite moving, WaveSprite movingWhite,
                          WaveSprite topWashing, WaveSprite topWashingWhite,
                          WaveSprite bottomWashing, WaveSprite bottomWashingWhite) {
    }

    private record Paint(float red, float green, float blue, float alpha, float foamAlpha, int light) {
    }

    public WaveRenderer(WavifyWaveHandler handler, ClientLevel level) {
        this.handler = handler;
        this.spriteHandler = ClientState.SPRITES;
    }

    public void render(PoseStack poseStack, BufferBuilder buffer) {
        List<Wave> waves = this.handler.getWaves();
        if (waves == null || waves.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        float tickDelta = mc.getFrameTime();
        Camera camera = mc.gameRenderer.getMainCamera();

        float shaderSink = IrisCompat.isShaderPackActive() ? (float) WavifyConfig.shaderWaveYSink : 0f;
        float baseOffset = (float) WavifyConfig.waveYOffset;
        float foamBase = baseOffset;
        float bodyBase = baseOffset + shaderSink;

        Sheets sheets = new Sheets(
                this.spriteHandler.getWaveSprite(WavifySprites.MOVING_TEXTURE_ID),
                this.spriteHandler.getWaveSprite(WavifySprites.MOVING_WHITE_TEXTURE_ID),
                this.spriteHandler.getWaveSprite(WavifySprites.TOP_WASHING_ID),
                this.spriteHandler.getWaveSprite(WavifySprites.TOP_WASHING_WHITE_ID),
                this.spriteHandler.getWaveSprite(WavifySprites.BOTTOM_WASHING_ID),
                this.spriteHandler.getWaveSprite(WavifySprites.BOTTOM_WASHING_WHITE_ID)
        );

        for (Wave wave : waves) {
            renderWave(poseStack, buffer, camera, wave, tickDelta, sheets, bodyBase, foamBase);
        }

        if (WavifyConfig.enableWetOverlay) renderOverlays(poseStack, buffer, camera, handler.coveredBlocks);
    }

    private void renderWave(PoseStack matrices, VertexConsumer buffer, Camera camera, Wave wave, float delta,
                            Sheets sheets, float bodyBase, float foamBase) {
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
        WaveSprite colorable = washingUp ? sheets.topWashing() : sheets.moving();
        WaveSprite white = washingUp ? sheets.topWashingWhite() : sheets.movingWhite();

        float transparency = (float) WavifyConfig.transparency;
        float alpha = wave.alpha * transparency;
        Paint paint = new Paint(wave.red, wave.green, wave.blue, alpha,
                WavifyConfig.applyTransparencyToFoam ? alpha : wave.alpha, wave.getLight());

        int age = wave.getAge();
        int maxAge = wave.getMaxAge();

        float sink = wave.getRenderYSink();
        float bodyYOffset = bodyBase + sink;
        float foamYOffset = foamBase + sink;

        renderWaveColumns(posMatrix, buffer, wave, colorable, white, age, maxAge, paint, bodyYOffset, foamYOffset);

        if (washingUp && wave.bigWave) {
            WaveSprite.Uv bottomBody = sheets.bottomWashing().uvAt(age, maxAge);
            WaveSprite.Uv bottomFoam = sheets.bottomWashingWhite().uvAt(age, maxAge);

            float ageDelta = (float) age / maxAge;
            float turnBackDelta = 0.5f;
            float washingLength = ageDelta > turnBackDelta ? Mth.lerp((ageDelta - turnBackDelta) * 2, 2f, 3f) : 2f;
            float washingZ = ageDelta > turnBackDelta ? Mth.lerp((ageDelta - turnBackDelta) * 2, 1.35f, 0) : 1.35f;
            matrices.scale(1.25f, 1, 1);
            for (int i = 0; i < wave.width; i++) {
                waveQuad(posMatrix, buffer, bottomBody, i - 0.15f, -0.05f + bodyYOffset, washingZ, 1, washingLength,
                        paint.red(), paint.green(), paint.blue(), paint.alpha(), paint.light());
                waveQuad(posMatrix, buffer, bottomFoam, i - 0.15f, -0.01f + foamYOffset, washingZ, 1, washingLength,
                        1f, 1f, 1f, paint.foamAlpha(), paint.light());
            }
        }

        matrices.popPose();
    }

    private void renderWaveColumns(Matrix4f posMatrix, VertexConsumer buffer, Wave wave,
                                   WaveSprite colorable, WaveSprite white, int age, int maxAge,
                                   Paint paint, float bodyYOffset, float foamYOffset) {
        int columnCount = wave.getRenderColumnCount();
        WaveSprite.Uv bodyUv = colorable.uvAt(age, maxAge);
        WaveSprite.Uv foamUv = white.uvAt(age, maxAge);

        for (int i = 0; i < columnCount; i++) {
            float x = wave.getRenderColumnLateralOffset(i, columnCount);
            float y = wave.getRenderColumnVerticalOffset(i, columnCount);
            float z = wave.getRenderColumnForwardOffset(i, columnCount);
            float width = wave.getRenderColumnWidth(i, columnCount);
            float length = wave.getRenderColumnLength(i, columnCount);
            waveQuad(posMatrix, buffer, bodyUv, x, y + bodyYOffset, z, width, length,
                    paint.red(), paint.green(), paint.blue(), paint.alpha(), paint.light());
            waveQuad(posMatrix, buffer, foamUv, x, y + WAVE_FOAM_Y_OFFSET + foamYOffset, z, width, length,
                    1f, 1f, 1f, paint.foamAlpha(), paint.light());
        }
    }

    private void waveQuad(Matrix4f matrix4f, VertexConsumer buffer, WaveSprite.Uv uv,
                          float x, float y, float z, float width, float length,
                          float red, float green, float blue, float alpha, int light) {
        float halfWidth = width / 2f;
        float halfLength = length / 2f;

        buffer.vertex(matrix4f, x - halfWidth, y, z - halfLength)
                .color(red, green, blue, alpha).uv(uv.u0(), uv.v1()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();

        buffer.vertex(matrix4f, x - halfWidth, y, z + halfLength)
                .color(red, green, blue, alpha).uv(uv.u0(), uv.v0()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();

        buffer.vertex(matrix4f, x + halfWidth, y, z + halfLength)
                .color(red, green, blue, alpha).uv(uv.u1(), uv.v0()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();

        buffer.vertex(matrix4f, x + halfWidth, y, z - halfLength)
                .color(red, green, blue, alpha).uv(uv.u1(), uv.v1()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();
    }

    public void renderOverlays(PoseStack poseStack, VertexConsumer buffer, Camera camera, Set<BlockPos> coveredBlocks) {
        if (coveredBlocks.isEmpty()) return;
        TextureAtlasSprite sprite = this.spriteHandler.getSprite(WavifySprites.WET_OVERLAY_TEXTURE_ID);
        for (BlockPos covered : coveredBlocks) {
            renderCoverOverlay(poseStack, buffer, camera, covered, sprite);
        }
    }

    private void renderCoverOverlay(PoseStack matrices, VertexConsumer buffer, Camera camera, BlockPos pos, TextureAtlasSprite sprite) {
        Vec3 cameraPos = camera.getPosition();
        Vec3 transPos = Vec3.atBottomCenterOf(pos).subtract(cameraPos);

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
}
