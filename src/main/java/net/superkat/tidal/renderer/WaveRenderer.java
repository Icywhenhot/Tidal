package net.superkat.tidal.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import net.superkat.tidal.TidalClient;
import net.superkat.tidal.sprite.TidalSpriteHandler;
import net.superkat.tidal.sprite.TidalSprites;
import net.superkat.tidal.wave.TidalWaveHandler;
import net.superkat.tidal.wave.Wave;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Set;

/**
 * THE WAVES AREN'T ENTITIES!!!!!!!!!!!!!!!!!!!!!!!!!!!
 */
public class WaveRenderer {
    public TidalWaveHandler handler;
    public TidalSpriteHandler spriteHandler;
    public ClientLevel level;

    public WaveRenderer(TidalWaveHandler handler, ClientLevel level) {
        this.handler = handler;
        this.spriteHandler = TidalClient.TIDAL_SPRITE_HANDLER;
        this.level = level;
    }

    public void render(BufferBuilder buffer, LevelRenderContext context) {
        List<Wave> waves = this.handler.getWaves();
        if (waves == null || waves.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Camera camera = mc.gameRenderer.getMainCamera();

        for (Wave wave : waves) {
            renderWave(buffer, camera, wave, tickDelta);
        }

        renderOverlays(buffer, camera, handler.coveredBlocks);
    }

    public void renderWave(BufferBuilder buffer, Camera camera, Wave wave, float delta) {
        if (wave == null) return;

        PoseStack matrices = new PoseStack();
        matrices.pushPose();

        AABB box = wave.getBoundingBox();
        Vec3 center = new Vec3((box.minX + box.maxX) / 2.0, box.minY, (box.minZ + box.maxZ) / 2.0);
        Vec3 cameraPos = camera.position();
        Vec3 transPos = center.subtract(cameraPos);

        matrices.pushPose();
        matrices.translate(transPos.x, transPos.y, transPos.z); // offsets to the wave's position
        matrices.mulPose(Axis.YP.rotationDegrees(-wave.yaw + 90)); // rotate wave left/right
        matrices.mulPose(Axis.XP.rotationDegrees(wave.pitch));
        float scale = wave.scale;
        matrices.scale(scale, 1, scale);
        // this is totally messed up but its pretty unnoticeable and my math isn't woroking right now
        matrices.translate(-wave.width / 3, 0, 0); // translate back to center

        Matrix4f posMatrix = matrices.last().pose();

        boolean washingUp = wave.isWashingUp();
        TextureAtlasSprite colorableSprite = washingUp ? getTopWashingSprite() : getMovingSprite();
        TextureAtlasSprite whiteSprite = washingUp ? getTopWashingWhiteSprite() : getMovingWhiteSprite();

        int light = wave.getLight();

        float red = wave.red;
        float green = wave.green;
        float blue = wave.blue;
        float alpha = wave.alpha;

        int age = wave.getAge();
        int maxAge = wave.getMaxAge();

        // normal wave texture
        for (int i = 0; i < wave.width; i++) {
            waveQuad(posMatrix, buffer, colorableSprite, age, maxAge, i, 0, 0, 1, wave.length, red, green, blue, alpha, light);
            waveQuad(posMatrix, buffer, whiteSprite, age, maxAge, i, 0.05f, 0, 1, wave.length, 1f, 1f, 1f, alpha, light);
        }

        // beneath wave texture after hitting shore
        if (washingUp && wave.bigWave) {
            TextureAtlasSprite washingColorableSprite = getBottomWashingSprite();
            TextureAtlasSprite washingWhiteSprite = getBottomWashingWhiteSprite();

            float ageDelta = (float) age / maxAge;
            float turnBackDelta = 0.5f;
            float washingLength = ageDelta > turnBackDelta ? Mth.lerp((ageDelta - turnBackDelta) * 2, 2f, 3f) : 2f;
            float washingZ = ageDelta > turnBackDelta ? Mth.lerp((ageDelta - turnBackDelta) * 2, 1.35f, 0) : 1.35f;
            matrices.scale(1.25f, 1, 1);
            for (int i = 0; i < wave.width; i++) {
                waveQuad(posMatrix, buffer, washingColorableSprite, age, maxAge, i - 0.15f, -0.05f, washingZ, 1, washingLength, red, green, blue, alpha, light);
                waveQuad(posMatrix, buffer, washingWhiteSprite, age, maxAge, i - 0.15f, -0.01f, washingZ, 1, washingLength, 1f, 1f, 1f, alpha, light);
            }
        }

        matrices.popPose();
    }

    private void waveQuad(Matrix4f matrix4f, BufferBuilder buffer, TextureAtlasSprite sprite, int waveAge, int waveMaxAge, float x, float y, float z, float width, float length, float red, float green, float blue, float alpha, int light) {
        float halfWidth = width / 2f;
        float halfLength = length / 2f;

        int frame = TidalSprites.getFrameFromAge(sprite, waveAge, waveMaxAge);
        float u0 = TidalSprites.getU0(sprite);
        float u1 = TidalSprites.getU1(sprite);
        float v0 = TidalSprites.getV0(sprite, frame);
        float v1 = TidalSprites.getV1(sprite, frame);

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
        return spriteHandler.getSprite(TidalSprites.MOVING_TEXTURE_ID);
    }

    public TextureAtlasSprite getMovingWhiteSprite() {
        return spriteHandler.getSprite(TidalSprites.MOVING_WHITE_TEXTURE_ID);
    }

    public TextureAtlasSprite getTopWashingSprite() {
        return spriteHandler.getSprite(TidalSprites.TOP_WASHING_ID);
    }

    public TextureAtlasSprite getTopWashingWhiteSprite() {
        return spriteHandler.getSprite(TidalSprites.TOP_WASHING_WHITE_ID);
    }

    public TextureAtlasSprite getBottomWashingSprite() {
        return spriteHandler.getSprite(TidalSprites.BOTTOM_WASHING_ID);
    }

    public TextureAtlasSprite getBottomWashingWhiteSprite() {
        return spriteHandler.getSprite(TidalSprites.BOTTOM_WASHING_WHITE_ID);
    }

    public TextureAtlasSprite getWashedSprite() {
        return spriteHandler.getSprite(TidalSprites.WASHING_TEXTURE_ID);
    }

    public TextureAtlasSprite getWashedWhiteSprite() {
        return spriteHandler.getSprite(TidalSprites.WASHING_WHITE_TEXTURE_ID);
    }

    public TextureAtlasSprite getWetOverlaySprite() {
        return spriteHandler.getSprite(TidalSprites.WET_OVERLAY_TEXTURE_ID);
    }

}
