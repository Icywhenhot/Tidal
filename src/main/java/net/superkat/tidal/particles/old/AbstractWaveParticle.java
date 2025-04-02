package net.superkat.tidal.particles.old;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;
import net.superkat.tidal.wave.TidalWaveHandler;
import org.apache.commons.compress.utils.Lists;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class AbstractWaveParticle extends SpriteBillboardParticle {
    protected static final double MAX_SQUARED_COLLISION_CHECK_DISTANCE = MathHelper.square(100.0);
    protected final SpriteProvider spriteProvider;

    public BlockState beneathBlock = Blocks.WATER.getDefaultState();
    public boolean aboveWater = true;
    public boolean returningBack = false;
    public boolean ending = false;
    public int turnBackAge = 0;
    public int maxTurnBackTicks = 60;

    public float yaw;
    public float speed;
    protected double maxVelX;
    protected double maxVelZ;
    public int spanWidth;
    public List<BlockPos> positions = Lists.newArrayList();
    public List<Float> yaws = Lists.newArrayList();

    public double hitShoreX;
    public double hitShoreZ;
    public float extraLength = 0;

    public AbstractWaveParticle(ClientWorld world, double x, double y, double z, double velX, double velY, double velZ, AbstractWaveParticleEffect params, SpriteProvider spriteProvider) {
        super(world, x, y, z, velX, velY, velZ);
        this.spriteProvider = spriteProvider;
        this.setSpriteForAge(spriteProvider);

        this.yaw = params.getYaw();
        this.speed = params.getSpeed();
        this.scale = params.getScale();
        this.spanWidth = params.getWidth();
        this.positions = params.getPositions();
        this.yaws = params.getYaws();
        this.maxAge = params.getLifetime();

        this.velocityX = Math.cos(Math.toRadians(yaw)) * speed;
        this.velocityY = 0;
        this.velocityZ = Math.sin(Math.toRadians(yaw)) * speed;
        this.maxVelX = this.velocityX;
        this.maxVelZ = this.velocityZ;

        this.maxTurnBackTicks = 60;

        this.velocityMultiplier = 1f;
        this.gravityStrength = 0f;
        this.ascending = false;
        this.collidesWithWorld = true;

        for (int i = 0; i < this.positions.size(); i++) {
            BlockPos pos = this.positions.get(i);
            this.world.addParticle(ParticleTypes.EGG_CRACK, pos.getX(), pos.getY() + 2, pos.getZ(), 0, 0, 0);
            Vector3f color = new Vector3f(1f, 1f, 1f);
            float yaw = this.yaws.get(i);
            DebugWaveMovementParticle.DebugWaveMovementParticleEffect particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(
                    color,
                    1f,
                    yaw,
                    0.3f,
                    20
            );
            this.world.addParticle(particleEffect, pos.getX(), pos.getY() + 1.5, pos.getZ(), 0, 0, 0);
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

    public void updateBeneathBlock() {
        this.beneathBlock = world.getBlockState(this.getPos().add(0, -1, 0));
        this.aboveWater = TidalWaveHandler.stateIsWater(beneathBlock);
    }

    public boolean returningBack() {
        if(!this.returningBack && !this.aboveWater) {
            this.returningBack = true;
            this.turnBackAge = this.age;
            this.hitShoreX = this.x;
            this.hitShoreZ = this.z;
        }
        return this.returningBack;
    }

    public void onBlockHit() {

    }

    public void spray() {
        for (int i = 1; i < this.spanWidth; i++) {
            for (int j = 0; j < 15; j++) {
                this.world.addParticle(ParticleTypes.SPLASH, this.x + 0.5 + this.random.nextBetween(-1, 1), this.y, this.z + 0.5 + this.random.nextBetween(-1, 1), this.random.nextGaussian() * 4 * i, this.random.nextGaussian() * 8 * (i / 2f), this.random.nextGaussian() * 4 * i);
            }
        }
        this.markDead();
    }

    @Override
    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;
        if (this.age++ >= this.maxAge || this.scale <= 0f) {
            this.markDead();
        }
        this.move(velocityX, velocityY, velocityZ);
        this.updateBeneathBlock();

        if(this.returningBack()) {

            extraLength = (float) (Math.abs(this.x - this.hitShoreX) + Math.abs(this.z - hitShoreZ)) / 2f;

            if(!ending) {
                this.velocityX *= 0.9f;
                this.velocityY = -0.0005f;
                this.velocityZ *= 0.9f;
                this.ending = Math.abs(this.velocityX) <= 0.03f && Math.abs(this.velocityZ) <= 0.03f;
                if(this.ending) {
                    this.collidesWithWorld = false;
                    this.turnBackAge = this.age;
//                    this.velocityY = -0.001f;
                }
            } else {
                if(this.age - this.turnBackAge >= this.turnBackAge || TidalWaveHandler.stateIsWater(beneathBlock)) {
                    this.markDead();
                }
                float delta = (float) (this.age - this.turnBackAge - 10) / ((float) this.maxTurnBackTicks);
                this.velocityX = (-this.maxVelX) * delta;
                this.velocityY = -0.001f;
                this.velocityZ = (-this.maxVelZ) * delta;
            }
        }

        this.setSpriteForAge(spriteProvider);
    }

    @Override
    public void move(double dx, double dy, double dz) {
        double initVelX = dx;
        double initVelY = dy;
        double initVelZ = dz;
        if (this.collidesWithWorld && (dx != 0.0 || dy != 0.0 || dz != 0.0) && dx * dx + dy * dy + dz * dz < MAX_SQUARED_COLLISION_CHECK_DISTANCE) {
            Vec3d vec3d = Entity.adjustMovementForCollisions(null, new Vec3d(dx, dy, dz), this.getBoundingBox(), this.world, List.of());
            dx = vec3d.x;
            dy = vec3d.y;
            dz = vec3d.z;
        }

        if(initVelX != dx || initVelZ != dz) {
            this.onBlockHit();
        }

        if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
            this.setBoundingBox(this.getBoundingBox().offset(dx, dy, dz));
            this.repositionFromBoundingBox();
        }
    }

    @Override
    public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        Quaternionf quaternionf = new Quaternionf();
        quaternionf.rotateX((float) Math.toRadians(-90f));
        quaternionf.rotateZ((float) Math.toRadians(-90f - this.yaw));
        render(vertexConsumer, camera, quaternionf, tickDelta);
    }

    protected void render(VertexConsumer vertexConsumer, Camera camera, Quaternionf quaternionf, float tickDelta) {
        Vec3d vec3d = camera.getPos();
        float x = (float)(MathHelper.lerp(tickDelta, this.prevPosX, this.x) - vec3d.getX());
        float y = (float)(MathHelper.lerp(tickDelta, this.prevPosY, this.y) - vec3d.getY());
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
        for (int i = 0; i < this.spanWidth; i++) {
            this.vertex(vertexConsumer, quaternionf, x, y, z, 1f + i * 2, -1f, scale, maxU, maxV, light);
            this.vertex(vertexConsumer, quaternionf, x, y, z, 1f + i * 2, 1f, scale, maxU, minV, light);
            this.vertex(vertexConsumer, quaternionf, x, y, z, -1f + i * 2, 1f, scale, minU, minV, light);
            this.vertex(vertexConsumer, quaternionf, x, y, z, -1f + i * 2, -1f, scale, minU, maxV, light);
        }
//        if(this.extraLength > 0) {
//            int i = this.spanWidth;
//            y -= 0.1f;
//            this.vertex(vertexConsumer, quaternionf, x, y, z, -1f + i * 2, -extraLength - 1, scale, maxU, maxV, light);
//            this.vertex(vertexConsumer, quaternionf, x, y, z, -1f + i * 2, extraLength + 1, scale, maxU, minV, light);
//            this.vertex(vertexConsumer, quaternionf, x, y, z, -1f, extraLength + 1, scale, minU, minV, light);
//            this.vertex(vertexConsumer, quaternionf, x, y, z, -1f, -extraLength - 1, scale, minU, maxV, light);
//        }
    }

    private void vertex(
            VertexConsumer vertexConsumer, Quaternionf quaternionf,
            float x, float y, float z,
            float offsetX, float offsetY,
            float scale,
            float u, float v,
            int light
    ) {
        Vector3f vector3f = new Vector3f(offsetX, offsetY, 0.0F).rotate(quaternionf).mul(scale).add(x, y, z);
        vertexConsumer.vertex(vector3f.x(), vector3f.y(), vector3f.z()).texture(u, v).color(this.red, this.green, this.blue, this.alpha).light(light);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static abstract class Factory<T extends AbstractWaveParticleEffect> implements ParticleFactory<T> {
        protected final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(T params, ClientWorld world, double x, double y, double z, double velX, double velY, double velZ) {
            return new AbstractWaveParticle(world, x, y, z, velX, velY, velZ, params, spriteProvider);
        }
    }

}
