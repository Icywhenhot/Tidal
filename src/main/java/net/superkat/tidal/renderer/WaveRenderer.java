package net.superkat.tidal.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
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
        this.level = world;
    }

    public void render(BufferBuilder buffer, LevelRenderContext context) {
        List<Wave> waves = this.handler.getWaves();
        if (waves == null || waves.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        float tickDelta = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Camera camera = mc.gameRenderer.getMainCamera();

        for (Wave wave : waves) {
            renderWave(buffer, camera, wave, tickDelta);
        }

        renderOverlays(buffer, camera, handler.coveredBlocks);
    }

    public void renderWave(BufferBuilder buffer, Camera camera, Wave wave, float delta) {
        if (wave == null) return;

        PoseStack matrices = new PoseStack();
        matrices.push();

        AABB box = wave.getBoundingBox();
        Vec3 center = new Vec3((box.minX + box.maxX) / 2.0, box.minY, (box.minZ + box.maxZ) / 2.0);
        Vec3 cameraPos = camera.position();
        Vec3 transPos = center.subtract(cameraPos);

        matrices.push();
        matrices.translate(transPos.x, transPos.y, transPos.z); // offsets to the wave's position
        matrices.multiply(Axis.POSITIVE_Y.rotationDegrees(-wave.yaw + 90)); // rotate wave left/right
        matrices.multiply(Axis.POSITIVE_X.rotationDegrees(wave.pitch));
        float scale = wave.quadSize;
        matrices.quadSize(scale, 1, scale);
        // this is totally messed up but its pretty unnoticeable and my math isn't woroking right now
        matrices.translate(-wave.width / 3, 0, 0); // translate back to center

        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        boolean washingUp = wave.isWashingUp();
        TextureAtlasSprite colorableSprite = washingUp ? getTopWashingSprite() : getMovingSprite();
        TextureAtlasSprite whiteSprite = washingUp ? getTopWashingWhiteSprite() : getMovingWhiteSprite();

        int light = wave.getLight();

        float red = wave.rCol;
        float green = wave.gCol;
        float blue = wave.bCol;
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

            // this is beyond cursed but i'm really frustrated right now so its fine
            float ageDelta = (float) age / maxAge;
            float turnBackDelta = 0.5f;
            float washingLength = ageDelta > turnBackDelta ? Mth.lerp((ageDelta - turnBackDelta) * 2, 2f, 3f) : 2f;
            float washingZ = ageDelta > turnBackDelta ? Mth.lerp((ageDelta - turnBackDelta) * 2, 1.35f, 0) : 1.35f;
            matrices.quadSize(1.25f, 1, 1);
            for (int i = 0; i < wave.width; i++) {
                waveQuad(posMatrix, buffer, washingColorableSprite, age, maxAge, i - 0.15f, -0.05f, washingZ, 1, washingLength, red, green, blue, alpha, light);
                waveQuad(posMatrix, buffer, washingWhiteSprite, age, maxAge, i - 0.15f, -0.01f, washingZ, 1, washingLength, 1f, 1f, 1f, alpha, light);
            }
        }

        matrices.pop();
    }

    private void waveQuad(Matrix4f matrix4f, BufferBuilder buffer, TextureAtlasSprite sprite, int waveAge, int waveMaxAge, float x, float y, float z, float width, float length, float red, float green, float blue, float alpha, int light) {
        float halfWidth = width / 2f;
        float halfLength = length / 2f;

        int frame = TidalSprites.getFrameFromAge(sprite, waveAge, waveMaxAge);
        float u0 = TidalSprites.getU0(sprite);
        float u1 = TidalSprites.getU1(sprite);
        float v0 = TidalSprites.getV0(sprite, frame);
        float v1 = TidalSprites.getV1(sprite, frame);

//        float u0 = 0f;
//        float u1 = 1f;
//        float v0 = 0f;
//        float v1 = 1f;

        buffer.vertex(matrix4f, x - halfWidth, y, z - halfLength)
                .color(red, green, blue, alpha).texture(u0, v1).light(light);

        buffer.vertex(matrix4f, x - halfWidth, y, z + halfLength)
                .color(red, green, blue, alpha).texture(u0, v0).light(light);

        buffer.vertex(matrix4f, x + halfWidth, y, z + halfLength)
                .color(red, green, blue, alpha).texture(u1, v0).light(light);

        buffer.vertex(matrix4f, x + halfWidth, y, z - halfLength)
                .color(red, green, blue, alpha).texture(u1, v1).light(light);
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

        matrices.push();
        matrices.translate(transPos.x - 0.5, transPos.y + 1.01, transPos.z - 0.5); // offsets to the wave's position
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        buffer.vertex(matrix4f, 0, 0, 0)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u0, v0).light(light);

        buffer.vertex(matrix4f, 0, 0, 1)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u0, v1).light(light);

        buffer.vertex(matrix4f, 1, 0, 1)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u1, v1).light(light);

        buffer.vertex(matrix4f, 1, 0, 0)
                .color(0.1f, 0.1f, 0.25f, 0.25f).texture(u1, v0).light(light);

        matrices.pop();
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
