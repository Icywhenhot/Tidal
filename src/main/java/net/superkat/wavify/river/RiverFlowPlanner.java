package net.superkat.wavify.river;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.superkat.wavify.scan.WaterHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Client-side planner for river flow, moving-wave paths, and shallow standing-wave candidates.
 */
public final class RiverFlowPlanner {
    private static final int[][] HORIZONTAL_NEIGHBORS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    };

    private RiverFlowPlanner() {
    }

    public enum RejectReason {
        NO_SEED(new Vector3f(0.95f, 0.25f, 0.25f)),
        EDGE_HIT(new Vector3f(1.0f, 0.55f, 0.15f)),
        DEAD_END(new Vector3f(0.85f, 0.2f, 0.85f)),
        TOO_SHORT(new Vector3f(0.95f, 0.2f, 0.55f));

        private final Vector3f color;

        RejectReason(Vector3f color) {
            this.color = color;
        }

        public Vector3f color() {
            return new Vector3f(this.color);
        }
    }

    public record FlowReference(double x, double z, double dirX, double dirZ) {
    }

    public record DebugMarker(double x, double y, double z, Vector3f color, float scale, boolean arrow, float yaw, float speed, int lifetime) {
    }

    public record RiverPlanPoint(BlockPos water, double x, double z, float yaw, double dirX, double dirZ, int bankDistance) {
    }

    public record RiverTravelPlan(long reachId, BlockPos spawnSurface, List<RiverPlanPoint> points, int width, int requiredBankDistance, float speed) {
    }

    public record StandingWaveCandidate(long reachId, BlockPos anchorWater, float yaw, double dirX, double dirZ, float energy, int width, int trainLength) {
    }

    public record CenterlineSample(BlockPos water, double x, double z, double dirX, double dirZ, float yaw, int bankDistance) {
    }

    private record TangentSample(double centerX, double centerZ, double dirX, double dirZ, float yaw) {
    }

    public static final class RiverReach {
        private final long id;
        private final List<BlockPos> waters;
        private final Set<Long> waterKeys;
        private final List<BlockPos> bankWaters;
        private final Map<Long, Integer> bankDistances;
        private final Map<Long, Integer> depths;
        private final List<BlockPos> centerline;
        private final List<CenterlineSample> samples;
        private final double centerX;
        private final double centerZ;
        private final double axisX;
        private final double axisZ;
        private final BlockPos upstreamEnd;
        private final BlockPos downstreamEnd;
        private final double upstreamProjection;
        private final double downstreamProjection;

        private RiverReach(long id,
                           List<BlockPos> waters,
                           Set<Long> waterKeys,
                           List<BlockPos> bankWaters,
                           Map<Long, Integer> bankDistances,
                           Map<Long, Integer> depths,
                           List<BlockPos> centerline,
                           List<CenterlineSample> samples,
                           double centerX,
                           double centerZ,
                           double axisX,
                           double axisZ,
                           BlockPos upstreamEnd,
                           BlockPos downstreamEnd) {
            this.id = id;
            this.waters = waters;
            this.waterKeys = waterKeys;
            this.bankWaters = bankWaters;
            this.bankDistances = bankDistances;
            this.depths = depths;
            this.centerline = centerline;
            this.samples = samples;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.axisX = axisX;
            this.axisZ = axisZ;
            this.upstreamEnd = upstreamEnd;
            this.downstreamEnd = downstreamEnd;
            this.upstreamProjection = projection(upstreamEnd);
            this.downstreamProjection = projection(downstreamEnd);
        }

        public long id() {
            return id;
        }

        public List<BlockPos> waters() {
            return waters;
        }

        public List<BlockPos> bankWaters() {
            return bankWaters;
        }

        public List<BlockPos> centerline() {
            return centerline;
        }

        public List<CenterlineSample> samples() {
            return samples;
        }

        public double axisX() {
            return axisX;
        }

        public double axisZ() {
            return axisZ;
        }

        public BlockPos upstreamEnd() {
            return upstreamEnd;
        }

        public BlockPos downstreamEnd() {
            return downstreamEnd;
        }

        public double upstreamProjection() {
            return upstreamProjection;
        }

        public double downstreamProjection() {
            return downstreamProjection;
        }

        public double reachLength() {
            return Math.max(0.0, this.downstreamProjection - this.upstreamProjection);
        }

        public boolean contains(BlockPos pos) {
            return this.waterKeys.contains(pos.asLong());
        }

        public int bankDistance(BlockPos pos) {
            return this.bankDistances.getOrDefault(pos.asLong(), 0);
        }

        public int depth(BlockPos pos) {
            return this.depths.getOrDefault(pos.asLong(), 1);
        }

        public double projection(BlockPos pos) {
            return projection(pos.getX() + 0.5, pos.getZ() + 0.5);
        }

        public double projection(double x, double z) {
            return (x - this.centerX) * this.axisX + (z - this.centerZ) * this.axisZ;
        }
    }

    public static List<RiverReach> buildReaches(ClientLevel world,
                                                WaterHandler waterHandler,
                                                ChunkPos playerChunk,
                                                int chunkRadius,
                                                @Nullable List<DebugMarker> debugMarkers) {
        ChunkPos start = new ChunkPos(playerChunk.x() + chunkRadius, playerChunk.z() + chunkRadius);
        ChunkPos end = new ChunkPos(playerChunk.x() - chunkRadius, playerChunk.z() - chunkRadius);

        Map<Long, BlockPos> allRiverWaters = new HashMap<>();
        for (ChunkPos chunkPos : ChunkPos.rangeClosed(start, end).toList()) {
            Set<BlockPos> waters = waterHandler.waters.get(chunkPos.pack());
            if (waters == null || waters.isEmpty()) continue;

            for (BlockPos water : waters) {
                if (!isRiverSurfaceWater(world, water)) continue;
                allRiverWaters.put(water.asLong(), water);
            }
        }

        Set<Long> remaining = new HashSet<>(allRiverWaters.keySet());
        List<RiverReach> reaches = new ArrayList<>();
        while (!remaining.isEmpty()) {
            long startKey = remaining.iterator().next();
            BlockPos startWater = allRiverWaters.get(startKey);
            List<BlockPos> component = new ArrayList<>();
            Queue<BlockPos> queue = new ArrayDeque<>();
            queue.add(startWater);
            remaining.remove(startKey);

            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                component.add(current);

                for (BlockPos neighbor : collectNeighborWaters(current, allRiverWaters)) {
                    if (!remaining.remove(neighbor.asLong())) continue;
                    queue.add(neighbor);
                }
            }

            if (component.size() < 24) continue;
            RiverReach reach = buildReach(world, component, debugMarkers);
            if (reach != null) {
                reaches.add(reach);
            }
        }

        reaches.sort(Comparator.comparingDouble(RiverReach::reachLength).reversed());
        return reaches;
    }

    @Nullable
    public static RiverTravelPlan buildTravelPlan(ClientLevel world,
                                                  RiverReach reach,
                                                  @Nullable FlowReference reference,
                                                  RandomSource random,
                                                  @Nullable List<DebugMarker> debugMarkers) {
        List<CenterlineSample> samples = reach.samples();
        if (samples.size() < 8) {
            addRejectMarker(debugMarkers, reach.upstreamEnd(), RejectReason.NO_SEED);
            return null;
        }

        int startIdx = chooseSeedIndex(world, samples, reference, random);
        if (startIdx < 0) {
            addRejectMarker(debugMarkers, reach.upstreamEnd(), RejectReason.NO_SEED);
            return null;
        }

        List<RiverPlanPoint> points = new ArrayList<>();
        int maxLen = Math.min(24, samples.size() - startIdx);
        int totalBank = 0;
        int minBank = Integer.MAX_VALUE;
        BlockPos spawnSurface = null;

        for (int i = 0; i < maxLen; i++) {
            CenterlineSample s = samples.get(startIdx + i);
            if (s.bankDistance() < 2) {
                if (points.size() >= 6) break;
                addRejectMarker(debugMarkers, s.water(), RejectReason.EDGE_HIT);
                return null;
            }
            if (!world.getBlockState(s.water().above()).isAir()) {
                if (points.size() >= 6) break;
                if (i == 0) {
                    addRejectMarker(debugMarkers, s.water(), RejectReason.EDGE_HIT);
                    return null;
                }
                break;
            }
            if (i == 0) spawnSurface = s.water().above();
            points.add(new RiverPlanPoint(s.water(), s.x(), s.z(), s.yaw(), s.dirX(), s.dirZ(), s.bankDistance()));
            totalBank += s.bankDistance();
            minBank = Math.min(minBank, s.bankDistance());
        }

        if (points.size() < 6 || spawnSurface == null) {
            addRejectMarker(debugMarkers, samples.get(startIdx).water(), RejectReason.TOO_SHORT);
            return null;
        }

        double avgBank = totalBank / (double) points.size();
        // Per-wave width jitter so a single river produces a mix of crest sizes instead of all waves
        // clamping to the same value.
        double widthJitter = 0.75 + random.nextDouble() * 0.55;
        int width = Mth.clamp((int) Math.round(avgBank * 1.25 * widthJitter), 3, 9);
        if ((width & 1) == 0) width = Math.max(3, width - 1);
        int requiredBankDistance = Math.max(2, Math.min(3, minBank));

        if (!pathHasClearance(reach, points, requiredBankDistance)) {
            addRejectMarker(debugMarkers, spawnSurface, RejectReason.EDGE_HIT);
            return null;
        }

        float speed = computeTravelSpeed(reach, points, requiredBankDistance);
        RiverTravelPlan plan = new RiverTravelPlan(reach.id(), spawnSurface, List.copyOf(points), width, requiredBankDistance, speed);
        addAcceptedPathMarkers(debugMarkers, plan);
        return plan;
    }

    private static int chooseSeedIndex(ClientLevel world,
                                       List<CenterlineSample> samples,
                                       @Nullable FlowReference reference,
                                       RandomSource random) {
        int latestStart = samples.size() - 6;
        if (latestStart < 0) return -1;

        if (reference != null) {
            int refIdx = nearestSampleIndex(samples, reference.x(), reference.z());
            int offset = 4 + random.nextInt(5);
            int idx = Math.min(latestStart, Math.max(0, refIdx - offset));
            for (int probe = 0; probe < 8 && idx <= latestStart; probe++, idx++) {
                CenterlineSample s = samples.get(idx);
                if (s.bankDistance() < 2) continue;
                if (!world.getBlockState(s.water().above()).isAir()) continue;
                return idx;
            }
        }

        int lo = Math.min(latestStart, Math.max(0, (int) (samples.size() * 0.05)));
        int hi = Math.min(latestStart, Math.max(lo, (int) (samples.size() * 0.55)));
        int range = Math.max(1, hi - lo + 1);
        for (int attempt = 0; attempt < 8; attempt++) {
            int idx = lo + random.nextInt(range);
            CenterlineSample s = samples.get(idx);
            if (s.bankDistance() < 2) continue;
            if (!world.getBlockState(s.water().above()).isAir()) continue;
            return idx;
        }
        return -1;
    }

    @Nullable
    public static StandingWaveCandidate findStandingWaveCandidate(ClientLevel world,
                                                                  RiverReach reach,
                                                                  Set<Long> occupiedAnchors,
                                                                  RandomSource random,
                                                                  @Nullable List<DebugMarker> debugMarkers) {
        StandingWaveCandidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (BlockPos water : reach.waters()) {
            if (occupiedAnchors.contains(water.asLong())) continue;

            int depth = reach.depth(water);
            if (depth < 1 || depth > 2) continue;
            if (reach.bankDistance(water) < 2) continue;
            if (!world.getBlockState(water.above()).isAir()) continue;

            TangentSample tangent = tangentAtPos(reach, water.getX() + 0.5, water.getZ() + 0.5);
            if (!isStandingAnchorStable(world, reach, water, tangent.dirX(), tangent.dirZ())) continue;

            double normalX = -tangent.dirZ();
            double normalZ = tangent.dirX();

            int solidUpstream = 0;
            if (isSolidBlock(world, offset(water, -tangent.dirX(), -tangent.dirZ()))) solidUpstream += 2;
            if (isSolidBlock(world, offset(water, -tangent.dirX() + normalX * 0.9, -tangent.dirZ() + normalZ * 0.9))) solidUpstream++;
            if (isSolidBlock(world, offset(water, -tangent.dirX() - normalX * 0.9, -tangent.dirZ() - normalZ * 0.9))) solidUpstream++;
            if (solidUpstream == 0) continue;

            BlockPos oneDown = offset(water, tangent.dirX(), tangent.dirZ());
            BlockPos twoDown = offset(water, tangent.dirX() * 2.0, tangent.dirZ() * 2.0);
            if (!reach.contains(oneDown) || !reach.contains(twoDown)) continue;

            int hereWidth = reach.bankDistance(water);
            int downWidth = Math.max(reach.bankDistance(oneDown), reach.bankDistance(twoDown));
            double constrictionScore = Math.max(0.0, downWidth - hereWidth);
            double score = solidUpstream * 1.8 + constrictionScore * 1.4 + (3 - depth) * 1.5 + Math.min(2.0, hereWidth * 0.7);
            if (score <= bestScore) continue;

            int width = Mth.clamp(Math.max(3, hereWidth * 2 - 1), 3, 5);
            float energy = Mth.clamp((float) (0.58 + solidUpstream * 0.1 + constrictionScore * 0.08 + (2 - depth) * 0.08), 0.58f, 1.28f);
            int trainLength = Mth.clamp(1 + solidUpstream / 2 + (int) Math.floor(constrictionScore * 0.5), 1, 3);

            bestScore = score;
            best = new StandingWaveCandidate(reach.id(), water, tangent.yaw(), tangent.dirX(), tangent.dirZ(), energy, width, trainLength);
        }

        if (best != null) {
            addStandingCandidateMarker(debugMarkers, best);
        }
        return best;
    }

    public static boolean isStandingAnchorStable(ClientLevel world, RiverReach reach, BlockPos water, double dirX, double dirZ) {
        if (!reach.contains(water)) return false;
        if (reach.bankDistance(water) < 2) return false;
        int depth = reach.depth(water);
        if (depth < 1 || depth > 2) return false;
        if (!world.getFluidState(water).is(net.minecraft.tags.FluidTags.WATER)) return false;
        if (!world.getBlockState(water.above()).isAir()) return false;

        double normalX = -dirZ;
        double normalZ = dirX;

        BlockPos downstreamOne = offset(water, dirX, dirZ);
        BlockPos downstreamTwo = offset(water, dirX * 2.0, dirZ * 2.0);
        BlockPos left = offset(water, normalX, normalZ);
        BlockPos right = offset(water, -normalX, -normalZ);

        if (!reach.contains(downstreamOne) || !reach.contains(downstreamTwo)) return false;
        if (reach.bankDistance(downstreamOne) < 2 || reach.bankDistance(downstreamTwo) < 2) return false;
        if (!reach.contains(left) || !reach.contains(right)) return false;
        return reach.bankDistance(left) >= 1 && reach.bankDistance(right) >= 1;
    }

    public static boolean isRiverSurfaceWater(ClientLevel world, BlockPos pos) {
        if (world.getBiome(pos).is(BiomeTags.IS_RIVER)) return true;

        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
            if (world.getBiome(check).is(BiomeTags.IS_RIVER)) return true;
        }

        return false;
    }

    @Nullable
    private static RiverReach buildReach(ClientLevel world, List<BlockPos> component, @Nullable List<DebugMarker> debugMarkers) {
        Set<Long> waterKeys = new HashSet<>(component.size());
        long id = Long.MAX_VALUE;
        double sumX = 0.0;
        double sumZ = 0.0;
        for (BlockPos water : component) {
            waterKeys.add(water.asLong());
            id = Math.min(id, water.asLong());
            sumX += water.getX() + 0.5;
            sumZ += water.getZ() + 0.5;
        }

        double centerX = sumX / component.size();
        double centerZ = sumZ / component.size();

        List<BlockPos> bankWaters = new ArrayList<>();
        Map<Long, Integer> depths = new HashMap<>();
        for (BlockPos water : component) {
            depths.put(water.asLong(), computeDepth(world, water, 4));
            if (isBankWater(water, waterKeys)) {
                bankWaters.add(water);
            }
        }

        if (bankWaters.size() < 6) return null;

        Map<Long, Integer> bankDistances = computeBankDistances(component, bankWaters, waterKeys);
        double[] axis = computePrincipalAxis(component, centerX, centerZ, bankDistances);
        double axisX = axis[0];
        double axisZ = axis[1];

        BlockPos minProjection = component.get(0);
        BlockPos maxProjection = component.get(0);
        double minValue = projection(minProjection, centerX, centerZ, axisX, axisZ);
        double maxValue = minValue;
        for (BlockPos water : component) {
            double value = projection(water, centerX, centerZ, axisX, axisZ);
            if (value < minValue) {
                minValue = value;
                minProjection = water;
            }
            if (value > maxValue) {
                maxValue = value;
                maxProjection = water;
            }
        }

        boolean downstreamUsesMax = true;
        List<BlockPos> oceanMouth = component.stream().filter(water -> touchesOceanBiome(world, water)).toList();
        if (!oceanMouth.isEmpty()) {
            double mouthProjectionSum = 0.0;
            for (BlockPos mouthWater : oceanMouth) {
                mouthProjectionSum += projection(mouthWater, centerX, centerZ, axisX, axisZ);
            }
            double mouthProjection = mouthProjectionSum / oceanMouth.size();
            downstreamUsesMax = Math.abs(maxValue - mouthProjection) <= Math.abs(minValue - mouthProjection);
        }

        if (!downstreamUsesMax) {
            axisX = -axisX;
            axisZ = -axisZ;
        }

        final double alignedAxisX = axisX;
        final double alignedAxisZ = axisZ;

        BlockPos upstream = downstreamUsesMax ? minProjection : maxProjection;
        BlockPos downstream = downstreamUsesMax ? maxProjection : minProjection;

        Map<Long, Integer> downstreamGraphDist = computeGraphDistance(downstream, waterKeys);
        List<BlockPos> walked = walkCenterlineDownstream(upstream, waterKeys, bankDistances, downstreamGraphDist);

        if (walked.size() < 4) {
            walked = component.stream()
                    .sorted(Comparator.<BlockPos>comparingInt(water -> bankDistances.getOrDefault(water.asLong(), 0)).reversed())
                    .limit(24)
                    .sorted(Comparator.comparingDouble(water -> projection(water, centerX, centerZ, alignedAxisX, alignedAxisZ)))
                    .toList();
        }

        List<CenterlineSample> samples = buildCenterlineSamples(walked, bankDistances, alignedAxisX, alignedAxisZ);

        RiverReach reach = new RiverReach(id, List.copyOf(component), waterKeys, List.copyOf(bankWaters), bankDistances, depths, List.copyOf(walked), List.copyOf(samples), centerX, centerZ, alignedAxisX, alignedAxisZ, upstream, downstream);
        addReachDebugMarkers(debugMarkers, reach);
        return reach;
    }

    private static Map<Long, Integer> computeGraphDistance(BlockPos source, Set<Long> waterKeys) {
        Map<Long, Integer> dist = new HashMap<>();
        if (!waterKeys.contains(source.asLong())) return dist;

        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(source);
        dist.put(source.asLong(), 0);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int currentDist = dist.get(current.asLong());
            for (BlockPos neighbor : collectNeighborWaters(current, waterKeys)) {
                if (dist.containsKey(neighbor.asLong())) continue;
                dist.put(neighbor.asLong(), currentDist + 1);
                queue.add(neighbor);
            }
        }
        return dist;
    }

    private static List<BlockPos> walkCenterlineDownstream(BlockPos upstream,
                                                           Set<Long> waterKeys,
                                                           Map<Long, Integer> bankDistances,
                                                           Map<Long, Integer> downstreamGraphDist) {
        List<BlockPos> path = new ArrayList<>();
        if (!waterKeys.contains(upstream.asLong())) return path;
        if (!downstreamGraphDist.containsKey(upstream.asLong())) return path;

        BlockPos current = upstream;
        Set<Long> visited = new HashSet<>();
        int safety = 4096;
        while (current != null && safety-- > 0) {
            if (!visited.add(current.asLong())) break;
            path.add(current);

            int currentDist = downstreamGraphDist.getOrDefault(current.asLong(), Integer.MAX_VALUE);
            if (currentDist <= 0) break;

            BlockPos best = null;
            int bestScore = Integer.MIN_VALUE;
            for (BlockPos neighbor : collectNeighborWaters(current, waterKeys)) {
                if (visited.contains(neighbor.asLong())) continue;
                int neighDist = downstreamGraphDist.getOrDefault(neighbor.asLong(), Integer.MAX_VALUE);
                if (neighDist >= currentDist) continue;

                int bank = bankDistances.getOrDefault(neighbor.asLong(), 0);
                int score = bank * 6 + (currentDist - neighDist);
                if (score > bestScore) {
                    bestScore = score;
                    best = neighbor;
                }
            }
            current = best;
        }
        return path;
    }

    private static List<CenterlineSample> buildCenterlineSamples(List<BlockPos> walked,
                                                                 Map<Long, Integer> bankDistances,
                                                                 double axisX,
                                                                 double axisZ) {
        if (walked.isEmpty()) return List.of();

        List<CenterlineSample> samples = new ArrayList<>(walked.size());
        int window = 2;
        for (int i = 0; i < walked.size(); i++) {
            BlockPos cell = walked.get(i);
            int lo = Math.max(0, i - window);
            int hi = Math.min(walked.size() - 1, i + window);
            BlockPos a = walked.get(lo);
            BlockPos b = walked.get(hi);
            double dx = b.getX() - a.getX();
            double dz = b.getZ() - a.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            double dirX = len > 1e-4 ? dx / len : axisX;
            double dirZ = len > 1e-4 ? dz / len : axisZ;
            float yaw = (float) Math.toDegrees(Math.atan2(dirZ, dirX));
            int bd = Math.max(1, bankDistances.getOrDefault(cell.asLong(), 1));
            samples.add(new CenterlineSample(cell, cell.getX() + 0.5, cell.getZ() + 0.5, dirX, dirZ, yaw, bd));
        }
        return samples;
    }

    private static int nearestSampleIndex(List<CenterlineSample> samples, double x, double z) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < samples.size(); i++) {
            CenterlineSample s = samples.get(i);
            double dx = s.x() - x;
            double dz = s.z() - z;
            double d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private static Map<Long, Integer> computeBankDistances(List<BlockPos> component, List<BlockPos> bankWaters, Set<Long> waterKeys) {
        Map<Long, Integer> distances = new HashMap<>(component.size());
        Queue<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos bankWater : bankWaters) {
            distances.put(bankWater.asLong(), 1);
            queue.add(bankWater);
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int distance = distances.getOrDefault(current.asLong(), 1);
            for (BlockPos neighbor : collectNeighborWaters(current, waterKeys)) {
                if (distances.containsKey(neighbor.asLong())) continue;
                distances.put(neighbor.asLong(), distance + 1);
                queue.add(neighbor);
            }
        }

        return distances;
    }

    private static double[] computePrincipalAxis(List<BlockPos> component, double centerX, double centerZ, Map<Long, Integer> bankDistances) {
        double covXX = 0.0;
        double covXZ = 0.0;
        double covZZ = 0.0;
        double weightTotal = 0.0;

        for (BlockPos water : component) {
            double dx = water.getX() + 0.5 - centerX;
            double dz = water.getZ() + 0.5 - centerZ;
            double weight = Math.max(1.0, bankDistances.getOrDefault(water.asLong(), 1));
            covXX += dx * dx * weight;
            covXZ += dx * dz * weight;
            covZZ += dz * dz * weight;
            weightTotal += weight;
        }

        if (weightTotal <= 0.0) {
            return new double[]{1.0, 0.0};
        }

        double theta = 0.5 * Math.atan2(2.0 * covXZ, covXX - covZZ);
        double axisX = Math.cos(theta);
        double axisZ = Math.sin(theta);
        if (!Double.isFinite(axisX) || !Double.isFinite(axisZ) || (Math.abs(axisX) < 0.0001 && Math.abs(axisZ) < 0.0001)) {
            return new double[]{1.0, 0.0};
        }
        return new double[]{axisX, axisZ};
    }

    private static TangentSample tangentAtPos(RiverReach reach, double x, double z) {
        List<CenterlineSample> samples = reach.samples();
        if (samples.isEmpty()) {
            float yaw = (float) Math.toDegrees(Math.atan2(reach.axisZ(), reach.axisX()));
            return new TangentSample(x, z, reach.axisX(), reach.axisZ(), yaw);
        }
        int idx = nearestSampleIndex(samples, x, z);
        CenterlineSample s = samples.get(idx);
        return new TangentSample(s.x(), s.z(), s.dirX(), s.dirZ(), s.yaw());
    }

    private static boolean pathHasClearance(RiverReach reach, List<RiverPlanPoint> points, int requiredBankDistance) {
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).bankDistance() < requiredBankDistance) return false;
            if (i >= points.size() - 1) continue;

            RiverPlanPoint from = points.get(i);
            RiverPlanPoint to = points.get(i + 1);
            double segmentX = to.x() - from.x();
            double segmentZ = to.z() - from.z();
            for (int step = 1; step <= 4; step++) {
                double delta = step / 4.0;
                double sampleX = from.x() + segmentX * delta;
                double sampleZ = from.z() + segmentZ * delta;
                BlockPos nearest = findNearestWater(reach, sampleX, sampleZ, 2.25);
                if (nearest == null || reach.bankDistance(nearest) < requiredBankDistance) {
                    return false;
                }
            }
        }

        return true;
    }

    @Nullable
    private static BlockPos findNearestWater(RiverReach reach, double x, double z, double maxDistanceSq) {
        BlockPos best = null;
        double bestDistance = maxDistanceSq;
        for (BlockPos water : reach.waters()) {
            double distance = Mth.square(water.getX() + 0.5 - x) + Mth.square(water.getZ() + 0.5 - z);
            if (distance > bestDistance) continue;
            bestDistance = distance;
            best = water;
        }
        return best;
    }

    private static float computeTravelSpeed(RiverReach reach, List<RiverPlanPoint> points, int requiredBankDistance) {
        double averageDepth = points.stream().mapToInt(point -> reach.depth(point.water())).average().orElse(2.0);
        double speed = (0.066 + averageDepth * 0.011 + requiredBankDistance * 0.0045) * 1.2;
        return (float) Mth.clamp(speed, 0.090, 0.150);
    }

    private static boolean isBankWater(BlockPos water, Set<Long> waterKeys) {
        for (int[] offset : HORIZONTAL_NEIGHBORS) {
            boolean hasNeighbor = false;
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos check = water.offset(offset[0], dy, offset[1]);
                if (!waterKeys.contains(check.asLong())) continue;
                hasNeighbor = true;
                break;
            }
            if (!hasNeighbor) return true;
        }
        return false;
    }

    private static boolean touchesOceanBiome(ClientLevel world, BlockPos water) {
        for (BlockPos check : BlockPos.betweenClosed(water.offset(-1, 0, -1), water.offset(1, 0, 1))) {
            if (world.getBiome(check).is(BiomeTags.IS_OCEAN)) return true;
        }
        return false;
    }

    private static boolean isSolidBlock(ClientLevel world, BlockPos pos) {
        return !world.getBlockState(pos).isAir() && !world.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
    }

    private static int computeDepth(ClientLevel world, BlockPos water, int maxDepth) {
        int depth = 0;
        BlockPos.MutableBlockPos cursor = water.mutable();
        while (depth < maxDepth && world.getFluidState(cursor).is(net.minecraft.tags.FluidTags.WATER)) {
            depth++;
            cursor.move(0, -1, 0);
        }
        return depth;
    }

    private static List<BlockPos> collectNeighborWaters(BlockPos origin, Map<Long, BlockPos> waters) {
        List<BlockPos> neighbors = new ArrayList<>(12);
        for (int[] offset : HORIZONTAL_NEIGHBORS) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos neighbor = origin.offset(offset[0], dy, offset[1]);
                BlockPos mapped = waters.get(neighbor.asLong());
                if (mapped == null) continue;
                neighbors.add(mapped);
                break;
            }
        }
        return neighbors;
    }

    private static List<BlockPos> collectNeighborWaters(BlockPos origin, Set<Long> waterKeys) {
        List<BlockPos> neighbors = new ArrayList<>(12);
        for (int[] offset : HORIZONTAL_NEIGHBORS) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos neighbor = origin.offset(offset[0], dy, offset[1]);
                if (!waterKeys.contains(neighbor.asLong())) continue;
                neighbors.add(neighbor);
                break;
            }
        }
        return neighbors;
    }

    private static double projection(BlockPos water, double centerX, double centerZ, double axisX, double axisZ) {
        return projection(water.getX() + 0.5, water.getZ() + 0.5, centerX, centerZ, axisX, axisZ);
    }

    private static double projection(double x, double z, double centerX, double centerZ, double axisX, double axisZ) {
        return (x - centerX) * axisX + (z - centerZ) * axisZ;
    }

    private static BlockPos offset(BlockPos origin, double offsetX, double offsetZ) {
        return origin.offset((int) Math.round(offsetX), 0, (int) Math.round(offsetZ));
    }

    private static void addReachDebugMarkers(@Nullable List<DebugMarker> debugMarkers, RiverReach reach) {
        if (debugMarkers == null) return;

        int bankStride = Math.max(4, reach.bankWaters().size() / 18);
        for (int i = 0; i < reach.bankWaters().size(); i += bankStride) {
            BlockPos bank = reach.bankWaters().get(i);
            debugMarkers.add(new DebugMarker(bank.getX() + 0.5, bank.getY() + 1.15, bank.getZ() + 0.5, new Vector3f(0.18f, 0.45f, 0.95f), 0.9f, false, 0f, 0f, 12));
        }

        int centerStride = Math.max(1, reach.centerline().size() / 18);
        float centerYaw = (float) Math.toDegrees(Math.atan2(reach.axisZ(), reach.axisX()));
        for (int i = 0; i < reach.centerline().size(); i += centerStride) {
            BlockPos center = reach.centerline().get(i);
            debugMarkers.add(new DebugMarker(center.getX() + 0.5, center.getY() + 1.35, center.getZ() + 0.5, new Vector3f(0.2f, 0.95f, 0.35f), 0.95f, true, centerYaw, 0.16f, 16));
        }
    }

    private static void addAcceptedPathMarkers(@Nullable List<DebugMarker> debugMarkers, RiverTravelPlan plan) {
        if (debugMarkers == null) return;

        for (RiverPlanPoint point : plan.points()) {
            debugMarkers.add(new DebugMarker(point.x(), point.water().getY() + 1.35, point.z(), new Vector3f(0.15f, 0.95f, 0.95f), 0.92f, true, point.yaw(), plan.speed() * 1.7f, 18));
        }
    }

    private static void addStandingCandidateMarker(@Nullable List<DebugMarker> debugMarkers, StandingWaveCandidate candidate) {
        if (debugMarkers == null) return;

        debugMarkers.add(new DebugMarker(candidate.anchorWater().getX() + 0.5, candidate.anchorWater().getY() + 1.25, candidate.anchorWater().getZ() + 0.5, new Vector3f(1.0f, 0.82f, 0.2f), 1.1f, true, candidate.yaw(), 0.08f, 20));
    }

    private static void addRejectMarker(@Nullable List<DebugMarker> debugMarkers, BlockPos pos, RejectReason reason) {
        if (debugMarkers == null) return;

        debugMarkers.add(new DebugMarker(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, reason.color(), 1.0f, false, 0f, 0f, 16));
    }
}
