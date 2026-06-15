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
}
