package net.superkat.wavify.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.superkat.wavify.ClientState;
import net.superkat.wavify.compat.IrisCompat;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.sprite.WavifySpriteHandler;
import net.superkat.wavify.sprite.WaveSprite;
import net.superkat.wavify.sprite.WavifySprites;
import net.superkat.wavify.wave.RiverWave;
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
    public ClientWorld world;

    public WaveRenderer(WavifyWaveHandler handler, ClientWorld world) {
        this.handler = handler;
        this.spriteHandler = ClientState.SPRITES;
        this.world = world;
    }

    public void render(BufferBuilder buffer, WorldRenderContext context) {
        List<Wave> waves = this.handler.getWaves();
        if (waves == null || waves.isEmpty()) return;

        float tickDelta = context.tickCounter().getTickDelta(false);
        Camera camera = context.camera();

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

        MatrixStack matrices = new MatrixStack();
        for (Wave wave : waves) {
            if (wave == null) continue;
            matrices.push();
            renderWave(matrices, buffer, camera, wave, tickDelta, sheets, bodyBase, foamBase);
            matrices.pop();
        }

        if (WavifyConfig.enableWetOverlay) renderOverlays(buffer, camera, handler.coveredBlocks);
    }

    private void renderWave(MatrixStack matrices, BufferBuilder buffer, Camera camera, Wave wave, float delta, Sheets sheets, float bodyBase, float foamBase) {
        Vec3d cameraPos = camera.getPos();

        matrices.translate(wave.getX(delta) - cameraPos.x, wave.getY(delta) - cameraPos.y, wave.getZ(delta) - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-wave.getYaw(delta) + 90));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(wave.pitch));
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

        renderWaveColumns(matrices.peek().getPositionMatrix(), buffer, wave, colorable, white, age, maxAge, paint, bodyYOffset, foamYOffset);

        if (washingUp && wave.bigWave) {
            renderWashingUnderside(matrices, buffer, wave, sheets, age, maxAge, paint, bodyYOffset, foamYOffset);
        }
    }

    private void renderWashingUnderside(MatrixStack matrices, BufferBuilder buffer, Wave wave, Sheets sheets,
                                        int age, int maxAge, Paint paint, float bodyYOffset, float foamYOffset) {
        WaveSprite.Uv bottomBody = sheets.bottomWashing().uvAt(age, maxAge);
        WaveSprite.Uv bottomFoam = sheets.bottomWashingWhite().uvAt(age, maxAge);

        float ageDelta = (float) age / maxAge;
        float turnBackDelta = 0.5f;
        float washingLength = ageDelta > turnBackDelta ? MathHelper.lerp((ageDelta - turnBackDelta) * 2, 2f, 3f) : 2f;
        float washingZ = ageDelta > turnBackDelta ? MathHelper.lerp((ageDelta - turnBackDelta) * 2, 1.35f, 0) : 1.35f;

        matrices.push();
        matrices.scale(1.25f, 1, 1);
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        for (int i = 0; i < wave.width; i++) {
            waveQuad(posMatrix, buffer, bottomBody, i - 0.15f, -0.05f + bodyYOffset, washingZ, 1, washingLength, paint.red(), paint.green(), paint.blue(), paint.alpha(), paint.light());
            waveQuad(posMatrix, buffer, bottomFoam, i - 0.15f, -0.01f + foamYOffset, washingZ, 1, washingLength, 1f, 1f, 1f, paint.foamAlpha(), paint.light());
        }

        matrices.pop();
    }

    private void renderWaveColumns(Matrix4f posMatrix, BufferBuilder buffer, Wave wave,
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

    private void waveQuad(Matrix4f matrix4f, BufferBuilder buffer, WaveSprite.Uv uv, float x, float y, float z, float width, float length, float red, float green, float blue, float alpha, int light) {
        float halfWidth = width / 2f;
        float halfLength = length / 2f;

        float u0 = uv.u0();
        float u1 = uv.u1();
        float v0 = uv.v0();
        float v1 = uv.v1();

        int overlay = OverlayTexture.DEFAULT_UV;

        buffer.vertex(matrix4f, x - halfWidth, y, z - halfLength)
                .color(red, green, blue, alpha).texture(u0, v1).overlay(overlay).light(light).normal(0f, 1f, 0f);

        buffer.vertex(matrix4f, x - halfWidth, y, z + halfLength)
                .color(red, green, blue, alpha).texture(u0, v0).overlay(overlay).light(light).normal(0f, 1f, 0f);

        buffer.vertex(matrix4f, x + halfWidth, y, z + halfLength)
                .color(red, green, blue, alpha).texture(u1, v0).overlay(overlay).light(light).normal(0f, 1f, 0f);

        buffer.vertex(matrix4f, x + halfWidth, y, z - halfLength)
                .color(red, green, blue, alpha).texture(u1, v1).overlay(overlay).light(light).normal(0f, 1f, 0f);
    }

    public void renderOverlays(BufferBuilder buffer, Camera camera, Set<BlockPos> coveredBlocks) {
        if (coveredBlocks.isEmpty()) return;

        Sprite sprite = getWetOverlaySprite();
        float u0 = sprite.getMinU();
        float u1 = sprite.getMaxU();
        float v0 = sprite.getMinV();
        float v1 = sprite.getMaxV();
        int light = LightmapTextureManager.pack(0, 0);
        int overlay = OverlayTexture.DEFAULT_UV;

        Vec3d cameraPos = camera.getPos();
        Matrix4f matrix4f = new Matrix4f();

        for (BlockPos covered : coveredBlocks) {
            float x = (float) (covered.getX() - cameraPos.x);
            float y = (float) (covered.getY() + 1.01 - cameraPos.y);
            float z = (float) (covered.getZ() - cameraPos.z);

            buffer.vertex(matrix4f, x, y, z)
                    .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u0, v0).overlay(overlay).light(light).normal(0f, 1f, 0f);

            buffer.vertex(matrix4f, x, y, z + 1f)
                    .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u0, v1).overlay(overlay).light(light).normal(0f, 1f, 0f);

            buffer.vertex(matrix4f, x + 1f, y, z + 1f)
                    .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u1, v1).overlay(overlay).light(light).normal(0f, 1f, 0f);

            buffer.vertex(matrix4f, x + 1f, y, z)
                    .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u1, v0).overlay(overlay).light(light).normal(0f, 1f, 0f);
        }
    }

    public Sprite getWetOverlaySprite() {
        return spriteHandler.getSprite(WavifySprites.WET_OVERLAY_TEXTURE_ID);
    }

}
