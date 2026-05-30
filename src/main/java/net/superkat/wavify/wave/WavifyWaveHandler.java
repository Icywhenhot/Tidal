package net.superkat.wavify.wave;

import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.superkat.wavify.DebugHelper;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.mixin.OptionsAccessor;
import net.superkat.wavify.particles.debug.DebugWaterParticle;
import net.superkat.wavify.particles.debug.DebugWaveMovementParticle;
import net.superkat.wavify.renderer.WaveRenderer;
import net.superkat.wavify.river.RiverFlowPlanner;
import net.superkat.wavify.scan.SitePos;
import net.superkat.wavify.scan.WaterHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * The main handler, used for spawning and handling waves, as well as ticking the world's {@link WaterHandler}
 */
public class WavifyWaveHandler {
    public final ClientLevel level;
    public WaterHandler waterHandler;
    public WaveRenderer renderer;

    public List<Wave> waves = new ObjectArrayList<>();
    // Set of BlockPos's currently being covered by waves - used for rendering wet overlay
    public Set<BlockPos> coveredBlocks = new ObjectArraySet<>();
    private List<RiverFlowPlanner.DebugMarker> riverDebugMarkers = List.of();

    // Per-area spawn cooldown for river waves: prevents two waves spawning in the same spot back-to-back.
    private static final double RIVER_SPAWN_COOLDOWN_RADIUS_SQ = 7.0 * 7.0;
    private static final long RIVER_SPAWN_COOLDOWN_TICKS = 20L;
    // Each entry: [x, z, expiryTick]
    private final List<double[]> recentRiverSpawns = new ArrayList<>();

    public boolean nearbyChunksLoaded = false;

    public WavifyWaveHandler(ClientLevel level) {
        this.level = level;
        this.waterHandler = new WaterHandler(this, level);
        this.renderer = new WaveRenderer(this, level);
    }

    public void reloadNearbyChunks() {
        this.nearbyChunksLoaded = false;
    }

    /**
     * General tick method for all tick-related things EXCEPT the actual waves.
     */
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        assert player != null;

        if (!this.nearbyChunksLoaded) {
            this.nearbyChunksLoaded = nearbyChunksLoaded(player);
        }

        this.waterHandler.tick();
        wavifyTick();

