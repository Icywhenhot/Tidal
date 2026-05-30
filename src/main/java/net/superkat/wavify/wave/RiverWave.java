package net.superkat.wavify.wave;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.superkat.wavify.river.RiverFlowPlanner;

import java.util.List;

/**
 * Traveling river wave that follows a precomputed path instead of deciding direction at runtime.
 */
public class RiverWave extends Wave {
    protected static final float RIVER_VERTICAL_OFFSET = -0.4f;

    protected final long reachId;
    protected final List<RiverFlowPlanner.RiverPlanPoint> plan;
    protected final float travelSpeed;
    protected final int requiredBankDistance;
    protected final float baseScale;
    protected final float baseLength;
    protected final float maxAlpha;
    protected final float motionPhase;
    protected final float curvatureFactor;
    protected final float lateralFactor;

    protected int planIndex = 0;
    protected boolean fading = false;

    public RiverWave(ClientLevel world, RiverFlowPlanner.RiverTravelPlan travelPlan) {
        super(world, travelPlan.spawnSurface(), travelPlan.points().get(0).yaw(), 0.4f, false);
        this.reachId = travelPlan.reachId();
        this.plan = List.copyOf(travelPlan.points());
        this.travelSpeed = travelPlan.speed();
        this.requiredBankDistance = travelPlan.requiredBankDistance();
        this.setWidth(travelPlan.width());
        float sizeJitter = 0.82f + this.level.getRandom().nextFloat() * 0.36f;
        this.scale = Math.max(1.7f, (1.55f + this.width * 0.24f) * sizeJitter);
        this.length = Math.max(1.05f, (1.0f + this.width * 0.11f) * sizeJitter);
        this.baseScale = this.scale;
        this.baseLength = this.length;
        this.maxAlpha = 0.78f;
        this.motionPhase = this.level.getRandom().nextFloat() * 24f;
        // Always > 0 so no wave is a straight line; upper end produces deep crescents.
        this.curvatureFactor = 0.65f + this.level.getRandom().nextFloat() * 0.95f;
        this.lateralFactor = 0.85f + this.level.getRandom().nextFloat() * 0.35f;
        this.maxAge = 105 + this.plan.size() * 5;
        this.maxWaterAge = this.maxAge;
        this.bigWave = false;
        this.offsetVertical(RIVER_VERTICAL_OFFSET);
        syncBoxToCurrentPosition();
    }

    public long getReachId() {
        return this.reachId;
    }

    public double getFlowDirX() {
        return Math.cos(Math.toRadians(this.yaw));
    }

    public double getFlowDirZ() {
        return Math.sin(Math.toRadians(this.yaw));
    }

    @Override
    public int getRenderColumnCount() {
        int columns = Math.max(5, Math.round(this.width) + 2);
        if ((columns & 1) == 0) columns++;
        return Math.min(columns, 11);
    }

    @Override
    public float getRenderColumnLateralOffset(int columnIndex, int columnCount) {
        return normalizedColumn(columnIndex, columnCount) * (0.68f + this.width * 0.05f) * this.lateralFactor;
    }

    @Override
    public float getRenderColumnForwardOffset(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        return -(0.16f + normalized * normalized * 0.54f) * this.curvatureFactor;
    }

    @Override
    public float getRenderColumnVerticalOffset(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        return (1f - normalized) * 0.055f;
    }

    @Override
    public float getRenderColumnWidth(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        return Mth.lerp(normalized, 0.62f, 1.0f);
    }

    @Override
    public float getRenderColumnLength(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        return this.length * Mth.lerp(normalized, 0.64f, 1.12f);
    }

    @Override
    public void tick() {
        if (this.age++ >= this.maxAge || this.plan.isEmpty()) {
            this.markDead();
            return;
        }

        RiverFlowPlanner.RiverPlanPoint currentPoint = this.plan.get(Mth.clamp(this.planIndex, 0, this.plan.size() - 1));
        RiverFlowPlanner.RiverPlanPoint nextPoint = this.plan.get(Mth.clamp(this.planIndex + 1, 0, this.plan.size() - 1));

        if (!isPlanPointSupported(currentPoint)) {
            this.fading = true;
        }

        capturePreviousState();
        updateWaterColor();
        updateTravelMotion(currentPoint, nextPoint);
        updateShapeAndOpacity();
        RiverFlowPlanner.RiverPlanPoint activePoint = this.plan.get(Mth.clamp(this.planIndex, 0, this.plan.size() - 1));
        updateVerticalPlacement(activePoint.water());
        syncBoxToCurrentPosition();

        if ((this.fading || this.planIndex >= this.plan.size() - 1) && this.alpha <= 0.02f) {
            this.markDead();
        }
    }

