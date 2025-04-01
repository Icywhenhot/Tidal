package net.superkat.tidal.wave;

import com.google.common.collect.Sets;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.superkat.tidal.TidalParticles;
import net.superkat.tidal.particles.SprayParticleEffect;
import org.jetbrains.annotations.Range;

import java.util.List;
import java.util.Set;

/**
 * A total mess of a class which handles wave position/movement, scale, color, and lifecycle of waves.<br><br>
 * <p>
 * i actually dislike this class a lot it is very incomprehensible
 */
public class Wave {
    private static final double MAX_SQUARED_COLLISION_CHECK_DISTANCE = MathHelper.square(100.0);

    //TODO - wave scales
    //TODO - spary particle width
    //TODO - fix fall washing up

    public ClientWorld world;
    public BlockPos spawnPos;
    public float yaw; //wave's yaw in degrees (in theory)
    public boolean bigWave;

    public Box box;
    public float scale;
    public float width;
    public float length;
    public float pitch = 0f;
    public float x;
    public float y;
    public float z;
    public float prevX;
    public float prevY;
    public float prevZ;

    public float velX;
    public float velY;
    public float velZ;

    public int age;
    public int maxAge;
    public boolean dead = false;
    public int maxWashingAge = 60;
    public int maxWaterAge = 100;
    public boolean drowningAway = false;
    public int ageUponWhichThisWaveHasOfficiallyJoinedEthoInBecomingWashedUp;

    public BlockState beneathBlock = Blocks.WATER.getDefaultState();
    public boolean aboveWater = true;
    public boolean washingUp = false;
    public boolean hitBlock = false;
    public int hitBlockAge;
    public boolean ending = false;

    public boolean waterfallMode = false;
    public boolean waterfallSplashed = false;

    public float red = 1f;
    public float green = 1f;
    public float blue = 1f;
    public float alpha = 1f;

    public Wave(ClientWorld world, BlockPos spawnPos, float yaw, float yOffset, boolean bigWave) {
        this.world = world;
        this.spawnPos = spawnPos;
        this.yaw = yaw;
        this.bigWave = bigWave;

        if (this.bigWave) {
            this.scale = 3f;
            this.length = 1.5f;
            this.width = 1f;
            this.maxAge = 300;
        } else {
            this.scale = 2f;
            this.length = 1f;
            this.width = 2f;
            this.maxAge = 250;
        }

        this.x = spawnPos.getX() + 0.5f;
        this.y = spawnPos.getY() + Math.abs(yOffset) + 0.15f;
        this.z = spawnPos.getZ() + 0.5f;

        float f = 0.2f / 2.0F;
        float g = 0.2f;
        this.box = (new Box(x - (double) f, y, z - (double) f, x + (double) f, y + (double) g, z + (double) f)).expand(this.scale / 4f, 0, this.scale / 4f);
        float speed = 0.115f;


        this.velX = (float) (Math.cos(Math.toRadians(yaw)) * speed);
        this.velZ = (float) (Math.sin(Math.toRadians(yaw)) * speed);

        this.alpha = 0f;
    }

    public int getWashingAge() {
        return this.age - this.ageUponWhichThisWaveHasOfficiallyJoinedEthoInBecomingWashedUp;
    }

    public BlockPos getBlockPos() {
        return BlockPos.ofFloored(this.x, this.y, this.z);
    }

    public Set<BlockPos> getCoveredBlocks() {
        Set<BlockPos> set = Sets.newHashSet();
        BlockPos currentPos = this.getBlockPos();

        int extra = 0;
        if (this.bigWave && this.getWashingAge() >= 13) {
            extra = this.getWashingAge() <= 40 ? 3 : 1;
        }
        int usedWidth = (int) (this.width - (this.bigWave ? 0 : 1)) + extra;
        for (BlockPos pos : BlockPos.iterate(currentPos.add(-usedWidth, -1, -usedWidth), currentPos.add(usedWidth, -1, usedWidth))) {
            if (TidalWaveHandler.posIsWater(world, pos) || world.isAir(pos)) continue;
            set.add(new BlockPos(pos));
        }
        return set;
    }

