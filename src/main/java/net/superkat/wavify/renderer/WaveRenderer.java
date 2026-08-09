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

// the waves aren't entities!!!!!!!!!!!!!!!!!!!!!!!!!!!
public class WaveRenderer {
    private static final float WAVE_FOAM_Y_OFFSET = 0.08f;
    // visual only sink so ocean waves sit flush with the water
    // collision y is untouched, the wave stays high so it still washes up and bounces
    // instead of crashing sideways into the shore block, river waves don't get this
    private static final float OCEAN_RENDER_Y_SINK = -0.5f;

    // with a shaderpack on we drop the colored body so vanilla water still covers it
    // the pack then paints its reflections over the wave color and it matches the rest of the surface
    // foam stays above the body so the white crest still pokes out
    // amount comes from the config, cached per frame since the iris check reflects and isn't free
    private float frameBodyYOffset = 0f;
    private float frameFoamYOffset = 0f;

    public WavifyWaveHandler handler;
    public WavifySpriteHandler spriteHandler;
    public ClientWorld world;

    public WaveRenderer(WavifyWaveHandler handler, ClientWorld world) {
        this.handler = handler;
        this.spriteHandler = WavifyClient.WAVIFY_SPRITE_HANDLER;
        this.world = world;
    }

    public void render(BufferBuilder buffer, WorldRenderContext context) {
        List<Wave> waves = this.handler.getWaves();
        if (waves == null || waves.isEmpty()) return;

        float tickDelta = context.tickCounter().getTickDelta(false);
        Camera camera = context.camera();

        float shaderSink = IrisCompat.isShaderPackActive() ? (float) WavifyConfig.shaderWaveYSink : 0f;
        float baseOffset = (float) WavifyConfig.waveYOffset;
        this.frameFoamYOffset = baseOffset;
        this.frameBodyYOffset = baseOffset + shaderSink;

        for (Wave wave : waves) {
            renderWave(buffer, camera, wave, tickDelta);
        }

        if (WavifyConfig.enableWetOverlay) renderOverlays(buffer, camera, handler.coveredBlocks);
    }

