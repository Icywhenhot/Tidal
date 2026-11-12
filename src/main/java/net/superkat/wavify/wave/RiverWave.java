package net.superkat.wavify.wave;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.river.RiverFlow;
import net.superkat.wavify.river.RiverFlowField;

import java.util.List;
import net.minecraft.util.math.random.Random;
import net.superkat.wavify.WavifyParticles;

public class RiverWave extends Wave {

    private static final float BODY_HEIGHT = 1.15f;

    private static final double STEER_BLEND = 0.1;

    protected final RiverFlowField field;
    protected final RiverStyle style;
    protected double dirX;
    protected double dirZ;
    protected final float travelSpeed;
    protected final double distanceBudget;
    protected double distanceTraveled = 0.0;
    protected float fadeShrink = 0f;
    protected int waterY;
    protected boolean fading = false;
    protected float coastSpeed;

    protected final float fadeStart;
    protected final double wobbleAmp;
    protected final double wobbleFreq;
    protected final double wobblePhase;
    protected final double curl;

    protected final float baseScale;
    protected final float baseLength;
    protected final float maxAlpha;
    protected final float motionPhase;
    protected final float curvatureFactor;
    protected final float lateralFactor;

    public RiverWave(ClientWorld world, BlockPos spawnWater, RiverFlowField field, RiverStyle style, double dirX, double dirZ, float travelBlocks, float storm) {
        super(world, spawnWater.up(), (float) Math.toDegrees(Math.atan2(dirZ, dirX)), 0.4f, false);
        this.field = field;
        this.style = style;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.waterY = spawnWater.getY();

        int rawWidth = RiverFlow.banksAt(world, this.x, this.z, this.waterY, dirX, dirZ, 5).width();
        int width = MathHelper.clamp(rawWidth, 3, 9);
        if ((width & 1) == 0) width = Math.max(3, width - 1);
        this.setWidth(width);

        int depth = RiverFlow.depthAt(world, spawnWater, 4);
        this.travelSpeed = (float) MathHelper.clamp((0.072 + depth * 0.012) * 1.2, 0.090, 0.150) * style.speed * (1f + 0.3f * storm);

        float sizeJitter = 0.82f + world.getRandom().nextFloat() * 0.36f;
        this.scale = Math.max(1.7f, (1.55f + this.width * 0.24f) * sizeJitter);
        this.length = Math.max(1.05f, (1.0f + this.width * 0.11f) * sizeJitter);
        this.baseScale = this.scale;
        this.baseLength = this.length;
        this.maxAlpha = 0.78f * Math.min(1f, 0.5f + style.foam * 0.5f);
        this.motionPhase = world.getRandom().nextFloat() * 24f;

        this.curvatureFactor = 0.65f + world.getRandom().nextFloat() * 0.95f;
        this.lateralFactor = 0.85f + world.getRandom().nextFloat() * 0.35f;

        Random rand = world.getRandom();
        double budgetJitter = 0.7 + rand.nextDouble() * 0.6;
        this.distanceBudget = Math.max(4.0, travelBlocks * style.travel * budgetJitter);
        this.maxAge = (int) (this.distanceBudget / this.travelSpeed) + 80;
        this.coastSpeed = this.travelSpeed;

        this.fadeStart = 0.45f + rand.nextFloat() * 0.25f;
        this.wobbleAmp = Math.toRadians(7 + rand.nextDouble() * 10);
        this.wobbleFreq = Math.PI * 2 / (7 + rand.nextDouble() * 7);
        this.wobblePhase = rand.nextDouble() * Math.PI * 2;
        this.curl = Math.toRadians((rand.nextDouble() - 0.5) * 8);
        this.maxWaterAge = this.maxAge;
        this.bigWave = false;

        this.y = this.waterY + BODY_HEIGHT;
        this.prevY = this.y;
        syncBoxToCurrentPosition();
    }

