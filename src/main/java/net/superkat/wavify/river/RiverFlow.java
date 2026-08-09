package net.superkat.wavify.river;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.joml.Vector3f;

// stateless helpers for river waves, the actual direction comes from RiverFlowField now
// a purely local tangent per point couldn't work out the channel direction on wide rivers
// and you ended up with chaotic waves smashing into each other
public final class RiverFlow {
    private RiverFlow() {
    }

    // unit direction on the xz plane
    public record Flow(double dirX, double dirZ) {
    }

    // debug marker
    public record DebugMarker(double x, double y, double z, Vector3f color, float scale, boolean arrow, float yaw, float speed, int lifetime) {
    }

    // is this river water, checks neighbours too so the banks count
    public static boolean isRiverWater(ClientWorld world, BlockPos pos) {
        if (world.getBiome(pos).isIn(BiomeTags.IS_RIVER)) return true;
        for (BlockPos check : BlockPos.iterate(pos.add(-1, 0, -1), pos.add(1, 0, 1))) {
            if (world.getBiome(check).isIn(BiomeTags.IS_RIVER)) return true;
        }
        return false;
    }

    // walks sideways from the flow until both sides hit a bank, gives the water span in blocks
    public static int channelWidth(ClientWorld world, double x, double z, int y, double dirX, double dirZ, int max) {
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
        return left + right + 1;
    }

    // same walk but returns whichever side is closer, keeps waves from spawning right up against a bank
    public static int bankClearance(ClientWorld world, double x, double z, int y, double dirX, double dirZ, int max) {
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
        return Math.min(left, right);
    }

    // how deep the water goes from the top down, capped
    public static int depthAt(ClientWorld world, BlockPos waterTop, int max) {
        int depth = 0;
        BlockPos.Mutable cursor = waterTop.mutableCopy();
        while (depth < max && world.getFluidState(cursor).isIn(FluidTags.WATER)) {
            depth++;
            cursor.move(0, -1, 0);
        }
        return depth;
    }

    // y of the open water surface near a spot, searches a small band so waves follow gentle slopes
    // returns the water block with air above it, or Integer.MIN_VALUE if there isn't one
    public static int surfaceWaterY(ClientWorld world, double x, double z, int preferredY) {
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = preferredY + 1; y >= preferredY - 2; y--) {
            cursor.set(bx, y, bz);
            if (!WavifyWaveHandler.posIsWater(world, cursor)) continue;
            if (!world.getBlockState(cursor.up()).isAir()) continue;
            return y;
        }
        return Integer.MIN_VALUE;
    }

    // ray directions for the ocean test, 4 cardinals and 4 diagonals
    private static final int[] RAY_DX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] RAY_DZ = {0, 1, 1, 1, 0, -1, -1, -1};

    private static final int RAY_RIVER = 0; // ran out of range still in river water, tells us nothing
    private static final int RAY_OCEAN = 1; // found ocean biome water
    private static final int RAY_LAND = 2;  // hit land or non ocean water, so a real bank

    // a real river has at least one land bank, an ocean stripe is fenced in by ocean on every side
    // so fire 8 rays, if one finds ocean and none find land it's embedded
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
            int y = surfaceWaterY(world, x, z, preferredY); // helper up above
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