    public void renderWave(BufferBuilder buffer, Camera camera, Wave wave, float delta) {
        if (wave == null) return;

        MatrixStack matrices = new MatrixStack();
        matrices.push();

        Vec3d center = new Vec3d(wave.getX(delta), wave.getY(delta), wave.getZ(delta));
        Vec3d cameraPos = camera.getPos();
        Vec3d transPos = center.subtract(cameraPos);

        matrices.push();
        matrices.translate(transPos.x, transPos.y, transPos.z); // move over to the wave
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-wave.getYaw(delta) + 90)); // swing it left or right
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(wave.pitch));
        float scale = wave.scale;
        matrices.scale(scale, 1, scale);

        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        boolean washingUp = wave.isWashingUp();
        Sprite colorableSprite = washingUp ? getTopWashingSprite() : getMovingSprite();
        Sprite whiteSprite = washingUp ? getTopWashingWhiteSprite() : getMovingWhiteSprite();

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

        // the bit that spreads out underneath once it hits the shore
        if (washingUp && wave.bigWave) {
            Sprite washingColorableSprite = getBottomWashingSprite();
            Sprite washingWhiteSprite = getBottomWashingWhiteSprite();

            // this is beyond cursed but i'm really frustrated right now so its fine
            float ageDelta = (float) age / maxAge;
            float turnBackDelta = 0.5f;
            float washingLength = ageDelta > turnBackDelta ? MathHelper.lerp((ageDelta - turnBackDelta) * 2, 2f, 3f) : 2f;
            float washingZ = ageDelta > turnBackDelta ? MathHelper.lerp((ageDelta - turnBackDelta) * 2, 1.35f, 0) : 1.35f;
            matrices.scale(1.25f, 1, 1);
            for (int i = 0; i < wave.width; i++) {
                waveQuad(posMatrix, buffer, washingColorableSprite, age, maxAge, i - 0.15f, -0.05f + bodyYOffset, washingZ, 1, washingLength, red, green, blue, alpha, light);
                waveQuad(posMatrix, buffer, washingWhiteSprite, age, maxAge, i - 0.15f, -0.01f + foamYOffset, washingZ, 1, washingLength, 1f, 1f, 1f, foamAlpha, light);
            }
        }

        matrices.pop();
    }

    private void renderWaveColumns(Matrix4f posMatrix, BufferBuilder buffer, Wave wave, Sprite colorableSprite, Sprite whiteSprite, int age, int maxAge, float red, float green, float blue, float alpha, float foamAlpha, int light, float bodyYOffset, float foamYOffset) {
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

    private void waveQuad(Matrix4f matrix4f, BufferBuilder buffer, Sprite sprite, int waveAge, int waveMaxAge, float x, float y, float z, float width, float length, float red, float green, float blue, float alpha, int light) {
        float halfWidth = width / 2f;
        float halfLength = length / 2f;

        int frame = WavifySprites.getFrameFromAge(sprite, waveAge, waveMaxAge);
        float u0 = WavifySprites.getMinU(sprite);
        float u1 = WavifySprites.getMaxU(sprite);
        float v0 = WavifySprites.getMinV(sprite, frame);
        float v1 = WavifySprites.getMaxV(sprite, frame);

//        float u0 = 0f;
//        float u1 = 1f;
//        float v0 = 0f;
//        float v1 = 1f;

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
        for (BlockPos covered : coveredBlocks) {
            renderCoverOverlay(buffer, camera, covered);
        }
    }

    public void renderCoverOverlay(BufferBuilder buffer, Camera camera, BlockPos pos) {
        MatrixStack matrices = new MatrixStack();
        Vec3d cameraPos = camera.getPos();
        Vec3d transPos = pos.toBottomCenterPos().subtract(cameraPos);

        Sprite sprite = getWetOverlaySprite();
        float u0 = sprite.getMinU();
        float u1 = sprite.getMaxU();
        float v0 = sprite.getMinV();
        float v1 = sprite.getMaxV();

        int light = LightmapTextureManager.pack(0, 0);

        matrices.push();
        matrices.translate(transPos.x - 0.5, transPos.y + 1.01, transPos.z - 0.5); // offsets to the wave's position
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        int overlay = OverlayTexture.DEFAULT_UV;

        buffer.vertex(matrix4f, 0, 0, 0)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u0, v0).overlay(overlay).light(light).normal(0f, 1f, 0f);

        buffer.vertex(matrix4f, 0, 0, 1)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u0, v1).overlay(overlay).light(light).normal(0f, 1f, 0f);

        buffer.vertex(matrix4f, 1, 0, 1)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u1, v1).overlay(overlay).light(light).normal(0f, 1f, 0f);

        buffer.vertex(matrix4f, 1, 0, 0)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u1, v0).overlay(overlay).light(light).normal(0f, 1f, 0f);

        matrices.pop();
    }

    public Sprite getMovingSprite() {
        return spriteHandler.getSprite(WavifySprites.MOVING_TEXTURE_ID);
    }

    public Sprite getMovingWhiteSprite() {
        return spriteHandler.getSprite(WavifySprites.MOVING_WHITE_TEXTURE_ID);
    }

    public Sprite getTopWashingSprite() {
        return spriteHandler.getSprite(WavifySprites.TOP_WASHING_ID);
    }

    public Sprite getTopWashingWhiteSprite() {
        return spriteHandler.getSprite(WavifySprites.TOP_WASHING_WHITE_ID);
    }

    public Sprite getBottomWashingSprite() {
        return spriteHandler.getSprite(WavifySprites.BOTTOM_WASHING_ID);
    }

    public Sprite getBottomWashingWhiteSprite() {
        return spriteHandler.getSprite(WavifySprites.BOTTOM_WASHING_WHITE_ID);
    }

    public Sprite getWashedSprite() {
        return spriteHandler.getSprite(WavifySprites.WASHING_TEXTURE_ID);
    }

    public Sprite getWashedWhiteSprite() {
        return spriteHandler.getSprite(WavifySprites.WASHING_WHITE_TEXTURE_ID);
    }

    public Sprite getWetOverlaySprite() {
        return spriteHandler.getSprite(WavifySprites.WET_OVERLAY_TEXTURE_ID);
    }

}
