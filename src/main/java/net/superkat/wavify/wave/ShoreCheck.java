package net.superkat.wavify.wave;

import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
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

    public static boolean isOceanConnected(ClientWorld world, BlockPos spawn, float yaw) {
        Walk w = walk(world, spawn, yaw, -1, 96);
        return !w.river() && w.ocean();
    }

    public static boolean isPathSafe(ClientWorld world, BlockPos spawn, float yaw) {
        return !walk(world, spawn, yaw, 1, 80).river();
    }

    private static Walk walk(ClientWorld world, BlockPos from, float yaw, double sign, int steps) {
        double rad = Math.toRadians(yaw);
        double dirX = Math.cos(rad) * sign;
        double dirZ = Math.sin(rad) * sign;
        double x = from.getX() + 0.5;
        double z = from.getZ() + 0.5;
        int prefY = from.getY() - 1;
        boolean ocean = world.getBiome(from).isIn(BiomeTags.IS_OCEAN);

        int cellX = Integer.MIN_VALUE;
        int cellZ = Integer.MIN_VALUE;

        for (int i = 0; i < steps; i++) {
            x += dirX * STEP;
            z += dirZ * STEP;

            int stepX = MathHelper.floor(x);
            int stepZ = MathHelper.floor(z);
            if (stepX == cellX && stepZ == cellZ) continue;
            cellX = stepX;
            cellZ = stepZ;

            BlockPos water = nearestSurfaceWater(world, x, z, prefY);
            if (water == null) return new Walk(false, ocean);
            prefY = water.getY();

            if (RiverFlow.isRiverWater(world, water)) return new Walk(true, ocean);
            if (world.getBiome(water).isIn(BiomeTags.IS_OCEAN)) ocean = true;
        }

        return new Walk(false, ocean);
    }

    private static BlockPos nearestSurfaceWater(ClientWorld world, double x, double z, int prefY) {
        int baseX = MathHelper.floor(x);
        int baseZ = MathHelper.floor(z);
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int bestX = 0;
        int bestZ = 0;
        boolean found = false;
        double bestDistance = Double.MAX_VALUE;

        for (int y = prefY + 1; y >= prefY - 2; y--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    cursor.set(baseX + dx, y, baseZ + dz);
                    if (!RiverFlow.isSurfaceWater(world, cursor)) continue;

                    double dist = MathHelper.square(cursor.getX() + 0.5 - x) + MathHelper.square(cursor.getZ() + 0.5 - z);
                    if (dist < bestDistance) {
                        bestDistance = dist;
                        bestX = cursor.getX();
                        bestZ = cursor.getZ();
                        found = true;
                    }
                }
            }
            if (found) return new BlockPos(bestX, y, bestZ);
        }

        return null;
    }

    public static boolean isIsolatedObject(ClientWorld world, SitePos site) {
        if (site.shoreClass != 0) return site.shoreClass == 1;
        boolean small = isSmallObject(world, site);
        site.shoreClass = (byte) (small ? 1 : 2);
        return small;
    }

    private static boolean isSmallObject(ClientWorld world, SitePos site) {
        BlockPos waterPos = site.getPos();
        BlockPos seed = solidNeighborAt(world, waterPos);
        if (seed == null) return false;
        return floodLandmassSize(world, seed, waterPos.getY()) <= LANDMASS_CAP;
    }

    private static BlockPos solidNeighborAt(ClientWorld world, BlockPos waterPos) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = waterPos.offset(dir);
            if (isSolidColumn(world, neighbor)) return neighbor;
        }
        return null;
    }

    private static boolean isSolidColumn(ClientWorld world, BlockPos pos) {
        return !world.isAir(pos) && !WavifyWaveHandler.posIsWater(world, pos);
    }

    private static int floodLandmassSize(ClientWorld world, BlockPos seed, int seaY) {
        Set<Long> visited = Sets.newHashSet();
        Queue<BlockPos> queue = Queues.newArrayDeque();
        queue.add(seed);
        visited.add(packXZ(seed.getX(), seed.getZ()));
        int count = 0;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
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
                    if (isSolidColumn(world, cursor)) queue.add(new BlockPos(nx, seaY, nz));
                }
            }
        }
        return count;
    }

    private static long packXZ(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((long) z << 32);
    }
}