    public void tick() {
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        if (updateWashingUp()) { // wave has hit shore
            if (this.getWashingAge() <= 10) { // just hit shore - immediate slowdown
                this.velX *= 0.875f;
                this.velY = -0.0005f;
                this.velZ *= 0.875f;
            } else if (washBounce()) { // sometime after shore - slight bounce
                this.velX *= 1.2f;
                this.velZ *= 1.2f;
            } else { // remaining time in shore - continue slowing down until despawn
                this.velX *= 0.9f;
                this.velZ *= 0.9f;
            }

            this.ending = Math.abs(this.velX) <= 0.03f && Math.abs(this.velZ) <= 0.3f;

            float addedLength = Math.abs(velX) * (this.bigWave ? 1 : 0.75f);
            this.length += addedLength;
            if (this.getWashingAge() >= maxWashingAge) {
                this.markDead();
            }
        } else {
            this.updateWaterColor();
            if (drowningAway) { // wave is despawning in water because it didn't hit shore within reasonable time
                this.length -= 0.1f;
                this.velY -= 0.005f;
                if (this.length <= 0f) this.markDead();
            }

            if (this.alpha < 1f) this.alpha += 0.05f; //fade in
        }

        if (this.hitBlock && this.age - this.hitBlockAge >= 2) {
            this.markDead();
        }

        this.move(this.velX, this.velY, this.velZ);
        this.updateBeneathBlock();
    }

    public void move(float velX, float velY, float velZ) {
        float initVelX = velX;
        float initVelY = velY;
        float initVelZ = velZ;
        if ((velX != 0.0 || velY != 0.0 || velZ != 0.0) && velX * velX + velY * velY + velZ * velZ < MAX_SQUARED_COLLISION_CHECK_DISTANCE) {
            Vec3d vec3d = Entity.adjustMovementForCollisions(null, new Vec3d(velX, velY, velZ), this.getHitBox(), this.world, List.of());
            velX = (float) vec3d.x;
            velY = (float) vec3d.y;
            velZ = (float) vec3d.z;
        }

        if (initVelX != velX || initVelZ != velZ) {
            this.spray();
        }

        if (velX != 0.0 || velY != 0.0 || velZ != 0.0) {
            this.box = this.box.offset(velX, velY, velZ);
            this.prevX = this.x;
            this.prevY = this.y;
            this.prevZ = this.z;
            this.x = (float) (box.minX + box.maxX) / 2f;
            this.y = (float) box.minY;
            this.z = (float) (box.minZ + box.maxZ) / 2f;
        }
    }

    // wave hit block and should spray - intensity depends on current speed & and if it was washing up
    public void spray() {
        if (this.hitBlock) return;

        if (!drowningAway) {
            float sprayIntensity;
            if (this.isWashingUp()) {
                sprayIntensity = getWashingAge() / 128f;
                if (washBounce()) sprayIntensity *= 2f;
            } else {
                sprayIntensity = ((float) this.age / this.maxAge) * 2.5f / (this.age / 16f);
            }

            double splashX = this.x + this.velX * 10;
            double splashZ = this.z + this.velZ * 10;

            for (int i = 0; i < 3; i++) {
                this.world.addParticle(TidalParticles.SPLASH_PARTICLE, splashX, this.y, splashZ, this.world.random.nextGaussian() * 0.1f, Math.abs(this.world.random.nextGaussian()) * 0.1f + 0.1f, this.world.random.nextGaussian() * 0.1f);
                if (this.bigWave) {
                    this.world.addParticle(TidalParticles.BIG_SPLASH_PARTICLE, splashX + this.world.random.nextGaussian() / 2f, this.y, splashZ + this.world.random.nextGaussian() / 2f, 0, 0.01, 0);
                }
            }


            this.world.addParticle(new SprayParticleEffect(this.yaw - 180f, sprayIntensity, this.scale), splashX, this.y - 0.05f, splashZ, -this.velX, 0, -this.velZ);

            this.velX = 0;
            this.velY = 0;
            this.velZ = 0;
        }


        this.hitBlockAge = this.age;
        this.hitBlock = true;
    }

