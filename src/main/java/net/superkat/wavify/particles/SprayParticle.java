package net.superkat.wavify.particles;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.superkat.wavify.WavifyParticles;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.joml.Quaternionf;

import java.util.List;

public class SprayParticle extends SingleQuadParticle {
    private static final double MAX_SQUARED_COLLISION_CHECK_DISTANCE = Mth.square(100.0);
    protected final SpriteSet spriteProvider;

    public float yaw;
    public float intensity;
    private boolean stopped;

    public SprayParticle(ClientLevel level, double x, double y, double z, double velX, double velY, double velZ, SprayParticleEffect params, SpriteSet spriteProvider) {
        super(level, x, y, z, velX, velY, velZ, spriteProvider.first());
        this.spriteProvider = spriteProvider;

        this.yaw = params.getYaw();
        this.intensity = params.getIntensity();
        float speed = (float) (Math.abs(velX) + Math.abs(velZ)) / 1.25f;
        this.xd = (float) (Math.cos(Math.toRadians(yaw)) * speed);
        this.yd = 0.15f * intensity;
        this.zd = (float) (Math.sin(Math.toRadians(yaw)) * speed);

        this.lifetime = (int) (50 + (intensity * 5f));

        this.quadSize = Mth.clamp(intensity * 4f, 1f, params.getScale() * 2f);
        this.hasPhysics = true;
        this.gravity = 0.5f;

        if(spawnWhite()) {
            this.level.addParticle(new WhiteSprayParticleEffect(yaw, intensity, this.quadSize), x, y, z, velX, velY, velZ);
            this.updateWaterColor();
        }

        this.roll = 15 * intensity * 5f;

        this.setSpriteFromAge(this.spriteProvider);
    }

    @Override
    public void tick() {
        super.tick();
        if(this.quadSize <= 0f) {
            this.remove();
            return;
        }

        if(WavifyWaveHandler.posIsWater(this.level, this.getBlockPos().offset(0, 1, 0))) {
            this.x -= this.xd * 8;
            this.z -= this.zd * 8f;
            for (int i = 0; i < 5; i++) {
                this.level.addParticle(WavifyParticles.SPLASH_PARTICLE.get(),
                        this.x + this.random.nextGaussian(), this.y + 1,
                        this.z + this.random.nextGaussian(),
                        this.random.nextGaussian() * 0.05f,
                        Math.abs(this.level.getRandom().nextGaussian()) * 0.1f + Mth.clamp(intensity, 0.1, 0.3),
                        this.random.nextGaussian() * 0.05f);

                this.level.addParticle(ParticleTypes.BUBBLE,
                        this.x + this.random.nextGaussian() / 2f, this.y + 1,
                        this.z + this.random.nextGaussian() / 2f,
                        this.random.nextGaussian() / 8f,
                        0,
                        this.random.nextGaussian() / 8f);
            }
            this.remove();
        }

        this.oRoll = this.roll;
        if(this.yd != 0 && !onGround) {
            this.roll = this.roll + (float) this.yd * 35f;
        } else {
            this.roll = 0f;
        }

        this.setSpriteFromAge(this.spriteProvider);
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float tickDelta) {
        Quaternionf quaternionf = new Quaternionf();
        quaternionf.rotateX((float) Math.toRadians(-90f));
        quaternionf.rotateZ((float) Math.toRadians(-90f - this.yaw));
        float angle = Mth.lerp(tickDelta, this.oRoll, this.roll);
        quaternionf.rotateX((float) Math.toRadians(angle));
        extractRotatedQuad(state, camera, quaternionf, tickDelta);
        quaternionf.rotateY((float) Math.toRadians(180f));
        extractRotatedQuad(state, camera, quaternionf, tickDelta);
    }

    @Override
    public void move(double dx, double dy, double dz) {
        if (!this.stopped) {
            double e = dy;
            if (this.hasPhysics && (dx != 0.0 || dy != 0.0 || dz != 0.0) && dx * dx + dy * dy + dz * dz < MAX_SQUARED_COLLISION_CHECK_DISTANCE) {
                //expanding bounding box to specifically account for mud and I guess soul sand too?
                Vec3 vec3d = Entity.collideBoundingBox(null, new Vec3(dx, dy, dz), this.getBoundingBox().inflate(0, 0.15, 0), this.level, List.of());
                dx = vec3d.x;
                dy = vec3d.y;
                dz = vec3d.z;
            }

            if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
                this.setBoundingBox(this.getBoundingBox().move(dx, dy, dz));
                this.setLocationFromBoundingbox();
            }

            this.onGround = e != dy && e < 0.0;
        }
    }

    public BlockPos getBlockPos() {
        return BlockPos.containing(this.x, this.y, this.z);
    }

    public void updateWaterColor() {
        int color = BiomeColors.getAverageWaterColor(this.level, this.getBlockPos());
        float r = (float) (color >> 16 & 0xFF) / 255.0F;
        float g = (float) (color >> 8 & 0xFF) / 255.0F;
        float b = (float) (color & 0xFF) / 255.0F;
        this.setColor(r, g, b);
    }

    protected boolean spawnWhite() {
        return true;
    }

    @Override
    public ParticleRenderType getGroup() {
        return ParticleRenderType.SINGLE_QUADS;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<SprayParticleEffect> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SprayParticleEffect params, ClientLevel level, double x, double y, double z, double velX, double velY, double velZ, RandomSource random) {
            return new SprayParticle(level, x, y, z, velX, velY, velZ, params, spriteProvider);
        }
    }
}
