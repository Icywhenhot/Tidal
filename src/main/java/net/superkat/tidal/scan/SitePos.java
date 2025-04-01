package net.superkat.tidal.scan;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.math.BlockPos;

/**
 * Holds a BlockPos as the main position, along with a list of all x/z coordinates of BlockPos' which were calculated to have this SitePos as the closest.<br>
 * The yaw is the direction from the center of all those blocks towards the SitePos' main position.
 */
public class SitePos {
    public BlockPos pos;
    public int centerX = 0;
    public int centerZ = 0;
    public float yaw = 0f;
    public boolean yawCalculated = false;

    // cache x's and z's - stored as ints for speed
    public IntArrayList xList = new IntArrayList();
    public IntArrayList zList = new IntArrayList();

    public SitePos(BlockPos pos) {
        this.pos = pos;
    }

    public void addPos(BlockPos pos) {
        this.xList.add(pos.getX());
        this.zList.add(pos.getZ());
    }

    public void removePos(BlockPos pos) {
        int xIndex = this.xList.indexOf(pos.getX());
        this.xList.removeInt(xIndex);

        int zIndex = this.zList.indexOf(pos.getZ());
        this.zList.removeInt(zIndex);
    }

    public void clearPositions() {
        this.xList.clear();
        this.zList.clear();
        this.yawCalculated = false;
    }

    public void updateCenter() {
        if(xList.isEmpty() || zList.isEmpty()) return;
        int xSize = this.xList.size();
        this.centerX = this.xList.intStream().sum() / xSize;

        int zSize = this.zList.size();
        this.centerZ = this.zList.intStream().sum() / zSize;

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

    /**
     * @return The yaw of this site's yaw, formatted the same way as the F3 debug screen's yaw(-180 through 180 degrees)
     */
    public float getYawAsF3Angle() {
        float angle = this.getYaw() - 90;
        if(angle < 0) angle += 360;
        if(angle > 180) angle -= 360;
        return angle;
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
