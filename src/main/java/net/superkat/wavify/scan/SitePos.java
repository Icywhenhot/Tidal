package net.superkat.wavify.scan;

import net.minecraft.util.math.BlockPos;

public class SitePos {
    public BlockPos pos;
    public int centerX = 0;
    public int centerZ = 0;
    public float yaw = 0f;
    public boolean yawCalculated = false;

    public byte shoreClass = 0;

    private long sumX = 0L;
    private long sumZ = 0L;
    private int posCount = 0;

    public SitePos(BlockPos pos) {
        this.pos = pos;
    }

    public void addPos(BlockPos pos) {
        this.sumX += pos.getX();
        this.sumZ += pos.getZ();
        this.posCount++;
    }

    public int posCount() {
        return this.posCount;
    }

    public void clearPositions() {
        this.sumX = 0L;
        this.sumZ = 0L;
        this.posCount = 0;
        this.yawCalculated = false;
        this.shoreClass = 0;
    }

    public void updateCenter() {
        if (this.posCount == 0) return;
        this.centerX = (int) (this.sumX / this.posCount);
        this.centerZ = (int) (this.sumZ / this.posCount);

        updateYaw();
    }

    public void updateYaw() {
        this.yawCalculated = true;
        this.yaw = (float) Math.toDegrees(Math.atan2(pos.getZ() - centerZ, pos.getX() - centerX));
        this.yaw = Math.round(this.yaw / 15f) * 15f;
    }

    public float getYaw() {
        return this.yaw;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getX() {
        return this.pos.getX();
    }

    public int getY() {
        return this.pos.getY();
    }

    public int getZ() {
        return this.pos.getZ();
    }
}
