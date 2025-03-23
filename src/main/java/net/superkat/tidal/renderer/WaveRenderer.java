package net.superkat.tidal.renderer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.superkat.tidal.TidalClient;
import net.superkat.tidal.sprite.TidalSpriteHandler;
import net.superkat.tidal.sprite.TidalSprites;
import net.superkat.tidal.wave.TidalWaveHandler;
import net.superkat.tidal.wave.Wave;
import org.joml.Matrix4f;

import java.util.Set;

public class WaveRenderer {
    public TidalWaveHandler handler;
    public TidalSpriteHandler spriteHandler;
    public ClientWorld world;

    public WaveRenderer(TidalWaveHandler handler, ClientWorld world) {
        this.handler = handler;
        this.spriteHandler = TidalClient.TIDAL_SPRITE_HANDLER;
        this.world = world;
    }

    public void render(BufferBuilder buffer, WorldRenderContext context) {
        ObjectArrayList<Wave> waves = this.handler.getWaves();
        if(waves == null || waves.isEmpty()) return;

        float tickDelta = context.tickCounter().getTickDelta(false);
        Camera camera = context.camera();

        for (Wave wave : waves) {
            renderWave(buffer, camera, wave, tickDelta);
        }

        renderOverlays(buffer, camera, handler.coveredBlocks);

        if(MinecraftClient.getInstance().getEntityRenderDispatcher().shouldRenderHitboxes()) {
            for (Wave wave : waves) {
                MatrixStack matrixStack = new MatrixStack();
                VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
                Vec3d cameraPos = camera.getPos();
                matrixStack.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
                WorldRenderer.drawBox(matrixStack, lines, wave.getBoundingBox(), 1f, 1f, 1f, 1f);
            }
        }
    }

    public void renderWave(BufferBuilder buffer, Camera camera, Wave wave, float delta) {
        if(wave == null) return;

        MatrixStack matrices = new MatrixStack();
        matrices.push();

        Box box = wave.getBoundingBox();
        Vec3d center = box.getBottomCenter();
        Vec3d cameraPos = camera.getPos();
        Vec3d transPos = center.subtract(cameraPos);

        matrices.push();
        matrices.translate(transPos.x, transPos.y, transPos.z); //offsets to the wave's position
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-wave.yaw + 90)); //rotate wave left/right
        matrices.translate(-wave.width / 3, 0, 0);
        matrices.scale(3f, 1, 3f);

        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        Sprite colorableSprite = getMovingSprite();
        Sprite whiteSprite = getMovingWhiteSprite();

        boolean washingUp = wave.isWashingUp();

        int light = wave.getLight();

        float red = wave.red;
        float green = wave.green;
        float blue = wave.blue;
        float alpha = wave.alpha;

        //normal wave texture
        for (int i = 0; i < wave.width; i++) {
            waveQuad(posMatrix, buffer, colorableSprite, wave.age, i, 0, 0, 1, wave.length, red, green, blue, alpha, light);
            waveQuad(posMatrix, buffer, whiteSprite, wave.age, i, 0.05f, 0, 1, wave.length, 1f, 1f, 1f, alpha, light);
        }

        //beneath wave texture after hitting shore
        if(washingUp && wave.bigWave) {
            Sprite washingColorableSprite = getWashedSprite();
            Sprite washingWhiteSprite = getWashedWhiteSprite();
            float washingZ = (float) Math.sin((double) wave.getWashingAge() / 40) + 1.15f;
            matrices.scale(1.25f, 1, 1);
            for (int i = 0; i < wave.width; i++) {
                waveQuad(posMatrix, buffer, washingColorableSprite, wave.getWashingAge(), i - 0.15f, -0.05f, washingZ, 1, 2, red, green, blue, alpha, light);
                waveQuad(posMatrix, buffer, washingWhiteSprite, wave.getWashingAge(), i - 0.15f, -0.01f, washingZ, 1, 2, 1f, 1f, 1f, alpha, light);
            }
        }

        matrices.pop();
    }

    private void waveQuad(Matrix4f matrix4f, BufferBuilder buffer, Sprite sprite, int waveAge, float x, float y, float z, float width, float length, float red, float green, float blue, float alpha, int light) {
        float halfWidth = width / 2f;
        float halfLength = length / 2f;

        int frame = TidalSprites.getFrameFromAge(sprite, waveAge);
        float u0 = TidalSprites.getMinU(sprite);
        float u1 = TidalSprites.getMaxU(sprite);
        float v0 = TidalSprites.getMinV(sprite, frame);
        float v1 = TidalSprites.getMaxV(sprite, frame);

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
        matrices.translate(transPos.x - 0.5, transPos.y + 1.01, transPos.z - 0.5); //offsets to the wave's position
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

    public Sprite getMovingSprite() {
        return spriteHandler.getSprite(TidalSprites.MOVING_TEXTURE_ID);
    }

    public Sprite getMovingWhiteSprite() {
        return spriteHandler.getSprite(TidalSprites.MOVING_WHITE_TEXTURE_ID);
    }

    public Sprite getWashedSprite() {
        return spriteHandler.getSprite(TidalSprites.WASHING_TEXTURE_ID);
    }

    public Sprite getWashedWhiteSprite() {
        return spriteHandler.getSprite(TidalSprites.WASHING_WHITE_TEXTURE_ID);
    }

    public Sprite getWetOverlaySprite() {
        return spriteHandler.getSprite(TidalSprites.WET_OVERLAY_TEXTURE_ID);
    }

}
