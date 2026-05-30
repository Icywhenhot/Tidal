package net.superkat.wavify.wave;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.superkat.wavify.WavifyParticles;
import net.superkat.wavify.particles.SprayParticleEffect;
import net.superkat.wavify.river.RiverFlowPlanner;

/**
 * Separate shallow-water standing wave, anchored behind an obstruction instead of traveling downstream.
 */
public class StandingRiverWave extends Wave {
    private final long reachId;
    private final BlockPos anchorWater;
    private final double anchorX;
    private final double anchorZ;
    private final double dirX;
    private final double dirZ;
    private final float energy;
    private final float baseScale;
    private final float baseLength;
    private final float baseY;
    private final float motionPhase;

    public StandingRiverWave(ClientLevel world, RiverFlowPlanner.StandingWaveCandidate candidate, BlockPos anchorWater, int trainIndex) {
        super(world, anchorWater.above(), candidate.yaw(), 0.4f, false);
        this.reachId = candidate.reachId();
        this.anchorWater = anchorWater;
        this.anchorX = anchorWater.getX() + 0.5;
        this.anchorZ = anchorWater.getZ() + 0.5;
        this.dirX = candidate.dirX();
        this.dirZ = candidate.dirZ();
        this.energy = Math.max(0.5f, candidate.energy() - trainIndex * 0.1f);
        this.setWidth(Math.max(3, candidate.width() - Math.min(1, trainIndex)));
        this.scale = 1.95f + this.width * 0.12f + this.energy * 0.15f;
        this.length = 1.2f + this.width * 0.05f + this.energy * 0.12f;
        this.baseScale = this.scale;
        this.baseLength = this.length;
        this.motionPhase = this.level.getRandom().nextFloat() * 20f + trainIndex * 2.5f;
        this.maxAge = 240 + this.level.getRandom().nextInt(40);
        this.maxWaterAge = this.maxAge;
        this.alpha = 0f;
        this.velX = 0f;
        this.velY = 0f;
        this.velZ = 0f;
        this.offsetVertical(RiverWave.RIVER_VERTICAL_OFFSET);
        this.baseY = this.y;
        syncBoxToCurrentPosition();
    }

    public long getReachId() {
        return this.reachId;
    }

    @Override
    public int getRenderColumnCount() {
        int columns = Math.max(3, Math.round(this.width));
        if ((columns & 1) == 0) columns++;
        return Math.min(columns, 5);
    }

    @Override
    public float getRenderColumnLateralOffset(int columnIndex, int columnCount) {
        return normalizedColumn(columnIndex, columnCount) * 0.68f;
    }

    @Override
    public float getRenderColumnForwardOffset(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        return -(0.08f + normalized * normalized * 0.32f);
    }

    @Override
    public float getRenderColumnVerticalOffset(int columnIndex, int columnCount) {
        return (1f - Math.abs(normalizedColumn(columnIndex, columnCount))) * 0.045f;
    }

    @Override
    public float getRenderColumnWidth(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        return Mth.lerp(normalized, 0.7f, 1.0f);
    }

    @Override
    public float getRenderColumnLength(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        return this.length * Mth.lerp(normalized, 0.7f, 1.05f);
    }

    @Override
    public void tick() {
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        capturePreviousState();
        updateWaterColor();

        if (!WavifyWaveHandler.posIsWater(this.level, this.anchorWater) || !this.level.getBlockState(this.anchorWater.above()).isAir()) {
            this.alpha = Math.max(0f, this.alpha - 0.08f);
            if (this.alpha <= 0.02f) {
                this.markDead();
            }
            return;
        }

        float pulse = Mth.sin((this.age + this.motionPhase) * (0.24f + this.energy * 0.05f));
        float crest = Math.abs(pulse);
        this.x = (float) (this.anchorX + this.dirX * pulse * 0.1f * this.energy);
        this.z = (float) (this.anchorZ + this.dirZ * pulse * 0.1f * this.energy);
        this.y = this.baseY + crest * 0.05f * this.energy;
        this.yaw = (float) Math.toDegrees(Math.atan2(this.dirZ, this.dirX));
        this.pitch = pulse * (3.8f + this.energy * 4.0f);
        this.scale = this.baseScale + crest * 0.1f;
        this.length = this.baseLength + crest * 0.2f;
        this.alpha = Math.min(0.84f, this.alpha + 0.06f);
        syncBoxToCurrentPosition();

        int sprayInterval = Math.max(9, 17 - Math.round(this.energy * 3f));
        if (crest > 0.92f && this.age % sprayInterval == 0) {
            sprayStandingCrest(crest);
        }
    }

    private void sprayStandingCrest(float crest) {
        double splashX = this.x + this.dirX * (this.length * 0.85f);
        double splashZ = this.z + this.dirZ * (this.length * 0.85f);
        float sprayIntensity = 0.12f + crest * 0.08f + this.energy * 0.05f;

        this.level.addParticle(
                            WavifyParticles.SPLASH_PARTICLE.get(),
                splashX + this.level.getRandom().nextGaussian() * 0.08f,
                this.y,
                splashZ + this.level.getRandom().nextGaussian() * 0.08f,
                this.level.getRandom().nextGaussian() * 0.025f,
                Math.abs(this.level.getRandom().nextGaussian()) * 0.05f + 0.04f,
                this.level.getRandom().nextGaussian() * 0.025f
        );
        this.level.addParticle(
                new SprayParticleEffect(this.yaw - 180f, sprayIntensity, Math.max(1.3f, this.scale * 0.7f)),
                splashX,
                this.y - 0.03f,
                splashZ,
                -this.dirX * 0.02f,
                0,
                -this.dirZ * 0.02f
        );
    }

    private float normalizedColumn(int columnIndex, int columnCount) {
        if (columnCount <= 1) return 0f;
        float center = (columnCount - 1) * 0.5f;
        return (columnIndex - center) / center;
    }

    @Override
    protected boolean canFullMoonGlow() {
        return false;
    }
}
