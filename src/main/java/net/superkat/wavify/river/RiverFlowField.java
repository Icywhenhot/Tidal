package net.superkat.wavify.river;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.superkat.wavify.scan.WaterHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

// cached flow field for river waves, only over a window around the player
// direction is the gradient of the along water distance from one end of each river
// gradient fields are irrotational so neighbouring cells can never point at each other
// that kills head on collisions and overlapping upstream/downstream waves by construction
// distance barely changes across a cross section and grows along the length, so the gradient
// points down the channel evenly even on wide rivers
// steps, all bounded to the window and rebuilt only when the player moves or on a timeout:
// occupancy map, connected components, pick a seed end per component,
// dijkstra out from the seed, then fill the gradient-less cells and smooth once
public final class RiverFlowField {
    // how far out from the player we bother looking
    private static final int WINDOW_RADIUS = 60;
    // coarse cell size, flow changes slowly so this is plenty
    private static final int CELL = 4;
    // rebuild once the player wanders this far from the last centre
    private static final int REBUILD_MOVE = 8;
    // and rebuild this often anyway so terrain edits show up
    private static final int REBUILD_INTERVAL = 200;

    private static final double SQRT2 = Math.sqrt(2.0);

    private static final int OCEAN_RIVER_RAY = 24;
    private static final int OCEAN_RIVER_CELL_BITS = 2; // 4 block classification cells
    private final Long2ByteOpenHashMap oceanRiverCache = new Long2ByteOpenHashMap();

    // block resolution occupancy over the window
    private int originX;
    private int originZ;
    private int blockW;
    private int blockH;
    private boolean[] water = new boolean[0];

    // coarse flow grid, (0, 0) means the cell is invalid
    private int gridW;
    private int gridH;
    private float[] dirX = new float[0];
    private float[] dirZ = new float[0];

    private int builtCenterX;
    private int builtCenterZ;
    private long builtTick = Long.MIN_VALUE;
    private boolean built = false;

    // rebuilds if the cache went stale for where the player is now, cheap when it hasn't
    public void ensureBuilt(ClientWorld world, WaterHandler waterHandler, double px, double pz, long tick) {
        int cx = MathHelper.floor(px);
        int cz = MathHelper.floor(pz);
        if (built
                && Math.abs(cx - builtCenterX) <= REBUILD_MOVE
                && Math.abs(cz - builtCenterZ) <= REBUILD_MOVE
                && tick - builtTick < REBUILD_INTERVAL) {
            return;
        }
        build(world, waterHandler, cx, cz, tick);
    }

    // flow direction at a spot, null if you're outside the window or off the channel
    @Nullable
    public RiverFlow.Flow flowAt(double x, double z) {
        if (!built) return null;
        int lx = MathHelper.floor(x) - originX;
        int lz = MathHelper.floor(z) - originZ;
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

    // true when worldgen tagged a stripe of ocean as IS_RIVER and it isn't really a river
    // cached per 4 block cell, thrown away on every rebuild
    public boolean isOceanSurroundedRiver(ClientWorld world, BlockPos pos) {
        long key = (((long) (pos.getX() >> OCEAN_RIVER_CELL_BITS)) & 0xFFFFFFFFL)
                | (((long) (pos.getZ() >> OCEAN_RIVER_CELL_BITS)) << 32);
        byte cached = this.oceanRiverCache.get(key); // 0 means we haven't checked yet
        if (cached != 0) return cached == 2;
        boolean embedded = RiverFlow.isOceanSurrounded(world, pos, OCEAN_RIVER_RAY);
        this.oceanRiverCache.put(key, (byte) (embedded ? 2 : 1)); // 1 is a real river, 2 is embedded
        return embedded;
    }

    private void build(ClientWorld world, WaterHandler waterHandler, int cx, int cz, long tick) {
        this.oceanRiverCache.clear();
        // keep the old field around so the new one can match its direction where they overlap
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
                long key = new ChunkPos(playerChunkX + cdx, playerChunkZ + cdz).toLong();
                Set<BlockPos> waters = waterHandler.waters.get(key);
                if (waters == null || waters.isEmpty()) continue;

                for (BlockPos w : waters) {
                    int lx = w.getX() - this.originX;
                    int lz = w.getZ() - this.originZ;
                    if (lx < 0 || lz < 0 || lx >= this.blockW || lz >= this.blockH) continue;
                    if (!RiverFlow.isRiverWater(world, w)) continue;
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
        List<List<Integer>> components = new ArrayList<>();

        for (int start = 0; start < n; start++) {
            if (visited[start] || !cellWater[start]) continue;
            List<Integer> component = gatherComponent(start, cellWater, visited);
            components.add(component);
            int seed = chooseSeed(component);
            geodesic(seed, cellWater, dist);
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

    // picks which end of the river to flow away from, the most extreme cell along the principal axis
    // the axis gets pinned to a fixed sign so every client lands on the same answer and it survives rebuilds
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

        // force the dominant component positive, only wobbles on the exact 45 degree tie
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

    // dijkstra along the water from the seed, staying inside one connected component
    private void geodesic(int seed, boolean[] cellWater, double[] dist) {
        dist[seed] = 0;
        boolean[] settled = new boolean[this.gridW * this.gridH];
        PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.comparingDouble(idx -> dist[idx]));
        pq.add(seed);

        while (!pq.isEmpty()) {
            int i = pq.poll();
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
                        pq.add(ni);
                    }
                }
            }
        }
    }

    // normalised gradient of that distance, points downstream away from the seed
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

    // stops the flow flipping as you walk around, if a component overlaps the old field and disagrees we flip it
    // brand new components with no overlap, like after a teleport, just keep whatever they picked
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

    // patches the handful of cells with no gradient, the seed and watershed ones, from their neighbours
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

    // a cell counts as water if any block in it is, keeps narrow rivers from vanishing
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

    // debug, one arrow per cell so you can actually see the field in world
    public void addDebugMarkers(ClientWorld world, List<RiverFlow.DebugMarker> out, int playerY) {
        if (!built) return;
        for (int gz = 0; gz < this.gridH; gz++) {
            for (int gx = 0; gx < this.gridW; gx++) {
                int i = gz * this.gridW + gx;
                if (isInvalid(i)) continue;
                int wx = this.originX + gx * CELL + CELL / 2;
                int wz = this.originZ + gz * CELL + CELL / 2;
                int y = RiverFlow.surfaceWaterY(world, wx + 0.5, wz + 0.5, playerY);
                if (y == Integer.MIN_VALUE) continue;
                float yaw = (float) Math.toDegrees(Math.atan2(this.dirZ[i], this.dirX[i]));
                out.add(new RiverFlow.DebugMarker(wx + 0.5, y + 1.3, wz + 0.5, new Vector3f(0.2f, 0.95f, 0.95f), 0.8f, true, yaw, 0.14f, 45));
            }
        }
    }
}
