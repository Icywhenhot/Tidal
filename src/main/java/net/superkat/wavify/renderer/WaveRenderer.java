package net.superkat.wavify.renderer;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
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
import net.superkat.wavify.ClientState;
import net.superkat.wavify.compat.IrisCompat;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.sprite.WavifySpriteHandler;
import net.superkat.wavify.sprite.WaveSprite;
import net.superkat.wavify.sprite.WavifySprites;
import net.superkat.wavify.wave.WavifyWaveHandler;
import net.superkat.wavify.wave.Wave;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Set;

public class WaveRenderer {
    private static final float WAVE_FOAM_Y_OFFSET = 0.08f;

    private record Sheets(WaveSprite moving, WaveSprite movingWhite,
                          WaveSprite topWashing, WaveSprite topWashingWhite,
                          WaveSprite bottomWashing, WaveSprite bottomWashingWhite) {
    }

    private record Paint(float red, float green, float blue, float alpha, float foamAlpha, int light) {
    }

    public WavifyWaveHandler handler;
    public WavifySpriteHandler spriteHandler;
    public ClientLevel level;

    public WaveRenderer(WavifyWaveHandler handler, ClientLevel level) {
        this.handler = handler;
        this.spriteHandler = ClientState.SPRITES;
        this.level = level;
    }

    public void render(VertexConsumer buffer, LevelRenderContext context) {
        List<Wave> waves = this.handler.getWaves();
        if (waves == null || waves.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Camera camera = mc.gameRenderer.mainCamera();

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

        PoseStack matrices = new PoseStack();
        for (Wave wave : waves) {
            if (wave == null) continue;
            matrices.pushPose();
            renderWave(matrices, buffer, camera, wave, tickDelta, sheets, bodyBase, foamBase);
            matrices.popPose();
        }

        if (WavifyConfig.enableWetOverlay) renderOverlays(buffer, camera, handler.coveredBlocks);
    }

    private void renderWave(PoseStack matrices, VertexConsumer buffer, Camera camera, Wave wave, float delta, Sheets sheets, float bodyBase, float foamBase) {
        Vec3 cameraPos = camera.position();

        matrices.translate(wave.getX(delta) - cameraPos.x, wave.getY(delta) - cameraPos.y, wave.getZ(delta) - cameraPos.z);
        matrices.mulPose(Axis.YP.rotationDegrees(-wave.getYaw(delta) + 90));
        matrices.mulPose(Axis.XP.rotationDegrees(wave.pitch));
        matrices.scale(wave.scale, 1, wave.scale);

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

        renderWaveColumns(matrices.last().pose(), buffer, wave, colorable, white, age, maxAge, paint, bodyYOffset, foamYOffset);

        if (washingUp && wave.bigWave) {
            renderWashingUnderside(matrices, buffer, wave, sheets, age, maxAge, paint, bodyYOffset, foamYOffset);
        }
    }

    private void renderWashingUnderside(PoseStack matrices, VertexConsumer buffer, Wave wave, Sheets sheets,
                                        int age, int maxAge, Paint paint, float bodyYOffset, float foamYOffset) {
        WaveSprite.Uv bottomBody = sheets.bottomWashing().uvAt(age, maxAge);
        WaveSprite.Uv bottomFoam = sheets.bottomWashingWhite().uvAt(age, maxAge);

        float ageDelta = (float) age / maxAge;
        float turnBackDelta = 0.5f;
        float washingLength = ageDelta > turnBackDelta ? Mth.lerp((ageDelta - turnBackDelta) * 2, 2f, 3f) : 2f;
        float washingZ = ageDelta > turnBackDelta ? Mth.lerp((ageDelta - turnBackDelta) * 2, 1.35f, 0) : 1.35f;

        matrices.pushPose();
        matrices.scale(1.25f, 1, 1);
        Matrix4f posMatrix = matrices.last().pose();

        for (int i = 0; i < wave.width; i++) {
            waveQuad(posMatrix, buffer, bottomBody, i - 0.15f, -0.05f + bodyYOffset, washingZ, 1, washingLength, paint.red(), paint.green(), paint.blue(), paint.alpha(), paint.light());
            waveQuad(posMatrix, buffer, bottomFoam, i - 0.15f, -0.01f + foamYOffset, washingZ, 1, washingLength, 1f, 1f, 1f, paint.foamAlpha(), paint.light());
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
            waveQuad(posMatrix, buffer, bodyUv, x, y + bodyYOffset, z, width, length, paint.red(), paint.green(), paint.blue(), paint.alpha(), paint.light());
            waveQuad(posMatrix, buffer, foamUv, x, y + WAVE_FOAM_Y_OFFSET + foamYOffset, z, width, length, 1f, 1f, 1f, paint.foamAlpha(), paint.light());
        }
    }

    private void waveQuad(Matrix4f matrix4f, VertexConsumer buffer, WaveSprite.Uv uv, float x, float y, float z, float width, float length, float red, float green, float blue, float alpha, int light) {
        float halfWidth = width / 2f;
        float halfLength = length / 2f;

        float u0 = uv.u0();
        float u1 = uv.u1();
        float v0 = uv.v0();
        float v1 = uv.v1();

        buffer.addVertex(matrix4f, x - halfWidth, y, z - halfLength)
                .setColor(red, green, blue, alpha).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

        buffer.addVertex(matrix4f, x - halfWidth, y, z + halfLength)
                .setColor(red, green, blue, alpha).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

        buffer.addVertex(matrix4f, x + halfWidth, y, z + halfLength)
                .setColor(red, green, blue, alpha).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

        buffer.addVertex(matrix4f, x + halfWidth, y, z - halfLength)
                .setColor(red, green, blue, alpha).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);
    }

    public void renderOverlays(VertexConsumer buffer, Camera camera, Set<BlockPos> coveredBlocks) {
        if (coveredBlocks.isEmpty()) return;

        TextureAtlasSprite sprite = getWetOverlaySprite();
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        int light = LightCoordsUtil.pack(0, 0);

        Vec3 cameraPos = camera.position();
        Matrix4f matrix4f = new Matrix4f();

        for (BlockPos covered : coveredBlocks) {
            float x = (float) (covered.getX() - cameraPos.x);
            float y = (float) (covered.getY() + 1.01 - cameraPos.y);
            float z = (float) (covered.getZ() - cameraPos.z);

            buffer.addVertex(matrix4f, x, y, z)
                    .setColor(0.1f, 0.1f, 0.25f, 0.25f).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

            buffer.addVertex(matrix4f, x, y, z + 1f)
                    .setColor(0.1f, 0.1f, 0.25f, 0.25f).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

            buffer.addVertex(matrix4f, x + 1f, y, z + 1f)
                    .setColor(0.1f, 0.1f, 0.25f, 0.25f).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);

            buffer.addVertex(matrix4f, x + 1f, y, z)
                    .setColor(0.1f, 0.1f, 0.25f, 0.25f).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);
        }
    }

    public TextureAtlasSprite getWetOverlaySprite() {
        return spriteHandler.getSprite(WavifySprites.WET_OVERLAY_TEXTURE_ID);
    }

}
