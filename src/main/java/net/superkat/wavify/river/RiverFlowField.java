package net.superkat.wavify.river;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class RiverFlowField {

    private static final int WINDOW_RADIUS = 60;

    private static final int CELL = 4;

    private static final int REBUILD_MOVE = 8;

    private static final int REBUILD_INTERVAL = 200;

    private static final double SQRT2 = Math.sqrt(2.0);

    private static final int OCEAN_RIVER_RAY = 24;
    private static final int OCEAN_RIVER_CELL_BITS = 2;
    private final Long2ByteOpenHashMap oceanRiverCache = new Long2ByteOpenHashMap();

    private int originX;
    private int originZ;
    private int blockW;
    private int blockH;
    private boolean[] water = new boolean[0];

    private int gridW;
    private int gridH;
    private float[] dirX = new float[0];
    private float[] dirZ = new float[0];

    private int builtCenterX;
    private int builtCenterZ;
    private long builtTick = Long.MIN_VALUE;
    private boolean built = false;

    public void ensureBuilt(Map<Long, Set<BlockPos>> riverWaters, double px, double pz, long tick) {
        int cx = Mth.floor(px);
        int cz = Mth.floor(pz);
        if (built
                && Math.abs(cx - builtCenterX) <= REBUILD_MOVE
                && Math.abs(cz - builtCenterZ) <= REBUILD_MOVE
                && tick - builtTick < REBUILD_INTERVAL) {
            return;
        }
        build(riverWaters, cx, cz, tick);
    }

    @Nullable
    public RiverFlow.Flow flowAt(Level level, double x, int y, double z) {
        RiverFlow.Flow dyn = RiverFlow.dynamicFlowAt(level, x, y, z);
        if (dyn != null) return dyn;
        return flowAt(x, z);
    }

    @Nullable
    public RiverFlow.Flow flowAt(double x, double z) {
        if (!built) return null;
        int lx = Mth.floor(x) - originX;
        int lz = Mth.floor(z) - originZ;
        if (lx < 0 || lz < 0) return null;
        int gx = lx / CELL;
        int gz = lz / CELL;
        if (gx >= gridW || gz >= gridH) return null;
        int i = gz * gridW + gx;
        float dx = dirX[i];
        float dz = dirZ[i];
        if (dx == 0f && dz == 0f) return null;
        return new RiverFlow.Flow(dx, dz);
    }

    public boolean isOceanSurroundedRiver(ClientLevel level, BlockPos pos) {
        long key = (((long) (pos.getX() >> OCEAN_RIVER_CELL_BITS)) & 0xFFFFFFFFL)
                | (((long) (pos.getZ() >> OCEAN_RIVER_CELL_BITS)) << 32);
        byte cached = this.oceanRiverCache.get(key);
        if (cached != 0) return cached == 2;
        boolean embedded = RiverFlow.isOceanSurrounded(level, pos, OCEAN_RIVER_RAY);
        this.oceanRiverCache.put(key, (byte) (embedded ? 2 : 1));
        return embedded;
    }

    private void build(Map<Long, Set<BlockPos>> riverWaters, int cx, int cz, long tick) {
        this.oceanRiverCache.clear();

        boolean hadPrev = this.built;
        int prevOriginX = this.originX;
        int prevOriginZ = this.originZ;
        int prevGridW = this.gridW;
        int prevGridH = this.gridH;
        float[] prevDirX = this.dirX;
        float[] prevDirZ = this.dirZ;

        this.originX = cx - WINDOW_RADIUS;
        this.originZ = cz - WINDOW_RADIUS;
        this.blockW = WINDOW_RADIUS * 2 + 1;
        this.blockH = this.blockW;
        this.water = new boolean[this.blockW * this.blockH];

        int chunkRadius = (WINDOW_RADIUS >> 4) + 1;
        int playerChunkX = cx >> 4;
        int playerChunkZ = cz >> 4;
        for (int cdx = -chunkRadius; cdx <= chunkRadius; cdx++) {
            for (int cdz = -chunkRadius; cdz <= chunkRadius; cdz++) {
                long key = new ChunkPos(playerChunkX + cdx, playerChunkZ + cdz).pack();
                Set<BlockPos> waters = riverWaters.get(key);
                if (waters == null || waters.isEmpty()) continue;

                for (BlockPos w : waters) {
                    int lx = w.getX() - this.originX;
                    int lz = w.getZ() - this.originZ;
                    if (lx < 0 || lz < 0 || lx >= this.blockW || lz >= this.blockH) continue;
                    this.water[lz * this.blockW + lx] = true;
                }
            }
        }

        this.gridW = this.blockW / CELL;
        this.gridH = this.blockH / CELL;
        int n = this.gridW * this.gridH;
        this.dirX = new float[n];
        this.dirZ = new float[n];

        boolean[] cellWater = new boolean[n];
        for (int gz = 0; gz < this.gridH; gz++) {
            for (int gx = 0; gx < this.gridW; gx++) {
                cellWater[gz * this.gridW + gx] = cellHasWater(gx, gz);
            }
        }

        double[] dist = new double[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        boolean[] visited = new boolean[n];
        boolean[] settled = new boolean[n];
        List<List<Integer>> components = new ArrayList<>();

        for (int start = 0; start < n; start++) {
            if (visited[start] || !cellWater[start]) continue;
            List<Integer> component = gatherComponent(start, cellWater, visited);
            components.add(component);
            int seed = chooseSeed(component);
            geodesic(seed, cellWater, dist, settled);
        }

        computeGradient(cellWater, dist);
        if (hadPrev) {
            reconcileOrientation(components, prevOriginX, prevOriginZ, prevGridW, prevGridH, prevDirX, prevDirZ);
        }
        fillGaps(cellWater);
        smoothOnce(cellWater);

        this.built = true;
        this.builtCenterX = cx;
        this.builtCenterZ = cz;
        this.builtTick = tick;
    }

    private List<Integer> gatherComponent(int start, boolean[] cellWater, boolean[] visited) {
        List<Integer> component = new ArrayList<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        visited[start] = true;
        while (!queue.isEmpty()) {
            int i = queue.poll();
            component.add(i);
            int ix = i % this.gridW;
            int iz = i / this.gridW;
            for (int ddx = -1; ddx <= 1; ddx++) {
                for (int ddz = -1; ddz <= 1; ddz++) {
                    if (ddx == 0 && ddz == 0) continue;
                    int nx = ix + ddx;
                    int nz = iz + ddz;
                    if (nx < 0 || nz < 0 || nx >= this.gridW || nz >= this.gridH) continue;
                    int ni = nz * this.gridW + nx;
                    if (visited[ni] || !cellWater[ni]) continue;
                    visited[ni] = true;
                    queue.add(ni);
                }
            }
        }
        return component;
    }

    private int chooseSeed(List<Integer> component) {
        double meanX = 0;
        double meanZ = 0;
        for (int i : component) {
            meanX += i % this.gridW;
            meanZ += i / this.gridW;
        }
        meanX /= component.size();
        meanZ /= component.size();

        double covXX = 0;
        double covXZ = 0;
        double covZZ = 0;
        for (int i : component) {
            double rx = (i % this.gridW) - meanX;
            double rz = (i / this.gridW) - meanZ;
            covXX += rx * rx;
            covXZ += rx * rz;
            covZZ += rz * rz;
        }

        double axisX;
        double axisZ;
        if (covXX + covZZ < 1e-6) {
            axisX = 1;
            axisZ = 0;
        } else {
            double theta = 0.5 * Math.atan2(2 * covXZ, covXX - covZZ);
            axisX = Math.cos(theta);
            axisZ = Math.sin(theta);
        }

        if (Math.abs(axisX) >= Math.abs(axisZ)) {
            if (axisX < 0) {
                axisX = -axisX;
                axisZ = -axisZ;
            }
        } else if (axisZ < 0) {
            axisX = -axisX;
            axisZ = -axisZ;
        }

        int seed = component.get(0);
        double bestProjection = Double.POSITIVE_INFINITY;
        for (int i : component) {
            double projection = (i % this.gridW) * axisX + (i / this.gridW) * axisZ;
            if (projection < bestProjection) {
                bestProjection = projection;
                seed = i;
            }
        }
        return seed;
    }

    private record Step(int index, double distance) {
    }

    private void geodesic(int seed, boolean[] cellWater, double[] dist, boolean[] settled) {
        dist[seed] = 0;
        PriorityQueue<Step> pq = new PriorityQueue<>(Comparator.comparingDouble(Step::distance));
        pq.add(new Step(seed, 0));

        while (!pq.isEmpty()) {
            int i = pq.poll().index();
            if (settled[i]) continue;
            settled[i] = true;
            int ix = i % this.gridW;
            int iz = i / this.gridW;

            for (int ddx = -1; ddx <= 1; ddx++) {
                for (int ddz = -1; ddz <= 1; ddz++) {
                    if (ddx == 0 && ddz == 0) continue;
                    int nx = ix + ddx;
                    int nz = iz + ddz;
                    if (nx < 0 || nz < 0 || nx >= this.gridW || nz >= this.gridH) continue;
                    int ni = nz * this.gridW + nx;
                    if (!cellWater[ni] || settled[ni]) continue;
                    double cost = (ddx != 0 && ddz != 0) ? SQRT2 : 1.0;
                    if (dist[i] + cost < dist[ni]) {
                        dist[ni] = dist[i] + cost;
                        pq.add(new Step(ni, dist[ni]));
                    }
                }
            }
        }
    }

    private void computeGradient(boolean[] cellWater, double[] dist) {
        for (int iz = 0; iz < this.gridH; iz++) {
            for (int ix = 0; ix < this.gridW; ix++) {
                int i = iz * this.gridW + ix;
                if (!cellWater[i] || Double.isInfinite(dist[i])) continue;

                double ddx = axisDerivative(ix, iz, 1, 0, cellWater, dist);
                double ddz = axisDerivative(ix, iz, 0, 1, cellWater, dist);
                double len = Math.sqrt(ddx * ddx + ddz * ddz);
                if (len > 1e-6) {
                    this.dirX[i] = (float) (ddx / len);
                    this.dirZ[i] = (float) (ddz / len);
                }
            }
        }
    }

    private void reconcileOrientation(List<List<Integer>> components,
                                      int prevOriginX, int prevOriginZ, int prevGridW, int prevGridH,
                                      float[] prevDirX, float[] prevDirZ) {
        if (prevDirX.length == 0) return;

        for (List<Integer> component : components) {
            double dot = 0;
            int overlap = 0;
            for (int i : component) {
                if (isInvalid(i)) continue;
                int gx = i % this.gridW;
                int gz = i / this.gridW;
                int worldX = this.originX + gx * CELL + CELL / 2;
                int worldZ = this.originZ + gz * CELL + CELL / 2;

                int plx = worldX - prevOriginX;
                int plz = worldZ - prevOriginZ;
                if (plx < 0 || plz < 0) continue;
                int pgx = plx / CELL;
                int pgz = plz / CELL;
                if (pgx >= prevGridW || pgz >= prevGridH) continue;
                int pi = pgz * prevGridW + pgx;
                if (prevDirX[pi] == 0f && prevDirZ[pi] == 0f) continue;

                dot += this.dirX[i] * prevDirX[pi] + this.dirZ[i] * prevDirZ[pi];
                overlap++;
            }

            if (overlap > 0 && dot < 0) {
                for (int i : component) {
                    this.dirX[i] = -this.dirX[i];
                    this.dirZ[i] = -this.dirZ[i];
                }
            }
        }
    }

    private double axisDerivative(int ix, int iz, int sx, int sz, boolean[] cellWater, double[] dist) {
        int plus = neighbor(ix + sx, iz + sz);
        int minus = neighbor(ix - sx, iz - sz);
        boolean hasPlus = plus >= 0 && cellWater[plus] && !Double.isInfinite(dist[plus]);
        boolean hasMinus = minus >= 0 && cellWater[minus] && !Double.isInfinite(dist[minus]);
        int self = iz * this.gridW + ix;
        if (hasPlus && hasMinus) return (dist[plus] - dist[minus]) / 2.0;
        if (hasPlus) return dist[plus] - dist[self];
        if (hasMinus) return dist[self] - dist[minus];
        return 0;
    }

    private int neighbor(int gx, int gz) {
        if (gx < 0 || gz < 0 || gx >= this.gridW || gz >= this.gridH) return -1;
        return gz * this.gridW + gx;
    }

    private void fillGaps(boolean[] cellWater) {
        for (int pass = 0; pass < 2; pass++) {
            for (int iz = 0; iz < this.gridH; iz++) {
                for (int ix = 0; ix < this.gridW; ix++) {
                    int i = iz * this.gridW + ix;
                    if (!cellWater[i] || !isInvalid(i)) continue;
                    double sx = 0;
                    double sz = 0;
                    for (int ddx = -1; ddx <= 1; ddx++) {
                        for (int ddz = -1; ddz <= 1; ddz++) {
                            if (ddx == 0 && ddz == 0) continue;
                            int ni = neighbor(ix + ddx, iz + ddz);
                            if (ni < 0 || isInvalid(ni)) continue;
                            sx += this.dirX[ni];
                            sz += this.dirZ[ni];
                        }
                    }
                    double len = Math.sqrt(sx * sx + sz * sz);
                    if (len > 1e-6) {
                        this.dirX[i] = (float) (sx / len);
                        this.dirZ[i] = (float) (sz / len);
                    }
                }
            }
        }
    }

    private void smoothOnce(boolean[] cellWater) {
        int n = this.gridW * this.gridH;
        float[] outX = new float[n];
        float[] outZ = new float[n];

        for (int iz = 0; iz < this.gridH; iz++) {
            for (int ix = 0; ix < this.gridW; ix++) {
                int i = iz * this.gridW + ix;
                if (isInvalid(i)) continue;

                double sx = this.dirX[i];
                double sz = this.dirZ[i];
                for (int ddx = -1; ddx <= 1; ddx++) {
                    for (int ddz = -1; ddz <= 1; ddz++) {
                        if (ddx == 0 && ddz == 0) continue;
                        int ni = neighbor(ix + ddx, iz + ddz);
                        if (ni < 0 || isInvalid(ni)) continue;
                        sx += this.dirX[ni];
                        sz += this.dirZ[ni];
                    }
                }
                double len = Math.sqrt(sx * sx + sz * sz);
                if (len > 1e-6) {
                    outX[i] = (float) (sx / len);
                    outZ[i] = (float) (sz / len);
                } else {
                    outX[i] = this.dirX[i];
                    outZ[i] = this.dirZ[i];
                }
            }
        }

        this.dirX = outX;
        this.dirZ = outZ;
    }

    private boolean isInvalid(int i) {
        return this.dirX[i] == 0f && this.dirZ[i] == 0f;
    }

    private boolean waterLocal(int bx, int bz) {
        if (bx < 0 || bz < 0 || bx >= this.blockW || bz >= this.blockH) return false;
        return this.water[bz * this.blockW + bx];
    }

    private boolean cellHasWater(int gx, int gz) {
        int baseX = gx * CELL;
        int baseZ = gz * CELL;
        for (int dx = 0; dx < CELL; dx++) {
            for (int dz = 0; dz < CELL; dz++) {
                if (waterLocal(baseX + dx, baseZ + dz)) return true;
            }
        }
        return false;
    }

}