    @Override
    public float getRenderYSink() {
        return (float) WavifyConfig.riverWaveYOffset;
    }

    @Override
    public int getRenderColumnCount() {
        int columns = MathHelper.clamp(Math.round(this.width) + 2, this.style.minColumns, this.style.maxColumns);
        if ((columns & 1) == 0) columns = columns < this.style.maxColumns ? columns + 1 : columns - 1;
        return columns;
    }

    @Override
    public float getRenderColumnLateralOffset(int columnIndex, int columnCount) {
        return normalizedColumn(columnIndex, columnCount) * (0.68f + this.width * 0.05f) * this.lateralFactor * this.style.spread;
    }

    @Override
    public float getRenderColumnForwardOffset(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        float off = -(0.16f + normalized * normalized * 0.54f) * this.curvatureFactor * this.style.curve;
        if (this.style == RiverStyle.SWELL) off += MathHelper.sin(this.age * 0.05f + columnIndex * 0.9f) * 0.15f;
        return off;
    }

    @Override
    public float getRenderColumnVerticalOffset(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        return (1f - normalized) * this.style.lift;
    }

    @Override
    public float getRenderColumnWidth(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        return MathHelper.lerp(normalized, this.style.widthIn, this.style.widthOut);
    }

    @Override
    public float getRenderColumnLength(int columnIndex, int columnCount) {
        float normalized = Math.abs(normalizedColumn(columnIndex, columnCount));
        return this.length * MathHelper.lerp(normalized, this.style.lengthIn, this.style.lengthOut);
    }

    @Override
    public float getFoamAlpha() {
        return this.style.foam;
    }

    @Override
    public void tick() {
        if (this.age++ >= this.maxAge || this.hitBlock && this.age - this.hitBlockAge >= 2) {
            this.markDead();
            return;
        }

        capturePreviousState();

        if (this.fading) {
            coast();
        } else {
            updateWaterColor();
            int surfaceY = RiverFlow.surfaceWaterY(this.world, this.x, this.z, this.waterY);
            if (surfaceY == Integer.MIN_VALUE) {
                this.fading = true;
            } else {
                if (this.waterY != surfaceY) {
                    this.waterY = surfaceY;
                    this.y = this.waterY + BODY_HEIGHT;
                    syncBoxToCurrentPosition();
                }
                steerAndAdvance();
                if (!this.fading && this.world.getRandom().nextFloat() < this.style.sprayChance) splash();
            }
        }

        updateShapeAndOpacity();
        this.y = this.waterY + BODY_HEIGHT;
        this.yaw = (float) Math.toDegrees(Math.atan2(this.dirZ, this.dirX));
        syncBoxToCurrentPosition();

        if (this.alpha <= 0.01f && (this.fading || this.distanceTraveled >= this.distanceBudget)) {
            this.markDead();
        }
    }

    protected void steerAndAdvance() {
        RiverFlow.Flow current = RiverFlow.dynamicFlowAt(this.world, this.x, this.waterY, this.z);
        RiverFlow.Flow flow = current != null ? current : this.field.flowAt(this.x, this.z);
        if (flow != null) {
            double tx = flow.dirX();
            double tz = flow.dirZ();
            double turn = this.curl + this.wobbleAmp * Math.sin(this.distanceTraveled * this.wobbleFreq + this.wobblePhase);
            double cos = Math.cos(turn);
            double sin = Math.sin(turn);

            if (tx * this.dirX + tz * this.dirZ < 0.0) {
                if (current != null) {
                    this.fading = true;
                    return;
                }
                tx = -tx;
                tz = -tz;
            }
            double rx = tx * cos - tz * sin;
            tz = tx * sin + tz * cos;
            tx = rx;
            double nx = this.dirX + (tx - this.dirX) * STEER_BLEND;
            double nz = this.dirZ + (tz - this.dirZ) * STEER_BLEND;
            double len = Math.sqrt(nx * nx + nz * nz);
            if (len > 1e-6) {
                this.dirX = nx / len;
                this.dirZ = nz / len;
            }
        }

        this.yaw = (float) Math.toDegrees(Math.atan2(this.dirZ, this.dirX));
        this.velX = (float) (this.dirX * this.travelSpeed);
        this.velY = 0f;
        this.velZ = (float) (this.dirZ * this.travelSpeed);

        if (!waterAt(this.x + this.dirX * 0.8, this.z + this.dirZ * 0.8)) {
            this.fading = true;
            return;
        }
        this.move(this.velX, this.velY, this.velZ);
        if (this.hitBlock || !waterAt(this.x, this.z)) {
            this.fading = true;
            return;
        }
        this.distanceTraveled += this.travelSpeed;
    }

