package net.superkat.wavify.particles;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.superkat.wavify.WavifyParticles;
import net.superkat.wavify.util.WavifyColors;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.joml.Vector3f;

import java.util.List;

public class SprayParticle extends TextureSheetParticle {
    private static final double MAX_SQUARED_COLLISION_CHECK_DISTANCE = Mth.square(100.0);
    protected final SpriteSet spriteProvider;

    public float yaw;
    public float intensity;
    private boolean stopped;

    // Spray tilt, kept SEPARATE from the inherited Particle#roll field on purpose.
    // On MC 1.21.1 the vanilla particle render() auto-applies a non-zero `roll` as
    // quaternionf.rotateZ(lerp(oRoll, roll)) treating it as RADIANS. This class's roll
    // is in the ~75+ "degrees" range, so rotateZ(75 rad) ≈ 12 full turns - that was the
    // spray "spinning" on shore impact. We drive our own tilt through getFacingCameraMode
    // and leave the inherited roll at 0 so the base renderer never adds that extra spin.
    private float sprayRoll;
    private float oSprayRoll;

    public SprayParticle(ClientLevel level, double x, double y, double z, double velX, double velY, double velZ, SprayParticleEffect params, SpriteSet spriteProvider) {
        super(level, x, y, z, velX, velY, velZ);
        this.spriteProvider = spriteProvider;
        this.pickSprite(spriteProvider);

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
            Vector3f color = WavifyColors.getWaterColorVec(this.level, BlockPos.containing(x, y, z));
            this.setColor(color.x, color.y, color.z);
        }

        this.sprayRoll = 15 * intensity * 5f;

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

        this.oSprayRoll = this.sprayRoll;
        if(this.yd != 0 && !onGround) {
            this.sprayRoll = this.sprayRoll + (float) this.yd * 35f;
        } else {
            this.sprayRoll = 0f;
        }

        this.setSpriteFromAge(this.spriteProvider);
    }

    @Override
    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        return (quaternionf, camera, tickDelta) -> {
            quaternionf.rotateX((float) Math.toRadians(-90f));
            quaternionf.rotateZ((float) Math.toRadians(-90f - this.yaw));
            float angle = Mth.lerp(tickDelta, this.oSprayRoll, this.sprayRoll);
            quaternionf.rotateX((float) Math.toRadians(angle));
        };
    }

    @Override
    public void move(double dx, double dy, double dz) {
        if (!this.stopped) {
            double e = dy;
            if (this.hasPhysics && (dx != 0.0 || dy != 0.0 || dz != 0.0) && dx * dx + dy * dy + dz * dz < MAX_SQUARED_COLLISION_CHECK_DISTANCE) {
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

    protected boolean spawnWhite() {
        return true;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<SprayParticleEffect> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SprayParticleEffect params, ClientLevel level, double x, double y, double z, double velX, double velY, double velZ) {
            return new SprayParticle(level, x, y, z, velX, velY, velZ, params, spriteProvider);
        }
    }
}
