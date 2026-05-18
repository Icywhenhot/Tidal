package net.superkat.tidal.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.superkat.tidal.TidalParticles;
import net.superkat.tidal.wave.TidalWaveHandler;
import org.joml.Quaternionf;

import java.util.List;

public class SprayParticle extends BillboardParticle {
    private static final double MAX_SQUARED_COLLISION_CHECK_DISTANCE = MathHelper.square(100.0);
    protected final SpriteProvider spriteProvider;

    public float yaw;
    public float intensity;
    private boolean stopped;

    public SprayParticle(ClientWorld world, double x, double y, double z, double velX, double velY, double velZ, SprayParticleEffect params, SpriteProvider spriteProvider) {
        super(world, x, y, z, velX, velY, velZ, spriteProvider.getFirst());
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
            this.world.addParticleClient(new WhiteSprayParticleEffect(yaw, intensity, this.scale), x, y, z, velX, velY, velZ);
            this.updateWaterColor(); //only need to update on spawn because it lasts for so little time
        }

        this.zRotation = 15 * intensity * 5f;

        this.updateSprite(this.spriteProvider);
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
                this.world.addParticleClient(TidalParticles.SPLASH_PARTICLE,
                        this.x + this.random.nextGaussian(), this.y + 1,
                        this.z + this.random.nextGaussian(),
                        this.random.nextGaussian() * 0.05f,
                        Math.abs(this.world.random.nextGaussian()) * 0.1f + MathHelper.clamp(intensity, 0.1, 0.3),
                        this.random.nextGaussian() * 0.05f);

                this.world.addParticleClient(ParticleTypes.BUBBLE,
                        this.x + this.random.nextGaussian() / 2f, this.y + 1,
                        this.z + this.random.nextGaussian() / 2f,
                        this.random.nextGaussian() / 8f,
                        0,
                        this.random.nextGaussian() / 8f);
            }
            this.markDead();
        }

        this.lastZRotation = this.zRotation;
        if(this.velocityY != 0 && !onGround) {
            this.zRotation = this.zRotation + (float) this.velocityY * 35f;
        } else {
            this.zRotation = 0f;
        }

        this.updateSprite(this.spriteProvider);
    }

    @Override
    public void render(BillboardParticleSubmittable submittable, Camera camera, float tickDelta) {
        Quaternionf quaternionf = new Quaternionf();
        quaternionf.rotateX((float) Math.toRadians(-90f));
        quaternionf.rotateZ((float) Math.toRadians(-90f - this.yaw));
        float angle = MathHelper.lerp(tickDelta, this.lastZRotation, this.zRotation);
        quaternionf.rotateX((float) Math.toRadians(angle));
        super.render(submittable, camera, quaternionf, tickDelta);
        quaternionf.rotateY((float) Math.toRadians(180f));
        super.render(submittable, camera, quaternionf, tickDelta);
    }

    @Override
    public void move(double dx, double dy, double dz) {
        if (!this.stopped) {
            double e = dy;
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
    protected RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SprayParticleEffect> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SprayParticleEffect params, ClientWorld world, double x, double y, double z, double velX, double velY, double velZ, Random random) {
            return new SprayParticle(world, x, y, z, velX, velY, velZ, params, spriteProvider);
        }
    }
}
