package net.superkat.wavify.river;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.superkat.wavify.compat.DynamicWatersCompat;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.jetbrains.annotations.Nullable;

public final class RiverFlow {
    private RiverFlow() {
    }

    public record Flow(double dirX, double dirZ) {
    }

    public record Banks(int left, int right) {
        public int width() {
            return this.left + this.right + 1;
        }

        public int clearance() {
            return Math.min(this.left, this.right);
        }
    }

    @Nullable
    public static Flow dynamicFlowAt(Level level, double x, int y, double z) {
        if (!DynamicWatersCompat.isLoaded()) return null;
        BlockPos pos = new BlockPos(Mth.floor(x), y, Mth.floor(z));
        FluidState fluid = level.getFluidState(pos);
        if (fluid.isEmpty()) return null;
        Vec3 flow = fluid.getFlow(level, pos);
        double len = Math.sqrt(flow.x * flow.x + flow.z * flow.z);
        if (len < 1.0e-6) return null;
        return new Flow(flow.x / len, flow.z / len);
    }

    public static boolean isRiverWater(ClientLevel level, BlockPos pos) {
        if (level.getBiome(pos).is(BiomeTags.IS_RIVER)) return true;

        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                check.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
                if (level.getBiome(check).is(BiomeTags.IS_RIVER)) return true;
            }
        }
        return false;
    }

    public static Banks banksAt(ClientLevel level, double x, double z, int y, double dirX, double dirZ, int max) {
        double nx = -dirZ;
        double nz = dirX;
        int left = 0;
        for (int s = 1; s <= max; s++) {
            if (surfaceWaterY(level, x + nx * s, z + nz * s, y) == Integer.MIN_VALUE) break;
            left++;
        }
        int right = 0;
        for (int s = 1; s <= max; s++) {
            if (surfaceWaterY(level, x - nx * s, z - nz * s, y) == Integer.MIN_VALUE) break;
            right++;
        }
        return new Banks(left, right);
    }

    public static int depthAt(ClientLevel level, BlockPos waterTop, int max) {
        int depth = 0;
        BlockPos.MutableBlockPos cursor = waterTop.mutable();
        while (depth < max && level.getFluidState(cursor).is(FluidTags.WATER)) {
            depth++;
            cursor.move(0, -1, 0);
        }
        return depth;
    }

    public static boolean isSurfaceWater(ClientLevel level, BlockPos pos) {
        if (!WavifyWaveHandler.posIsWater(level, pos)) return false;
        return level.getBlockState(pos.above()).isAir();
    }

    public static int surfaceWaterY(ClientLevel level, double x, double z, int preferredY) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = preferredY + 1; y >= preferredY - 2; y--) {
            cursor.set(bx, y, bz);
            if (isSurfaceWater(level, cursor)) return y;
        }
        return Integer.MIN_VALUE;
    }

    private static final int[] RAY_DX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] RAY_DZ = {0, 1, 1, 1, 0, -1, -1, -1};

    private static final int RAY_RIVER = 0;
    private static final int RAY_OCEAN = 1;
    private static final int RAY_LAND = 2;

    public static boolean isOceanSurrounded(ClientLevel level, BlockPos pos, int maxRay) {
        boolean hasOcean = false;
        for (int d = 0; d < 8; d++) {
            int outcome = castRay(level, pos, RAY_DX[d], RAY_DZ[d], maxRay);
            if (outcome == RAY_LAND) return false;
            if (outcome == RAY_OCEAN) hasOcean = true;
        }
        return hasOcean;
    }

    private static int castRay(ClientLevel level, BlockPos origin, int dx, int dz, int maxRay) {
        int preferredY = origin.getY();
        BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
        for (int s = 1; s <= maxRay; s++) {
            double x = origin.getX() + 0.5 + (double) dx * s;
            double z = origin.getZ() + 0.5 + (double) dz * s;
            int y = surfaceWaterY(level, x, z, preferredY);
            if (y == Integer.MIN_VALUE) return RAY_LAND;
            preferredY = y;
            sample.set(Mth.floor(x), y, Mth.floor(z));
            var biome = level.getBiome(sample);
            if (biome.is(BiomeTags.IS_OCEAN)) return RAY_OCEAN;
            if (!biome.is(BiomeTags.IS_RIVER)) return RAY_LAND;
        }
        return RAY_RIVER;
    }
}
