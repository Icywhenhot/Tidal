package net.superkat.wavify.wave;

import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.superkat.wavify.river.RiverFlow;
import net.superkat.wavify.scan.SitePos;

import java.util.Queue;
import java.util.Set;

public final class ShoreCheck {
    private ShoreCheck() {
    }

    private static final double STEP = 0.115;

    private static final int LANDMASS_CAP = 768;

    private record Walk(boolean river, boolean ocean) {
    }

    public static boolean isOceanConnected(ClientLevel level, BlockPos spawn, float yaw) {
        Walk w = walk(level, spawn, yaw, -1, 96);
        return !w.river() && w.ocean();
    }

    public static boolean isPathSafe(ClientLevel level, BlockPos spawn, float yaw) {
        return !walk(level, spawn, yaw, 1, 80).river();
    }

    private static Walk walk(ClientLevel level, BlockPos from, float yaw, double sign, int steps) {
        double rad = Math.toRadians(yaw);
        double dirX = Math.cos(rad) * sign;
        double dirZ = Math.sin(rad) * sign;
        double x = from.getX() + 0.5;
        double z = from.getZ() + 0.5;
        int prefY = from.getY() - 1;
        boolean ocean = level.getBiome(from).is(BiomeTags.IS_OCEAN);

        for (int i = 0; i < steps; i++) {
            x += dirX * STEP;
            z += dirZ * STEP;

            BlockPos water = nearestSurfaceWater(level, x, z, prefY);
            if (water == null) return new Walk(false, ocean);
            prefY = water.getY();

            if (RiverFlow.isRiverWater(level, water)) return new Walk(true, ocean);
            if (level.getBiome(water).is(BiomeTags.IS_OCEAN)) ocean = true;
        }

        return new Walk(false, ocean);
    }

    private static BlockPos nearestSurfaceWater(ClientLevel level, double x, double z, int prefY) {
        int baseX = Mth.floor(x);
        int baseZ = Mth.floor(z);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int y = prefY + 1; y >= prefY - 2; y--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos check = new BlockPos(baseX + dx, y, baseZ + dz);
                    if (!RiverFlow.isSurfaceWater(level, check)) continue;

                    double dist = Mth.square(check.getX() + 0.5 - x) + Mth.square(check.getZ() + 0.5 - z);
                    if (dist < bestDistance) {
                        bestDistance = dist;
                        best = check;
                    }
                }
            }
            if (best != null) return best;
        }

        return null;
    }

    public static boolean isIsolatedObject(ClientLevel level, SitePos site) {
        if (site.shoreClass != 0) return site.shoreClass == 1;
        boolean small = isSmallObject(level, site);
        site.shoreClass = (byte) (small ? 1 : 2);
        return small;
    }

    private static boolean isSmallObject(ClientLevel level, SitePos site) {
        BlockPos waterPos = site.getPos();
        BlockPos seed = solidNeighborAt(level, waterPos);
        if (seed == null) return false;
        return floodLandmassSize(level, seed, waterPos.getY()) <= LANDMASS_CAP;
    }

    private static BlockPos solidNeighborAt(ClientLevel level, BlockPos waterPos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = waterPos.relative(dir);
            if (isSolidColumn(level, neighbor)) return neighbor;
        }
        return null;
    }

    private static boolean isSolidColumn(ClientLevel level, BlockPos pos) {
        return !level.isEmptyBlock(pos) && !WavifyWaveHandler.posIsWater(level, pos);
    }

    private static int floodLandmassSize(ClientLevel level, BlockPos seed, int seaY) {
        Set<Long> visited = Sets.newHashSet();
        Queue<BlockPos> queue = Queues.newArrayDeque();
        queue.add(seed);
        visited.add(packXZ(seed.getX(), seed.getZ()));
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (++count > LANDMASS_CAP) return count;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = current.getX() + dx;
                    int nz = current.getZ() + dz;
                    if (!visited.add(packXZ(nx, nz))) continue;
                    cursor.set(nx, seaY, nz);
                    if (isSolidColumn(level, cursor)) queue.add(new BlockPos(nx, seaY, nz));
                }
            }
        }
        return count;
    }

    private static long packXZ(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((long) z << 32);
    }
}