        if (DebugHelper.debug()) {
            debugTick(client, player);
        }

    }

    public void render(MultiBufferSource bufferSource, RenderType layer) {
        this.renderer.render(bufferSource, layer);
    }

    /**
     * Tick method for waves
     */
    public void wavifyTick() {
        if (!this.level.tickRateManager().runsNormally()) return;
        double time = this.level.getGameTime();
        if (time % 80 == 0) {
            spawnAllWaves();
        } else if (WavifyConfig.enableRiverWaves && time % 40 == 0) {
            spawnRiverWavesNearPlayer();
        }

        boolean updateCoveredBlocks = time % 10 == 0;
        ObjectArraySet<BlockPos> updatedCovered = new ObjectArraySet<>();

        for (Iterator<Wave> iterator = waves.iterator(); iterator.hasNext(); ) {
            Wave wave = iterator.next();
            wave.tick();

            if (wave.isDead()) {
                iterator.remove();
            } else if (updateCoveredBlocks) {
                updatedCovered.addAll(wave.getCoveredBlocks());
            }
        }

        if (updateCoveredBlocks) this.coveredBlocks = updatedCovered;
    }

    public void spawnAllWaves() {
        int distFromShore = WavifyConfig.spawnDistance;
        WavifyConfig.waveDistFromShore = distFromShore; // keep legacy field in sync
        int chunkRadius = WavifyConfig.chunkRadius - 2;

        ChunkPos playerChunk = Minecraft.getInstance().player.chunkPosition();
        ChunkPos start = new ChunkPos(playerChunk.x + chunkRadius, playerChunk.z + chunkRadius);
        ChunkPos end = new ChunkPos(playerChunk.x - chunkRadius, playerChunk.z - chunkRadius);
        Set<BlockPos> waterBlocks = ChunkPos.rangeClosed(start, end)
                .map(chunkPos -> this.waterHandler.getWaterCacheAtDistance(chunkPos, distFromShore))
                .filter(map -> map != null)
                .flatMap(Collection::stream)
                .filter(water -> !isRiverBiomeWater(water))
                .collect(ObjectArraySet::new, Set::add, Set::addAll);
        if (!waterBlocks.isEmpty()) spawnWaves(waterBlocks);
        if (WavifyConfig.enableRiverWaves) {
            spawnRiverWaves(playerChunk, chunkRadius);
        } else {
            this.riverDebugMarkers = List.of();
        }

        if (DebugHelper.debug()) {
            if (DebugHelper.holdingSpyglass()) debugWaveParticles(waterBlocks);
            if (DebugHelper.offhandClock()) {
                for (BlockPos water : waterBlocks) {
                    Vec3 pos = water.getCenter();
                    this.level.addParticle(ParticleTypes.END_ROD, pos.x(), pos.y() + 2.5, pos.z(), 0, 0, 0);
                }
            }
        }
    }

    public void spawnWaves(Set<BlockPos> waterBlocks) {
        Set<BlockPos> visited = Sets.newHashSet();
        int spawned = 0;

        for (BlockPos water : waterBlocks) {
            if (visited.contains(water)) continue;
            SitePos site = this.waterHandler.getSiteForPos(water);
            if (site == null || !site.yawCalculated) continue;
            if (site.xList.size() < 50) continue;

            float yaw = site.getYaw();
            Set<BlockPos> connected = findConnected(water, yaw, waterBlocks, visited);
            visited.addAll(connected);

            boolean bigWave = site.xList.size() >= 100;

            spawned++;
            float yOffset = Mth.sin(spawned) / 16f + 0.65f;
            BlockPos spawnPos = connected.stream().sorted(Comparator.comparingInt(Vec3i::getZ)).toList().get(connected.size() / 2).offset(0, 1, 0);

            BlockPos beneath = spawnPos.offset(0, -1, 0);
            if (this.level.isEmptyBlock(beneath) || !this.level.getBlockState(beneath).getFluidState().isSource()) continue;

            if (isRiverBiomeWater(spawnPos)) continue;

            boolean oceanConnected = isOceanConnectedWave(spawnPos, yaw);
            if (!oceanConnected) continue;
            if (!isShoreWavePathSafe(spawnPos, yaw)) continue;

            Wave wave = new Wave(this.level, spawnPos, yaw, yOffset, bigWave);
            int width = (int) Mth.clamp(connected.size() * 1.5, 1, 3);
            wave.setWidth(width);
            wave.offsetVertical(-0.5f);
            this.waves.add(wave);
        }
    }

    private void spawnRiverWavesNearPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        spawnRiverWaves(player.chunkPosition(), WavifyConfig.chunkRadius - 2);
    }

    private boolean isOceanConnectedWave(BlockPos surfaceSpawn, float yaw) {
        double x = surfaceSpawn.getX() + 0.5;
        double z = surfaceSpawn.getZ() + 0.5;
        double rad = Math.toRadians(yaw);
        double dirX = Math.cos(rad);
        double dirZ = Math.sin(rad);
        double speed = 0.115;
        int preferredWaterY = surfaceSpawn.getY() - 1;
        boolean sawOceanWater = this.level.getBiome(surfaceSpawn).is(BiomeTags.IS_OCEAN);

        for (int step = 0; step < 96; step++) {
            x -= dirX * speed;
            z -= dirZ * speed;

            BlockPos water = findSurfaceWaterAtPathPoint(x, z, preferredWaterY);
            if (water == null) return sawOceanWater;
            preferredWaterY = water.getY();

            if (isRiverBiomeWater(water)) return false;
            if (this.level.getBiome(water).is(BiomeTags.IS_OCEAN)) {
                sawOceanWater = true;
            }
        }

        return sawOceanWater;
    }

    private boolean isShoreWavePathSafe(BlockPos surfaceSpawn, float yaw) {
        double x = surfaceSpawn.getX() + 0.5;
        double z = surfaceSpawn.getZ() + 0.5;
        double rad = Math.toRadians(yaw);
        double dirX = Math.cos(rad);
        double dirZ = Math.sin(rad);
        double speed = 0.115;
        int preferredWaterY = surfaceSpawn.getY() - 1;

        for (int step = 0; step < 80; step++) {
            x += dirX * speed;
            z += dirZ * speed;

            BlockPos water = findSurfaceWaterAtPathPoint(x, z, preferredWaterY);
            if (water == null) return true;
            preferredWaterY = water.getY();

            if (isRiverBiomeWater(water)) return false;
        }

        return true;
    }

    private BlockPos findSurfaceWaterAtPathPoint(double x, double z, int preferredWaterY) {
        int baseX = Mth.floor(x);
        int baseZ = Mth.floor(z);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int y = preferredWaterY + 1; y >= preferredWaterY - 2; y--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos check = new BlockPos(baseX + dx, y, baseZ + dz);
                    if (!WavifyWaveHandler.posIsWater(this.level, check)) continue;
                    if (!this.level.getBlockState(check.above()).isAir()) continue;

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

    private boolean isRiverBiomeWater(BlockPos pos) {
        if (this.level.getBiome(pos).is(BiomeTags.IS_RIVER)) return true;

        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
            if (this.level.getBiome(check).is(BiomeTags.IS_RIVER)) return true;
        }

        return false;
    }

    public void spawnRiverWaves(ChunkPos playerChunk, int chunkRadius) {
        List<RiverFlowPlanner.DebugMarker> debugMarkers = DebugHelper.debug() ? new ArrayList<>() : null;
        List<RiverFlowPlanner.RiverReach> reaches = RiverFlowPlanner.buildReaches(this.level, this.waterHandler, playerChunk, chunkRadius, debugMarkers);
        if (reaches.isEmpty()) {
            this.riverDebugMarkers = debugMarkers == null ? List.of() : List.copyOf(debugMarkers);
            return;
        }

        spawnMovingRiverWaves(reaches, debugMarkers);
        spawnStandingRiverWaves(reaches, debugMarkers);
        this.riverDebugMarkers = debugMarkers == null ? List.of() : List.copyOf(debugMarkers);
    }

    private void spawnMovingRiverWaves(List<RiverFlowPlanner.RiverReach> reaches, @Nullable List<RiverFlowPlanner.DebugMarker> debugMarkers) {
        long now = this.level.getGameTime();
        this.recentRiverSpawns.removeIf(entry -> entry[2] <= now);

        for (RiverFlowPlanner.RiverReach reach : reaches) {
            int existing = 0;
            RiverWave reference = null;
            double referenceDistanceSq = Double.MAX_VALUE;
            for (Wave wave : this.waves) {
                if (!(wave instanceof RiverWave riverWave) || wave instanceof StandingRiverWave) continue;
                if (riverWave.getReachId() != reach.id()) continue;
                existing++;

                double dx = riverWave.x - (reach.downstreamEnd().getX() + 0.5);
                double dz = riverWave.z - (reach.downstreamEnd().getZ() + 0.5);
                double distanceSq = dx * dx + dz * dz;
                if (distanceSq < referenceDistanceSq) {
                    referenceDistanceSq = distanceSq;
                    reference = riverWave;
                }
            }

            int maxMoving = Mth.clamp((int) Math.round(reach.reachLength() / 18.0), 2, 4);
            if (existing >= maxMoving) continue;

            double chance = Mth.clamp(WavifyConfig.riverWaveFrequency + reach.reachLength() / 140.0, 0.58, 0.97);
            if (this.level.getRandom().nextDouble() > chance) continue;

            RiverFlowPlanner.FlowReference flowReference = reference == null ? null
                    : new RiverFlowPlanner.FlowReference(reference.x, reference.z, reference.getFlowDirX(), reference.getFlowDirZ());
            RiverFlowPlanner.RiverTravelPlan plan = RiverFlowPlanner.buildTravelPlan(this.level, reach, flowReference, this.level.getRandom(), debugMarkers);
            if (plan == null) continue;
            double spawnX = plan.spawnSurface().getX() + 0.5;
            double spawnZ = plan.spawnSurface().getZ() + 0.5;
            if (hasRiverWaveNear(spawnX, spawnZ, 5.5, reach.id())) continue;
            if (recentRiverSpawnBlocks(spawnX, spawnZ)) continue;

            this.waves.add(new RiverWave(this.level, plan));
            this.recentRiverSpawns.add(new double[]{spawnX, spawnZ, now + RIVER_SPAWN_COOLDOWN_TICKS});
        }
    }

    private boolean recentRiverSpawnBlocks(double x, double z) {
        for (double[] entry : this.recentRiverSpawns) {
            double dx = entry[0] - x;
            double dz = entry[1] - z;
            if (dx * dx + dz * dz <= RIVER_SPAWN_COOLDOWN_RADIUS_SQ) return true;
        }
        return false;
    }

    private void spawnStandingRiverWaves(List<RiverFlowPlanner.RiverReach> reaches, @Nullable List<RiverFlowPlanner.DebugMarker> debugMarkers) {
        for (RiverFlowPlanner.RiverReach reach : reaches) {
            Set<Long> occupiedAnchors = new HashSet<>();
            int existingStanding = 0;
            for (Wave wave : this.waves) {
                if (!(wave instanceof StandingRiverWave standingWave)) continue;
                if (standingWave.getReachId() != reach.id()) continue;
                existingStanding++;
                occupiedAnchors.add(standingWave.getBlockPos().below().asLong());
            }

            if (existingStanding >= 3) continue;
            if (this.level.getRandom().nextDouble() > WavifyConfig.standingRiverWaveFrequency) continue;

            RiverFlowPlanner.StandingWaveCandidate candidate = RiverFlowPlanner.findStandingWaveCandidate(this.level, reach, occupiedAnchors, this.level.getRandom(), debugMarkers);
            if (candidate == null) continue;
            if (hasStandingWaveNear(candidate.anchorWater().getX() + 0.5, candidate.anchorWater().getZ() + 0.5, 6.0, reach.id())) continue;

            for (int i = 0; i < candidate.trainLength(); i++) {
                double offsetX = candidate.anchorWater().getX() + 0.5 + candidate.dirX() * (i * 1.9);
                double offsetZ = candidate.anchorWater().getZ() + 0.5 + candidate.dirZ() * (i * 1.9);
                BlockPos anchor = findNearestReachWater(reach, offsetX, offsetZ, 4.5, true, candidate.dirX(), candidate.dirZ());
                if (anchor == null) continue;
                if (hasStandingWaveNear(anchor.getX() + 0.5, anchor.getZ() + 0.5, 1.6, reach.id())) continue;

                occupiedAnchors.add(anchor.asLong());
                this.waves.add(new StandingRiverWave(this.level, candidate, anchor, i));
            }
        }
    }

    @Nullable
    private BlockPos findNearestReachWater(RiverFlowPlanner.RiverReach reach, double x, double z, double maxDistanceRadius, boolean shallowOnly, double dirX, double dirZ) {
        BlockPos best = null;
        double bestDistance = maxDistanceRadius * maxDistanceRadius;
        for (BlockPos water : reach.waters()) {
            if (shallowOnly && (reach.depth(water) < 1 || reach.depth(water) > 2)) continue;
            if (reach.bankDistance(water) < 2) continue;
            if (!this.level.getBlockState(water.above()).isAir()) continue;
            if (shallowOnly && !RiverFlowPlanner.isStandingAnchorStable(this.level, reach, water, dirX, dirZ)) continue;

            double distance = Mth.square(water.getX() + 0.5 - x) + Mth.square(water.getZ() + 0.5 - z);
            if (distance > bestDistance) continue;
            bestDistance = distance;
            best = water;
        }
        return best;
    }

    private boolean hasRiverWaveNear(double x, double z, double radius, long reachId) {
        double radiusSq = radius * radius;
        double routeRadiusSq = 4.0 * 4.0;
        for (Wave wave : this.waves) {
            if (!(wave instanceof RiverWave riverWave) || wave instanceof StandingRiverWave) continue;
            if (riverWave.getReachId() != reachId) continue;
            double dx = wave.x - x;
            double dz = wave.z - z;
            if (dx * dx + dz * dz <= radiusSq) return true;

            // Also reject if the spawn falls anywhere along the existing wave's remaining route.
            List<RiverFlowPlanner.RiverPlanPoint> plan = riverWave.plan;
            for (int i = riverWave.planIndex; i < plan.size(); i++) {
                RiverFlowPlanner.RiverPlanPoint point = plan.get(i);
                double pdx = point.x() - x;
                double pdz = point.z() - z;
                if (pdx * pdx + pdz * pdz <= routeRadiusSq) return true;
            }
        }
        return false;
    }

    private boolean hasStandingWaveNear(double x, double z, double radius, long reachId) {
        double radiusSq = radius * radius;
        for (Wave wave : this.waves) {
            if (!(wave instanceof StandingRiverWave standingWave)) continue;
            if (standingWave.getReachId() != reachId) continue;
            double dx = wave.x - x;
            double dz = wave.z - z;
            if (dx * dx + dz * dz <= radiusSq) return true;
        }
        return false;
    }

    public Set<BlockPos> findConnected(BlockPos start, float yaw, Set<BlockPos> waterBlocks, Set<BlockPos> ignoreSet) {
        int maxLength = 3;
        Set<BlockPos> connected = Sets.newHashSet();
        Queue<BlockPos> stack = Queues.newArrayDeque();
        stack.add(start);

        for (int i = 0; i < maxLength; i++) {
            BlockPos water = stack.poll();
            connected.add(water);
            for (BlockPos check : BlockPos.betweenClosed(water.offset(-1, 0, -1), water.offset(1, 0, 1))) {
                if (water == check) continue;
                if (ignoreSet.contains(check)) continue;
                if (!waterBlocks.contains(check)) continue;

                SitePos site = this.waterHandler.getSiteForPos(check);
                if (site == null || !site.yawCalculated || site.xList.size() < 50) continue;
                if (Math.abs(site.yaw - yaw) > 15) continue;
                stack.add(new BlockPos(check));
            }

            if (stack.isEmpty()) break;
        }
        return connected;
    }

    public void debugWaveParticles(Set<BlockPos> waterBlocks) {
        Vector3f color = new Vector3f(1f, 1f, 1f); //activates the movement particle's custom colors
//        Vector3f color = new Vector3f(0.75f, 0.75f, 0.75f); //deactivates the custom colors
        boolean farParticles = false;

        for (BlockPos water : waterBlocks) {
            SitePos site = this.waterHandler.getSiteForPos(water);
            if (site == null || !site.yawCalculated) continue;
//            if(site.xList.size() < 50) continue;

            DebugWaveMovementParticle.DebugWaveMovementParticleEffect particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(
                    color,
                    1f,
                    site.getYaw(),
                    0.3f,
                    20);
            this.level.addParticle(particleEffect, farParticles, water.getX(), water.getY() + 2, water.getZ(), 0, 0, 0);
        }
    }

    public List<Wave> getWaves() {
        return this.waves;
    }

    // This isn't perfect, but its close enough I suppose
    public boolean nearbyChunksLoaded(LocalPlayer player) {
        if (nearbyChunksLoaded) return true;
        int chunkRadius = getChunkRadius();

        // using LevelChunk instead of chunk because it has "isEmpty" method
        // could use chunk instanceof EmptyChunk instead, but this felt better
        int chunkX = player.chunkPosition().x;
        int chunkZ = player.chunkPosition().z;
        int chunkRadiusReduced = chunkRadius - (chunkRadius / 3);

        List<LevelChunk> checkChunks = List.of(
                this.level.getChunk(chunkX + chunkRadius, chunkZ),
                this.level.getChunk(chunkX - chunkRadius, chunkZ),
                this.level.getChunk(chunkX, chunkZ + chunkRadius),
                this.level.getChunk(chunkX, chunkZ - chunkRadius),
                this.level.getChunk(chunkX + (chunkRadiusReduced), chunkZ + (chunkRadiusReduced)),
                this.level.getChunk(chunkX - (chunkRadiusReduced), chunkZ + (chunkRadiusReduced)),
                this.level.getChunk(chunkX - (chunkRadiusReduced), chunkZ - (chunkRadiusReduced)),
                this.level.getChunk(chunkX + (chunkRadiusReduced), chunkZ - (chunkRadiusReduced))
        );
        return checkChunks.stream().noneMatch(LevelChunk::isEmpty);
        // alternative way - takes slightly longer
//        return Minecraft.getInstance().worldRenderer.isTerrainRenderComplete();
    }

    // Gets all loaded nearby chunks - created using ClientChunkManager & ClientChunkManager.ClientChunkMap
    // Unused right now, but could be helpful for making the WaterBodyHandler's scanners empty out when a scanner is done
    public Set<ChunkPos> getNearbyChunkPos() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ChunkPos playerPos = player.chunkPosition();
        int playerX = playerPos.x;
        int playerZ = playerPos.z;

        int radius = getLoadedChunkRadius();
        ChunkPos start = new ChunkPos(playerX + radius, playerZ + radius);
        ChunkPos end = new ChunkPos(playerX - radius, playerZ - radius);

        Set<ChunkPos> loadedChunks = Sets.newHashSet();
        for (ChunkPos chunkPos : ChunkPos.rangeClosed(start, end).toList()) {
            LevelChunk chunk = this.level.getChunk(chunkPos.x, chunkPos.z);
            if (chunk.isEmpty()) continue;
            loadedChunks.add(chunkPos);
        }

        return loadedChunks;
    }

    /**
     * @return The wave chunk radius - pulls from either the Wavify config or the server's render distance(whichever one is smaller).
     */
    public int getChunkRadius() {
        Minecraft client = Minecraft.getInstance();
        int configRadius = WavifyConfig.chunkRadius;
        int serverRadius = ((OptionsAccessor) client.options).wavify$getServerRenderDistance();

        return Math.min(configRadius, serverRadius);
    }

    /**
     * @return The loaded chunk radius - used for figuring out all loaded chunks on the client.
     */
    public int getLoadedChunkRadius() {
        Minecraft client = Minecraft.getInstance();
        int loadRadius = ((OptionsAccessor) client.options).wavify$getServerRenderDistance();
        return Math.max(2, loadRadius) + 3;
    }

    public void debugTick(Minecraft client, LocalPlayer player) {
        if (DebugHelper.offhandSpyglass()) {
            renderRiverDebugMarkers(player);
        }

        // show water direction of water blocks
        if (DebugHelper.holdingCompass() || DebugHelper.offhandCompass()) {
            if (!this.waterHandler.built) return;

            ChunkPos playerChunk = player.chunkPosition();
            if (DebugHelper.offhandCompass()) {
                if (client.level.getGameTime() % 5 != 0) return;
                int radius = 2;
                ChunkPos start = new ChunkPos(playerChunk.x + radius, playerChunk.z + radius);
                ChunkPos end = new ChunkPos(playerChunk.x - radius, playerChunk.z - radius);
                for (ChunkPos chunkPos : ChunkPos.rangeClosed(start, end).toList()) {
                    debugChunkDirectionParticles(chunkPos.toLong(), true);
                }
            } else {
                debugChunkDirectionParticles(playerChunk.toLong(), false);
            }

        }

        // print water direction's yaw
        if (DebugHelper.usingSpyglass()) {
            if (client.level.getGameTime() % 20 != 0) return;

            BlockPos playerPos = player.blockPosition();

            List<BlockPos> scannedBlocks = this.waterHandler.waterCache.values().stream().flatMap(map -> map.keySet().stream()).toList();
            if (scannedBlocks.contains(playerPos)) {
                long chunkPosL = ChunkPos.asLong(playerPos);
                SitePos site = this.waterHandler.waterCache.get(chunkPosL).get(playerPos);
//                System.out.println(this.level.getBiome(site.getPos()).is(BiomeTags.IS_RIVER));
                System.out.println(site.xList.size());
            }
        }
    }

    private void renderRiverDebugMarkers(LocalPlayer player) {
        if (this.riverDebugMarkers.isEmpty()) return;

        for (RiverFlowPlanner.DebugMarker marker : this.riverDebugMarkers) {
            if (!new Vec3(marker.x(), marker.y(), marker.z()).closerThan(new Vec3(player.getX(), player.getY(), player.getZ()), 96.0)) continue;

            if (marker.arrow()) {
                DebugWaveMovementParticle.DebugWaveMovementParticleEffect effect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(
                        marker.color(),
                        marker.scale(),
                        marker.yaw(),
                        marker.speed(),
                        marker.lifetime()
                );
                this.level.addParticle(effect, false, marker.x(), marker.y(), marker.z(), 0, 0, 0);
            } else {
                DebugWaterParticle.DebugWaterParticleEffect effect = new DebugWaterParticle.DebugWaterParticleEffect(marker.color(), marker.scale());
                this.level.addParticle(effect, marker.x(), marker.y(), marker.z(), 0, 0, 0);
            }
        }
    }

    public void debugChunkDirectionParticles(long chunkPosL, boolean farParticles) {
        Vector3f color = new Vector3f(1f, 1f, 1f); //activates the movement particle's custom colors
//        Vector3f color = new Vector3f(0.75f, 0.75f, 0.75f); //deactivates the custom colors

        Map<BlockPos, SitePos> map = this.waterHandler.waterCache.get(chunkPosL);
        if (map == null) return;

        for (Map.Entry<BlockPos, SitePos> entry : map.entrySet()) {
            BlockPos pos = entry.getKey();
            SitePos sitePos = entry.getValue();
            if (sitePos == null || !sitePos.yawCalculated) continue;
            DebugWaveMovementParticle.DebugWaveMovementParticleEffect particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(
                    color,
                    1f,
                    sitePos.getYaw(),
                    0.3f,
                    20);
            this.level.addParticle(particleEffect, farParticles, pos.getX(), pos.getY() + 2, pos.getZ(), 0, 0, 0);
        }
    }

    /**
     * @return A random RandomSource with a randomly generated random RandomSeed seed.
     */
    public static RandomSource getRandom() {
        return RandomSource.create();
    }

    /**
     * @return A random with a seed that will, most likely, be synced between clients despite being client side. Lag may cause a small issue, but should be rare.
     */
    public static RandomSource getSyncedRandom() {
        long time = Minecraft.getInstance().level.getGameTime();
        long random = 5L * Math.round(time / 5f); // math.ceil instead?
        return RandomSource.create(random);
    }

    /**
     * Check if a BlockPos is water or is waterlogged
     *
     * @param world World to check in
     * @param pos   BlockPos to check
     * @return If the BlockPos is water or waterlogged
     */
    public static boolean posIsWater(ClientLevel level, BlockPos pos) {
        FluidState state = level.getFluidState(pos);
        return state.is(FluidTags.WATER);
    }

    public static boolean stateIsWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

}
