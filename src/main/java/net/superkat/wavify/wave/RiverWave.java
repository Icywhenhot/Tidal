package net.superkat.wavify.wave;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.superkat.wavify.river.RiverFlow;
import net.superkat.wavify.river.RiverFlowField;

// follows the cached flow field down the channel then fades out after a set distance
// unlike ocean waves this never washes up, it overrides tick() and skips updateWashingUp entirely
// it runs along the channel parallel to the banks
// a lookahead check fades it before it can actually reach a bank
// and because the flow field is coherent, neighbouring waves never point at each other
public class RiverWave extends Wave {
    // sits about 0.15 above the water, matches how river waves used to look
    private static final float BODY_HEIGHT = 1.15f;

    // how hard we steer toward the flow field each tick
    private static final double STEER_BLEND = 0.18;

    protected final RiverFlowField field;
    protected double dirX;
    protected double dirZ;
    protected final float travelSpeed;
    protected final double distanceBudget;
    protected double distanceTraveled = 0.0;
    protected int waterY;
    protected boolean fading = false;

    protected final float baseScale;
    protected final float baseLength;
    protected final float maxAlpha;
    protected final float motionPhase;
    protected final float curvatureFactor;
    protected final float lateralFactor;

    public RiverWave(ClientWorld world, BlockPos spawnWater, RiverFlowField field, double dirX, double dirZ, float travelBlocks) {
        super(world, spawnWater.up(), (float) Math.toDegrees(Math.atan2(dirZ, dirX)), 0.4f, false);
        this.field = field;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.waterY = spawnWater.getY();

        int rawWidth = RiverFlow.channelWidth(world, this.x, this.z, this.waterY, dirX, dirZ, 5);
        int width = MathHelper.clamp(rawWidth, 3, 9);
        if ((width & 1) == 0) width = Math.max(3, width - 1);
        this.setWidth(width);

        int depth = RiverFlow.depthAt(world, spawnWater, 4);
        this.travelSpeed = (float) MathHelper.clamp((0.072 + depth * 0.012) * 1.2, 0.090, 0.150);

        float sizeJitter = 0.82f + world.getRandom().nextFloat() * 0.36f;
        this.scale = Math.max(1.7f, (1.55f + this.width * 0.24f) * sizeJitter);
        this.length = Math.max(1.05f, (1.0f + this.width * 0.11f) * sizeJitter);
        this.baseScale = this.scale;
        this.baseLength = this.length;
        this.maxAlpha = 0.78f;
        this.motionPhase = world.getRandom().nextFloat() * 24f;
        // always above 0 so nothing comes out dead straight, the top end gives deep crescents
        this.curvatureFactor = 0.65f + world.getRandom().nextFloat() * 0.95f;
        this.lateralFactor = 0.85f + world.getRandom().nextFloat() * 0.35f;

        double budgetJitter = 0.85 + world.getRandom().nextDouble() * 0.30;
        this.distanceBudget = Math.max(4.0, travelBlocks * budgetJitter);
        this.maxAge = (int) (this.distanceBudget / this.travelSpeed) + 40;
        this.maxWaterAge = this.maxAge;
        this.bigWave = false;

        this.y = this.waterY + BODY_HEIGHT;
        this.prevY = this.y;
        syncBoxToCurrentPosition();
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
        return MathHelper.lerp(normalized, 0.62f, 1.0f);
    }

    @Override
    public float getRenderColumnLength(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        return this.length * MathHelper.lerp(normalized, 0.64f, 1.12f);
    }

    @Override
    public void tick() {
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        capturePreviousState();
        updateWaterColor();

        int surfaceY = RiverFlow.surfaceWaterY(this.world, this.x, this.z, this.waterY);
        if (surfaceY == Integer.MIN_VALUE) {
            this.fading = true;
        } else {
            this.waterY = surfaceY;
        }

        if (!this.fading) {
            steerAndAdvance();
        }

        updateShapeAndOpacity();
        this.y = this.waterY + BODY_HEIGHT;
        this.yaw = (float) Math.toDegrees(Math.atan2(this.dirZ, this.dirX));
        syncBoxToCurrentPosition();

        if (this.fading && this.alpha <= 0.02f) {
            this.markDead();
        }
    }

    protected void steerAndAdvance() {
        // ease toward the flow field so the wave curves down the channel
        RiverFlow.Flow flow = this.field.flowAt(this.x, this.z);
        if (flow != null) {
            double tx = flow.dirX();
            double tz = flow.dirZ();
            // keep going forward, an orientation flip should never reverse a wave mid channel
            if (tx * this.dirX + tz * this.dirZ < 0.0) {
                tx = -tx;
                tz = -tz;
            }
            double nx = this.dirX + (tx - this.dirX) * STEER_BLEND;
            double nz = this.dirZ + (tz - this.dirZ) * STEER_BLEND;
            double len = Math.sqrt(nx * nx + nz * nz);
            if (len > 1e-6) {
                this.dirX = nx / len;
                this.dirZ = nz / len;
            }
        }

        // move along the heading, if the centerpoint would land on a block instead of water just fade
        // trying to dodge the bank instead is what caused the edge jitter
        double nextX = this.x + this.dirX * this.travelSpeed;
        double nextZ = this.z + this.dirZ * this.travelSpeed;
        if (!waterAt(nextX, nextZ)) {
            this.fading = true;
            return;
        }

        this.velX = (float) (this.dirX * this.travelSpeed);
        this.velY = 0f;
        this.velZ = (float) (this.dirZ * this.travelSpeed);
        this.x += this.velX;
        this.z += this.velZ;
        this.distanceTraveled += this.travelSpeed;
        if (this.distanceTraveled >= this.distanceBudget) {
            this.fading = true;
        }
    }

    // is the column at this spot open surface water rather than a block
    private boolean waterAt(double wx, double wz) {
        return RiverFlow.surfaceWaterY(this.world, wx, wz, this.waterY) != Integer.MIN_VALUE;
    }

    protected void updateShapeAndOpacity() {
        float pulse = MathHelper.sin((this.age + this.motionPhase) * 0.18f);
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

    protected float normalizedColumn(int columnIndex, int columnCount) {
        if (columnCount <= 1) return 0f;
        float center = (columnCount - 1) * 0.5f;
        return (columnIndex - center) / center;
    }

    @Override
    protected boolean canFullMoonGlow() {
        return false;
    }
}
