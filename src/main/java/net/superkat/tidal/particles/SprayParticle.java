package net.superkat.tidal.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.superkat.tidal.TidalParticles;
import net.superkat.tidal.wave.TidalWaveHandler;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class SprayParticle extends SpriteBillboardParticle {
    private static final double MAX_SQUARED_COLLISION_CHECK_DISTANCE = MathHelper.square(100.0);
    protected final SpriteProvider spriteProvider;

    public float yaw;
    public float intensity;
    private boolean stopped;

    public SprayParticle(ClientWorld world, double x, double y, double z, double velX, double velY, double velZ, SprayParticleEffect params, SpriteProvider spriteProvider) {
        super(world, x, y, z, velX, velY, velZ);
        this.spriteProvider = spriteProvider;

        this.yaw = params.getYaw();
        this.intensity = params.getIntensity();
        float speed = (float) (Math.abs(velX) + Math.abs(velZ)) / 1.25f;
        this.velocityX = (float) (Math.cos(Math.toRadians(yaw)) * speed);
        this.velocityY = 0.15f * intensity;
        this.velocityZ = (float) (Math.sin(Math.toRadians(yaw)) * speed);

        this.maxAge = (int) (50 + (intensity * 5f));

        this.scale = MathHelper.clamp(intensity * 4f, 1f, params.getScale() * 2f);
        this.collidesWithWorld = true;
        this.gravityStrength = 0.5f;

        if(spawnWhite()) {
            this.world.addParticle(new WhiteSprayParticleEffect(yaw, intensity, this.scale), x, y, z, velX, velY, velZ);
            this.updateWaterColor(); //only need to update on spawn because it lasts for so little time
        }

        this.angle = 15 * intensity * 5f;

        this.setSpriteForAge(this.spriteProvider);
    }

    @Override
    public void tick() {
        super.tick();
        if(this.scale <= 0f) {
            this.markDead();
            return;
        }

        if(TidalWaveHandler.posIsWater(this.world, this.getPos().add(0, 1, 0))) {
            this.x -= this.velocityX * 8;
            this.z -= this.velocityZ * 8f;
            for (int i = 0; i < 5; i++) {
                this.world.addParticle(TidalParticles.SPLASH_PARTICLE,
                        this.x + this.random.nextGaussian(), this.y + 1,
                        this.z + this.random.nextGaussian(),
                        this.random.nextGaussian() * 0.05f,
                        Math.abs(this.world.random.nextGaussian()) * 0.1f + MathHelper.clamp(intensity, 0.1, 0.3),
                        this.random.nextGaussian() * 0.05f);

                this.world.addParticle(ParticleTypes.BUBBLE,
                        this.x + this.random.nextGaussian() / 2f, this.y + 1,
                        this.z + this.random.nextGaussian() / 2f,
                        this.random.nextGaussian() / 8f,
                        0,
                        this.random.nextGaussian() / 8f);
            }
            this.markDead();
        }

        this.prevAngle = this.angle;
        if(this.velocityY != 0 && !onGround) {
            this.angle = this.angle + (float) this.velocityY * 35f;
        } else {
            this.angle = 0f;
        }

        this.setSpriteForAge(this.spriteProvider);
    }

    @Override
    public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        Quaternionf quaternionf = new Quaternionf();
        quaternionf.rotateX((float) Math.toRadians(-90f));
        quaternionf.rotateZ((float) Math.toRadians(-90f - this.yaw));
        quaternionf.rotateX((float) Math.toRadians(this.angle));
        render(vertexConsumer, camera, quaternionf, tickDelta);
        quaternionf.rotateY((float) Math.toRadians(180f));
        render(vertexConsumer, camera, quaternionf, tickDelta);
    }

    protected void render(VertexConsumer vertexConsumer, Camera camera, Quaternionf quaternionf, float tickDelta) {
        Vec3d vec3d = camera.getPos();
        float x = (float)(MathHelper.lerp(tickDelta, this.prevPosX, this.x) - vec3d.getX());
        float y = (float)(MathHelper.lerp(tickDelta, this.prevPosY, this.y) - vec3d.getY()) + (spawnWhite() ? 0.025f : 0.125f);
        float z = (float)(MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - vec3d.getZ());
        this.quad(vertexConsumer, quaternionf, x, y, z, tickDelta);
    }

    protected void quad(VertexConsumer vertexConsumer, Quaternionf quaternionf, float x, float y, float z, float tickDelta) {
        float scale = this.getSize(tickDelta);
        float minU = this.getMinU();
        float maxU = this.getMaxU();
        float minV = this.getMinV();
        float maxV = this.getMaxV();
        int light = this.getBrightness(tickDelta);
        this.vertex(vertexConsumer, quaternionf, x, y, z, 1f, -1f, scale, maxU, maxV, light);
        this.vertex(vertexConsumer, quaternionf, x, y, z, 1f, 1f, scale, maxU, minV, light);
        this.vertex(vertexConsumer, quaternionf, x, y, z, -1f, 1f, scale, minU, minV, light);
        this.vertex(vertexConsumer, quaternionf, x, y, z, -1f, -1f, scale, minU, maxV, light);
    }

    private void vertex(
            VertexConsumer vertexConsumer, Quaternionf quaternionf,
            float x, float y, float z,
            float offsetX, float offsetY,
            float scale,
            float u, float v,
            int light
    ) {
        Vector3f vector3f = new Vector3f(offsetX, offsetY, 0.0F).rotate(quaternionf).mul(scale, 1, scale).add(x, y, z);
        vertexConsumer.vertex(vector3f.x(), vector3f.y(), vector3f.z()).texture(u, v).color(this.red, this.green, this.blue, this.alpha).light(light);
    }

    public void move(double dx, double dy, double dz) {
        if (!this.stopped) {
            double d = dx;
            double e = dy;
            double f = dz;
            if (this.collidesWithWorld && (dx != 0.0 || dy != 0.0 || dz != 0.0) && dx * dx + dy * dy + dz * dz < MAX_SQUARED_COLLISION_CHECK_DISTANCE) {
                //expanding bounding box to specifically account for mud and I guess soul sand too?
                Vec3d vec3d = Entity.adjustMovementForCollisions(null, new Vec3d(dx, dy, dz), this.getBoundingBox().expand(0, 0.15, 0), this.world, List.of());
                dx = vec3d.x;
                dy = vec3d.y;
                dz = vec3d.z;
            }

            if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
                this.setBoundingBox(this.getBoundingBox().offset(dx, dy, dz));
                this.repositionFromBoundingBox();
            }

            this.onGround = e != dy && e < 0.0;
        }
    }

    public BlockPos getPos() {
        return BlockPos.ofFloored(this.x, this.y, this.z);
    }

    public void updateWaterColor() {
        int color = BiomeColors.getWaterColor(this.world, this.getPos());
        float r = (float) (color >> 16 & 0xFF) / 255.0F;
        float g = (float) (color >> 8 & 0xFF) / 255.0F;
        float b = (float) (color & 0xFF) / 255.0F;
        this.setColor(r, g, b);
    }

    protected boolean spawnWhite() {
        return true;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SprayParticleEffect> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(SprayParticleEffect params, ClientWorld world, double x, double y, double z, double velX, double velY, double velZ) {
            return new SprayParticle(world, x, y, z, velX, velY, velZ, params, spriteProvider);
        }
    }
}