    public boolean updateWashingUp() {
        if (!washingUp && !aboveWater && !drowningAway) {
            if (beneathBlock.isAir()) {
                this.waterfallMode = true;
                this.velY = MathHelper.clamp(this.velY - 0.01f, -1.5f, 0);
                this.pitch += 1 + Math.abs(velY) * 5;
            } else {
                this.velY = 0;
                this.pitch = 0;
                this.washingUp = true;
                this.ageUponWhichThisWaveHasOfficiallyJoinedEthoInBecomingWashedUp = this.age;
            }
        }

        if(this.waterfallMode && this.aboveWater && !this.waterfallSplashed && TidalWaveHandler.posIsWater(this.world, this.getBlockPos())) {
            this.waterfallSplashed = true;
            int splashAmount = this.bigWave ? 7 : 3;
            float splashIntensity = this.bigWave ? 0.2f : 0.1f;
            double splashX = this.x + this.velX * 3;
            double splashZ = this.z + this.velZ * 3;

            for (int i = 0; i < this.width; i++) {
                for (int j = 0; j < splashAmount; j++) {
                    this.world.addParticle(TidalParticles.SPLASH_PARTICLE,
                            splashX + this.world.random.nextGaussian(),
                            this.y,
                            splashZ + this.world.random.nextGaussian(),
                            this.world.random.nextGaussian() * splashIntensity,
                            Math.abs(this.world.random.nextGaussian()) * splashIntensity + splashIntensity,
                            this.world.random.nextGaussian() * splashIntensity);
                }
            }

            for (int i = 0; i < splashAmount; i++) {
            }
        }

        if (!drowningAway && !washingUp && this.age >= this.maxWaterAge) {
            this.drowningAway = true;
        }

        return this.washingUp;
    }

    public boolean isWashingUp() {
        return this.washingUp;
    }

    private boolean washBounce() {
        return this.getWashingAge() >= 12 && this.getWashingAge() <= 17;
    }

    public void updateBeneathBlock() {
        this.beneathBlock = world.getBlockState(this.getBlockPos().add(0, -1, 0));
        this.aboveWater = TidalWaveHandler.stateIsWater(beneathBlock);
    }

    public Box getBoundingBox() {
        return this.box.expand(0.5);
    }

    public Box getHitBox() {
        if (this.isWashingUp()) {
            float yawRadians = (float) Math.toRadians(this.yaw); // this took way to long to figure out ( ͡ಠ ʖ̯ ͡ಠ)
            float usedLength = this.bigWave ? this.length * 1.5f : this.length / 16f;
            return this.getBoundingBox().stretch(usedLength * Math.cos(yawRadians), 0, usedLength * Math.sin(yawRadians));
        }
        return this.getBoundingBox();
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void updateWaterColor() {
        int color = BiomeColors.getWaterColor(this.world, this.getBlockPos());
        float r = (float) (color >> 16 & 0xFF) / 255.0F;
        float g = (float) (color >> 8 & 0xFF) / 255.0F;
        float b = (float) (color & 0xFF) / 255.0F;
        this.setColor(r, g, b); // colorhelp here?
    }

    /**
     * @param red   Float 0f through 1f
     * @param green Float 0f through 1f
     * @param blue  Float 0f through 255f - nah I'm just kidding its 0f through 1f
     */
    public void setColor(@Range(from = 0, to = 1) float red, @Range(from = 0, to = 1) float green, @Range(from = 0, to = 1) float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public float getX(float delta) {
        return MathHelper.lerp(delta, this.prevX, this.x);
    }

    public float getY(float delta) {
        return MathHelper.lerp(delta, this.prevY, this.y);
    }

    public float getZ(float delta) {
        return MathHelper.lerp(delta, this.prevZ, this.z);
    }

    public int getAge() {
        return this.isWashingUp() ? this.getWashingAge() : this.age;
    }

    public int getMaxAge() {
        return this.isWashingUp() ? this.maxWashingAge : this.maxAge;
    }

    public int getLight() {
        //emissive during full moon :)
        if (this.world.getMoonPhase() == 0 && this.world.getTimeOfDay() >= 12000)
            return LightmapTextureManager.pack(15, 15);
        BlockPos pos = this.getBlockPos().add(0, 1, 0);
        int blockLight = this.world.getLightLevel(LightType.BLOCK, pos);
        int skylight = this.world.getLightLevel(LightType.SKY, pos);
        return LightmapTextureManager.pack(blockLight, skylight);
    }

    public void markDead() {
        this.dead = true;
    }

    public boolean isDead() {
        return this.dead;
    }

}
