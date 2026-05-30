package net.superkat.wavify.wave;

import com.google.common.collect.Sets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LightLayer;
import net.superkat.wavify.WavifyParticles;
import net.superkat.wavify.particles.SprayParticleEffect;
import net.superkat.wavify.util.WavifyColors;
import org.joml.Vector3f;
import org.jetbrains.annotations.Range;

import java.util.List;
import java.util.Set;

/**
 * A total mess of a class which handles wave position/movement, scale, color, and lifecycle of waves.<br><br>
 * <p>
 * i actually dislike this class a lot it is very incomprehensible
 */
public class Wave {
    private static final double MAX_SQUARED_COLLISION_CHECK_DISTANCE = Mth.square(100.0);

    //TODO - wave scales
    //TODO - spary particle width
    //TODO - fix fall washing up

    public ClientLevel level;
    public BlockPos spawnPos;
    public float yaw; //wave's yaw in degrees (in theory)
    public boolean bigWave;

    public AABB box;
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

    public BlockState beneathBlock = Blocks.WATER.defaultBlockState();
    public boolean aboveWater = true;
    public boolean washingUp = false;
    public boolean hitBlock = false;
    public int hitBlockAge;
    public boolean ending = false;

    public boolean waterfallMode = false;
    public boolean waterfallSplashed = false;
    public float prevYaw = 0f;

    public float red = 1f;
    public float green = 1f;
    public float blue = 1f;
    public float alpha = 1f;

    public Wave(ClientLevel world, BlockPos spawnPos, float yaw, float yOffset, boolean bigWave) {
        this.level = world;
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
        this.box = (new AABB(x - (double) f, y, z - (double) f, x + (double) f, y + (double) g, z + (double) f)).inflate(this.scale / 4f, 0, this.scale / 4f);
        float speed = 0.115f;


        this.velX = (float) (Math.cos(Math.toRadians(yaw)) * speed);
        this.velZ = (float) (Math.sin(Math.toRadians(yaw)) * speed);

        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;
        this.prevYaw = this.yaw;

        this.alpha = 0f;
    }

    public int getWashingAge() {
        return this.age - this.ageUponWhichThisWaveHasOfficiallyJoinedEthoInBecomingWashedUp;
    }

    public BlockPos getBlockPos() {
        return BlockPos.containing(this.x, this.y, this.z);
    }

    public Set<BlockPos> getCoveredBlocks() {
        Set<BlockPos> set = Sets.newHashSet();
        BlockPos currentPos = this.getBlockPos();

        int extra = 0;
        if (this.bigWave && this.getWashingAge() >= 13) {
            extra = this.getWashingAge() <= 40 ? 3 : 1;
        }
        int usedWidth = (int) (this.width - (this.bigWave ? 0 : 1)) + extra;
        for (BlockPos pos : BlockPos.betweenClosed(currentPos.offset(-usedWidth, -1, -usedWidth), currentPos.offset(usedWidth, -1, usedWidth))) {
            if (WavifyWaveHandler.posIsWater(this.level, pos) || this.level.isEmptyBlock(pos)) continue;
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
            Vec3 vec3d = Entity.collideBoundingBox(null, new Vec3(velX, velY, velZ), this.getHitBox(), this.level, List.of());
            velX = (float) vec3d.x;
            velY = (float) vec3d.y;
            velZ = (float) vec3d.z;
        }

        if (initVelX != velX || initVelZ != velZ) {
            this.spray();
        }

        if (velX != 0.0 || velY != 0.0 || velZ != 0.0) {
            this.box = this.box.move(velX, velY, velZ);
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
            int sprayAmount = this.bigWave ? 3 : 1;
            float sprayIntensity;
            if (this.isWashingUp()) {
                sprayIntensity = getWashingAge() / 128f;
                if (washBounce()) sprayIntensity *= 2f;
            } else {
                sprayIntensity = ((float) this.age / this.maxAge) * 2.5f / (this.age / 16f);
            }

            double splashX = this.x + this.velX * 10;
            double splashZ = this.z + this.velZ * 10;

            for (int i = 0; i < sprayAmount; i++) {
            this.level.addParticle(WavifyParticles.SPLASH_PARTICLE.get(), splashX, this.y, splashZ, this.level.getRandom().nextGaussian() * 0.1f, Math.abs(this.level.getRandom().nextGaussian()) * 0.1f + 0.1f, this.level.getRandom().nextGaussian() * 0.1f);
                if (this.bigWave) {
            this.level.addParticle(WavifyParticles.BIG_SPLASH_PARTICLE.get(), splashX + this.level.getRandom().nextGaussian() / 2f, this.y, splashZ + this.level.getRandom().nextGaussian() / 2f, 0, 0.01, 0);
                }
            }


            this.level.addParticle(new SprayParticleEffect(this.yaw - 180f, sprayIntensity, this.scale), splashX, this.y - 0.05f, splashZ, -this.velX, 0, -this.velZ);

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
                this.velY = Mth.clamp(this.velY - 0.01f, -1.5f, 0);
                this.pitch += 1 + Math.abs(velY) * 5;
            } else {
                this.velY = 0;
                this.pitch = 0;
                this.washingUp = true;
                this.ageUponWhichThisWaveHasOfficiallyJoinedEthoInBecomingWashedUp = this.age;
            }
        }

