package net.superkat.wavify.particles;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class SprayParticle extends TextureSheetParticle {
    private static final double MAX_SQUARED_COLLISION_CHECK_DISTANCE = Mth.square(100.0);
    protected final SpriteSet spriteProvider;

    public float yaw;
    public float intensity;
    private boolean stopped;

    private final boolean white;
    private float sprayRoll;

    public SprayParticle(ClientLevel level, double x, double y, double z, double velX, double velY, double velZ, SprayParticleEffect params, SpriteSet spriteProvider) {
        super(level, x, y, z, velX, velY, velZ);
        this.spriteProvider = spriteProvider;
        this.white = params.isWhite();

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
            return;
        }

        if(this.yd != 0 && !onGround) {
            this.sprayRoll = this.sprayRoll + (float) this.yd * 35f;
        } else {
            this.sprayRoll = 0f;
        }

        this.setSpriteFromAge(this.spriteProvider);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float tickDelta) {
        Quaternionf rotation = new Quaternionf();
        rotation.rotateX((float) Math.toRadians(-90f));
        rotation.rotateZ((float) Math.toRadians(-90f - this.yaw));
        rotation.rotateX((float) Math.toRadians(this.sprayRoll));
        sprayQuad(buffer, camera, rotation, tickDelta);
        rotation.rotateY((float) Math.toRadians(180f));
        sprayQuad(buffer, camera, rotation, tickDelta);
    }

    private void sprayQuad(VertexConsumer buffer, Camera camera, Quaternionf rotation, float tickDelta) {
        Vec3 cameraPos = camera.getPosition();
        float x = (float) (Mth.lerp(tickDelta, this.xo, this.x) - cameraPos.x());
        float y = (float) (Mth.lerp(tickDelta, this.yo, this.y) - cameraPos.y()) + (this.white ? 0.125f : 0.025f);
        float z = (float) (Mth.lerp(tickDelta, this.zo, this.z) - cameraPos.z());

        float size = this.getQuadSize(tickDelta);
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(tickDelta);

        sprayVertex(buffer, rotation, x, y, z, 1f, -1f, size, u1, v1, light);
        sprayVertex(buffer, rotation, x, y, z, 1f, 1f, size, u1, v0, light);
        sprayVertex(buffer, rotation, x, y, z, -1f, 1f, size, u0, v0, light);
        sprayVertex(buffer, rotation, x, y, z, -1f, -1f, size, u0, v1, light);
    }

    private void sprayVertex(VertexConsumer buffer, Quaternionf rotation, float x, float y, float z,
                             float offsetX, float offsetY, float size, float u, float v, int light) {
        Vector3f corner = new Vector3f(offsetX, offsetY, 0f).rotate(rotation).mul(size, 1f, size).add(x, y, z);
        buffer.addVertex(corner.x(), corner.y(), corner.z())
                .setUv(u, v)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light);
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
