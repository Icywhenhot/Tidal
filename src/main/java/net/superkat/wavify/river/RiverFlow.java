package net.superkat.wavify.river;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.joml.Vector3f;

/**
 * Shared, stateless helpers for the river wave system. The actual flow <i>direction</i> now comes from
 * {@link RiverFlowField} (a cached, coherent grid) - a purely local per-point tangent could not recover
 * the channel direction inside wide rivers, which produced chaotic, colliding waves.
 */
public final class RiverFlow {
    private RiverFlow() {
    }

    /** A unit flow direction in the XZ plane. */
    public record Flow(double dirX, double dirZ) {
    }

    /** Debug visual marker. */
    public record DebugMarker(double x, double y, double z, Vector3f color, float scale, boolean arrow, float yaw, float speed, int lifetime) {
    }

    /** True if a position is river water (biome IS_RIVER with a small tolerance so banks count too). */
    public static boolean isRiverWater(ClientWorld world, BlockPos pos) {
        if (world.getBiome(pos).isIn(BiomeTags.IS_RIVER)) return true;
        for (BlockPos check : BlockPos.iterate(pos.add(-1, 0, -1), pos.add(1, 0, 1))) {
            if (world.getBiome(check).isIn(BiomeTags.IS_RIVER)) return true;
        }
        return false;
    }

    /**
     * Measures the channel width by marching perpendicular to the flow until both sides hit a bank.
     *
     * @return the total water span in blocks (always >= 1).
     */
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

    /**
     * Distance to the nearest bank perpendicular to the flow, taking the closer of the two sides. Used to
     * keep waves from spawning right against a bank, where the flow direction is least reliable and the
     * predictive bank check would otherwise flail.
     *
     * @return {@code min(left run, right run)} of water blocks, capped at {@code max}.
     */
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

    /** Counts contiguous water blocks downward from {@code waterTop}, capped at {@code max}. */
    public static int depthAt(ClientWorld world, BlockPos waterTop, int max) {
        int depth = 0;
        BlockPos.Mutable cursor = waterTop.mutableCopy();
        while (depth < max && world.getFluidState(cursor).isIn(FluidTags.WATER)) {
            depth++;
            cursor.move(0, -1, 0);
        }
        return depth;
    }

    /**
     * Finds the Y of the open surface-water column nearest a position, searching a small vertical band
     * around {@code preferredY} so waves track gentle elevation changes.
     *
     * @return the water block Y with air directly above it, or {@link Integer#MIN_VALUE} if none.
     */
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

    // Ray directions for the ocean-surrounded test: 4 cardinals + 4 diagonals.
    private static final int[] RAY_DX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] RAY_DZ = {0, 1, 1, 1, 0, -1, -1, -1};

    private static final int RAY_RIVER = 0; // reached the cap still in river water (inconclusive)
    private static final int RAY_OCEAN = 1; // hit ocean-biome surface water
    private static final int RAY_LAND = 2;  // blocked by land, or by some non-ocean water (a real bank)

    /**
     * A real river always has at least one land bank; an ocean-embedded river stripe is fenced in by ocean
     * water on every side. Casts 8 rays from a river-water block: ocean-surrounded if at least one ray reaches
     * ocean water and none hits land within range.
     */
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
            int y = surfaceWaterY(world, x, z, preferredY);   // existing helper in this class
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