        if(this.waterfallMode && this.aboveWater && !this.waterfallSplashed && WavifyWaveHandler.posIsWater(this.level, this.getBlockPos())) {
            this.waterfallSplashed = true;
            int splashAmount = this.bigWave ? 7 : 3;
            float splashIntensity = this.bigWave ? 0.2f : 0.1f;
            double splashX = this.x + this.velX * 3;
            double splashZ = this.z + this.velZ * 3;

            for (int i = 0; i < this.width; i++) {
                for (int j = 0; j < splashAmount; j++) {
            this.level.addParticle(WavifyParticles.SPLASH_PARTICLE.get(),
                            splashX + this.level.getRandom().nextGaussian(),
                            this.y,
                            splashZ + this.level.getRandom().nextGaussian(),
                            this.level.getRandom().nextGaussian() * splashIntensity,
                            Math.abs(this.level.getRandom().nextGaussian()) * splashIntensity + splashIntensity,
                            this.level.getRandom().nextGaussian() * splashIntensity);
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
        this.beneathBlock = this.level.getBlockState(this.getBlockPos().offset(0, -1, 0));
        this.aboveWater = WavifyWaveHandler.stateIsWater(beneathBlock);
    }

    public AABB getBoundingBox() {
        return this.box.inflate(0.5);
    }

    public AABB getHitBox() {
        if (this.isWashingUp()) {
            float yawRadians = (float) Math.toRadians(this.yaw); // this took way to long to figure out ( ͡ಠ ʖ̯ ͡ಠ)
            float usedLength = this.bigWave ? this.length * 1.5f : this.length / 16f;
            return this.getBoundingBox().expandTowards(usedLength * Math.cos(yawRadians), 0, usedLength * Math.sin(yawRadians));
        }
        return this.getBoundingBox();
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void offsetVertical(float offset) {
        this.y += offset;
        this.prevY += offset;
        syncBoxToCurrentPosition();
    }

    public void updateWaterColor() {
        Vector3f color = WavifyColors.getWaterColorVec(this.level, this.getBlockPos());
        this.setColor(color.x, color.y, color.z);
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
        return Mth.lerp(delta, this.prevX, this.x);
    }

    public float getY(float delta) {
        return Mth.lerp(delta, this.prevY, this.y);
    }

    public float getZ(float delta) {
        return Mth.lerp(delta, this.prevZ, this.z);
    }

    public float getYaw(float delta) {
        return Mth.rotLerp(delta, this.prevYaw, this.yaw);
    }

    public int getAge() {
        return this.isWashingUp() ? this.getWashingAge() : this.age;
    }

    public int getMaxAge() {
        return this.isWashingUp() ? this.maxWashingAge : this.maxAge;
    }

    public int getLight() {
        //emissive during full moon :)
        long dayTime = this.level.getGameTime();
        if (canFullMoonGlow() && (int)(dayTime / 24000L % 8L) == 0 && dayTime % 24000L >= 12000)
            return LightTexture.pack(15, 15);
        BlockPos pos = this.getBlockPos().offset(0, 1, 0);
        int blockLight = this.level.getBrightness(LightLayer.BLOCK, pos);
        int skylight = this.level.getBrightness(LightLayer.SKY, pos);
        return LightTexture.pack(blockLight, skylight);
    }

    protected boolean canFullMoonGlow() {
        return true;
    }

    public int getRenderColumnCount() {
        return Math.max(1, Math.round(this.width));
    }

    public float getRenderColumnLateralOffset(int columnIndex, int columnCount) {
        return columnIndex - (columnCount - 1) * 0.5f;
    }

    public float getRenderColumnForwardOffset(int columnIndex, int columnCount) {
        return 0f;
    }

    public float getRenderColumnVerticalOffset(int columnIndex, int columnCount) {
        return 0f;
    }

    public float getRenderColumnWidth(int columnIndex, int columnCount) {
        return 1f;
    }

    public float getRenderColumnLength(int columnIndex, int columnCount) {
        return this.length;
    }

    protected void capturePreviousState() {
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;
        this.prevYaw = this.yaw;
    }

    protected void syncBoxToCurrentPosition() {
        float f = 0.2f / 2.0F;
        float g = 0.2f;
        this.box = (new AABB(x - (double) f, y, z - (double) f, x + (double) f, y + (double) g, z + (double) f)).inflate(this.scale / 4f, 0, this.scale / 4f);
    }

    public void markDead() {
        this.dead = true;
    }

    public boolean isDead() {
        return this.dead;
    }

}