    protected void updateTravelMotion(RiverFlowPlanner.RiverPlanPoint currentPoint, RiverFlowPlanner.RiverPlanPoint nextPoint) {
        if (this.planIndex < this.plan.size() - 1 && hasPassedTarget(currentPoint, nextPoint)) {
            this.planIndex++;
            currentPoint = this.plan.get(Mth.clamp(this.planIndex, 0, this.plan.size() - 1));
            nextPoint = this.plan.get(Mth.clamp(this.planIndex + 1, 0, this.plan.size() - 1));
        }

        double targetX = nextPoint.x();
        double targetZ = nextPoint.z();
        double deltaX = targetX - this.x;
        double deltaZ = targetZ - this.z;
        double lengthSq = deltaX * deltaX + deltaZ * deltaZ;

        if (lengthSq < 0.0001) {
            if (this.planIndex >= this.plan.size() - 1) {
                this.fading = true;
            }
            this.velX = 0f;
            this.velY = 0f;
            this.velZ = 0f;
            return;
        }

        double length = Math.sqrt(lengthSq);
        double dirX = deltaX / length;
        double dirZ = deltaZ / length;
        this.velX = Mth.lerp(0.42f, this.velX, (float) (dirX * this.travelSpeed));
        this.velZ = Mth.lerp(0.42f, this.velZ, (float) (dirZ * this.travelSpeed));
        this.velY = 0f;
        this.x += this.velX;
        this.z += this.velZ;
        this.yaw = Mth.rotLerp(0.35f, this.yaw, currentPoint.yaw());

        if (this.planIndex >= this.plan.size() - 1 && length < 0.95) {
            this.fading = true;
        }
    }

    protected void updateShapeAndOpacity() {
        float pulse = Mth.sin((this.age + this.motionPhase) * 0.18f);
        float crest = Math.abs(pulse);
        this.pitch = pulse * 2.4f;
        this.scale = this.baseScale + crest * 0.05f;
        this.length = this.baseLength + crest * 0.08f;

        if (this.fading) {
            this.alpha = Math.max(0f, this.alpha - 0.05f);
            this.length = Math.max(0f, this.length - 0.02f);
        } else {
            this.alpha = Math.min(this.maxAlpha, this.alpha + 0.06f);
        }
    }

    protected void updateVerticalPlacement(BlockPos water) {
        this.y = water.getY() + 1.15f;
    }

    protected boolean isPlanPointSupported(RiverFlowPlanner.RiverPlanPoint point) {
        if (point.bankDistance() < this.requiredBankDistance) return false;
        if (!WavifyWaveHandler.posIsWater(this.level, point.water())) return false;
        return this.level.getBlockState(point.water().above()).isAir();
    }

    protected boolean hasPassedTarget(RiverFlowPlanner.RiverPlanPoint currentPoint, RiverFlowPlanner.RiverPlanPoint nextPoint) {
        double segmentX = nextPoint.x() - currentPoint.x();
        double segmentZ = nextPoint.z() - currentPoint.z();
        double lengthSq = segmentX * segmentX + segmentZ * segmentZ;
        if (lengthSq < 0.0001) return true;

        double offsetX = this.x - currentPoint.x();
        double offsetZ = this.z - currentPoint.z();
        double delta = (offsetX * segmentX + offsetZ * segmentZ) / lengthSq;
        double toTargetX = this.x - nextPoint.x();
        double toTargetZ = this.z - nextPoint.z();
        return delta >= 0.92 || toTargetX * toTargetX + toTargetZ * toTargetZ <= Mth.square(0.8);
    }

    protected float normalizedColumn(int columnIndex, int columnCount) {
        if (columnCount <= 1) return 0f;
        float center = (columnCount - 1) * 0.5f;
        return (columnIndex - center) / center;
    }
}