    private void coast() {
        this.coastSpeed *= 0.9f;
        if (this.coastSpeed < 0.005f) return;
        double nx = this.x + this.dirX * this.coastSpeed;
        double nz = this.z + this.dirZ * this.coastSpeed;
        if (!waterAt(nx, nz)) {
            this.coastSpeed = 0f;
            return;
        }
        this.velX = (float) (this.dirX * this.coastSpeed);
        this.velZ = (float) (this.dirZ * this.coastSpeed);
        this.move(this.velX, 0f, this.velZ);
    }

    @Override
    public Box getHitBox() {
        return this.box.expand(0.5, 0, 0.5);
    }

    @Override
    protected Vec3d sprayPosition() {
        double sprayY = this.waterY + 1.8;
        Box bounds = new Box(this.x - 0.1, sprayY - 0.2, this.z - 0.1,
                this.x + 0.1, sprayY + 0.3, this.z + 0.1);
        Vec3d offset = Entity.adjustMovementForCollisions(null, new Vec3d(this.velX * 10, 0, this.velZ * 10),
                bounds, this.world, List.of());
        return new Vec3d(this.x + offset.x, sprayY, this.z + offset.z);
    }

    @Override
    protected int sprayMaxAge() {
        return SMALL_WAVE_MAX_AGE;
    }

    private void splash() {
        Random rand = this.world.getRandom();
        this.world.addParticleClient(WavifyParticles.SPLASH_PARTICLE,
                this.x + rand.nextGaussian() * 0.4, this.waterY + 1.05, this.z + rand.nextGaussian() * 0.4,
                this.dirX * 0.05 + rand.nextGaussian() * 0.03, 0.12 + rand.nextFloat() * 0.08, this.dirZ * 0.05 + rand.nextGaussian() * 0.03);
    }

    private boolean waterAt(double wx, double wz) {
        return RiverFlow.surfaceWaterY(this.world, wx, wz, this.waterY) != Integer.MIN_VALUE;
    }

    protected void updateShapeAndOpacity() {
        float pulse = MathHelper.sin((this.age + this.motionPhase) * 0.18f);
        float crest = Math.abs(pulse);
        this.pitch = pulse * 2.4f * this.style.pulse;
        this.scale = this.baseScale + crest * 0.05f * this.style.pulse;

        if (this.fading) {
            this.alpha = Math.max(0f, this.alpha - 0.035f);
            this.fadeShrink = Math.min(this.baseLength * 0.7f, this.fadeShrink + 0.015f);
        } else {
            float p = (float) (this.distanceTraveled / this.distanceBudget);
            float out = 1f;
            if (p > this.fadeStart) {
                float k = MathHelper.clamp(1f - (p - this.fadeStart) / (1f - this.fadeStart), 0f, 1f);
                out = k * k * (3f - 2f * k);
            }
            this.alpha = Math.min(this.alpha + 0.03f, this.maxAlpha * out);
            this.fadeShrink = Math.max(this.fadeShrink, this.baseLength * 0.6f * (1f - out));
        }

        this.length = Math.max(0f, this.baseLength + crest * 0.08f * this.style.pulse - this.fadeShrink);
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
