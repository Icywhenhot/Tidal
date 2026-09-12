package net.superkat.wavify.particles;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.superkat.wavify.WavifyParticles;
import net.superkat.wavify.river.RiverFlow;
import net.superkat.wavify.util.WavifyColors;
import net.superkat.wavify.wave.WavifyWaveHandler;

import org.joml.Quaternionf;

import java.util.List;

public class SprayParticle extends SingleQuadParticle {
    private static final double MAX_SQUARED_COLLISION_CHECK_DISTANCE = Mth.square(100.0);
    protected final SpriteSet spriteProvider;

    public float yaw;
    public float intensity;
    private boolean stopped;

    private final boolean white;
    private float sprayRoll;
    private float oSprayRoll;

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

        this.white = params.isWhite();

        if(!this.white) {
            this.level.addParticle(new SprayParticleEffect(yaw, intensity, this.quadSize, true), x, y, z, velX, velY, velZ);
            this.updateWaterColor();
        }

        this.sprayRoll = 15 * intensity * 5f;

        this.setSpriteFromAge(this.spriteProvider);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        if(this.quadSize <= 0f) {
            this.remove();
            return;
        }

        if(enteredWater()) {
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
            return;
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
    public void extract(QuadParticleRenderState state, Camera camera, float tickDelta) {
        Quaternionf rotation = new Quaternionf();
        rotation.rotateX((float) Math.toRadians(-90f));
        rotation.rotateZ((float) Math.toRadians(-90f - this.yaw));
        float angle = Mth.lerp(tickDelta, this.oSprayRoll, this.sprayRoll);
        rotation.rotateX((float) Math.toRadians(angle));
        this.extractRotatedQuad(state, camera, rotation, tickDelta);
        rotation.rotateY((float) Math.toRadians(180f));
        this.extractRotatedQuad(state, camera, rotation, tickDelta);
    }

    @Override
    protected void extractRotatedQuad(QuadParticleRenderState state, Camera camera, Quaternionf rotation, float tickDelta) {
        Vec3 cameraPos = camera.position();
        float x = (float) (Mth.lerp(tickDelta, this.xo, this.x) - cameraPos.x());
        float y = (float) (Mth.lerp(tickDelta, this.yo, this.y) - cameraPos.y()) + (this.white ? 0.125f : 0.025f);
        float z = (float) (Mth.lerp(tickDelta, this.zo, this.z) - cameraPos.z());
        this.extractRotatedQuad(state, rotation, x, y, z, tickDelta);
    }

    private boolean enteredWater() {
        if (WavifyWaveHandler.posIsWater(this.level, this.getBlockPos().offset(0, 1, 0))) return true;

        int surfaceY = RiverFlow.surfaceWaterY(this.level, this.x, this.z, Mth.floor(this.y));
        if (surfaceY == Integer.MIN_VALUE) return false;

        BlockPos surfacePos = BlockPos.containing(this.x, surfaceY, this.z);
        return this.y <= surfaceY + this.level.getFluidState(surfacePos).getHeight(this.level, surfacePos);
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

    public void updateWaterColor() {
        int color = WavifyColors.getWaterColor(this.level, this.getBlockPos());
        this.setColor(WavifyColors.red(color), WavifyColors.green(color), WavifyColors.blue(color));
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
