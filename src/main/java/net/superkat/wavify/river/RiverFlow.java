package net.superkat.wavify.river;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
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
    public static Flow dynamicFlowAt(World world, double x, int y, double z) {
        if (!DynamicWatersCompat.isLoaded()) return null;
        BlockPos pos = new BlockPos(MathHelper.floor(x), y, MathHelper.floor(z));
        FluidState fluid = world.getFluidState(pos);
        if (fluid.isEmpty()) return null;
        Vec3d flow = fluid.getVelocity(world, pos);
        double len = Math.sqrt(flow.x * flow.x + flow.z * flow.z);
        if (len < 1.0e-6) return null;
        return new Flow(flow.x / len, flow.z / len);
    }

    public static boolean isRiverWater(ClientWorld world, BlockPos pos) {
        if (world.getBiome(pos).isIn(BiomeTags.IS_RIVER)) return true;
        for (BlockPos check : BlockPos.iterate(pos.add(-1, 0, -1), pos.add(1, 0, 1))) {
            if (world.getBiome(check).isIn(BiomeTags.IS_RIVER)) return true;
        }
        return false;
    }

    public static Banks banksAt(ClientWorld world, double x, double z, int y, double dirX, double dirZ, int max) {
        double nx = -dirZ;
        double nz = dirX;
        int left = 0;
        for (int s = 1; s <= max; s++) {
            if (surfaceWaterY(world, x + nx * s, z + nz * s, y) == Integer.MIN_VALUE) break;
            left++;
        }
        int right = 0;
        for (int s = 1; s <= max; s++) {
            if (surfaceWaterY(world, x - nx * s, z - nz * s, y) == Integer.MIN_VALUE) break;
            right++;
        }
        return new Banks(left, right);
    }

    public static int depthAt(ClientWorld world, BlockPos waterTop, int max) {
        int depth = 0;
        BlockPos.Mutable cursor = waterTop.mutableCopy();
        while (depth < max && world.getFluidState(cursor).isIn(FluidTags.WATER)) {
            depth++;
            cursor.move(0, -1, 0);
        }
        return depth;
    }

    public static boolean isSurfaceWater(ClientWorld world, BlockPos pos) {
        if (!WavifyWaveHandler.posIsWater(world, pos)) return false;
        return world.getBlockState(pos.up()).isAir();
    }

    public static int surfaceWaterY(ClientWorld world, double x, double z, int preferredY) {
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = preferredY + 1; y >= preferredY - 2; y--) {
            cursor.set(bx, y, bz);
            if (isSurfaceWater(world, cursor)) return y;
        }
        return Integer.MIN_VALUE;
    }

    private static final int[] RAY_DX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] RAY_DZ = {0, 1, 1, 1, 0, -1, -1, -1};

    private static final int RAY_RIVER = 0;
    private static final int RAY_OCEAN = 1;
    private static final int RAY_LAND = 2;

    public static boolean isOceanSurrounded(ClientWorld world, BlockPos pos, int maxRay) {
        boolean hasOcean = false;
        for (int d = 0; d < 8; d++) {
            int outcome = castRay(world, pos, RAY_DX[d], RAY_DZ[d], maxRay);
            if (outcome == RAY_LAND) return false;
            if (outcome == RAY_OCEAN) hasOcean = true;
        }
        return hasOcean;
    }

    private static int castRay(ClientWorld world, BlockPos origin, int dx, int dz, int maxRay) {
        int preferredY = origin.getY();
        BlockPos.Mutable sample = new BlockPos.Mutable();
        for (int s = 1; s <= maxRay; s++) {
            double x = origin.getX() + 0.5 + (double) dx * s;
            double z = origin.getZ() + 0.5 + (double) dz * s;
            int y = surfaceWaterY(world, x, z, preferredY);
            if (y == Integer.MIN_VALUE) return RAY_LAND;
            preferredY = y;
            sample.set(MathHelper.floor(x), y, MathHelper.floor(z));
            var biome = world.getBiome(sample);
            if (biome.isIn(BiomeTags.IS_OCEAN)) return RAY_OCEAN;
            if (!biome.isIn(BiomeTags.IS_RIVER)) return RAY_LAND;
        }
        return RAY_RIVER;
    }
}
