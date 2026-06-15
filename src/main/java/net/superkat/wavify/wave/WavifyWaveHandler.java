package net.superkat.wavify.wave;

import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.WorldChunk;
import net.superkat.wavify.DebugHelper;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.particles.debug.DebugWaterParticle;
import net.superkat.wavify.particles.debug.DebugWaveMovementParticle;
import net.superkat.wavify.renderer.WaveRenderer;
import net.superkat.wavify.river.RiverFlow;
import net.superkat.wavify.river.RiverFlowField;
import net.superkat.wavify.scan.SitePos;
import net.superkat.wavify.scan.WaterHandler;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * The main handler, used for spawning and handling waves, as well as ticking the world's {@link WaterHandler}
 */
public class WavifyWaveHandler {
    public final ClientWorld world;
    public WaterHandler waterHandler;
    public WaveRenderer renderer;

    public List<Wave> waves = new ObjectArrayList<>();
    // Set of BlockPos's currently being covered by waves - used for rendering wet overlay
    public Set<BlockPos> coveredBlocks = new ObjectArraySet<>();
    private List<RiverFlow.DebugMarker> riverDebugMarkers = List.of();
    private final RiverFlowField riverFlow = new RiverFlowField();

    // Near-player blue-noise scatter parameters for river waves. River waves are small and only read up
    // close, so we only ever consider water within a configurable radius of the player - cost is
    // independent of how large the river actually is. The radius itself comes from
    // WavifyConfig.riverWaveSpawnRadius; the chunk radius is derived from it.
    private static final double RIVER_MIN_SPACING_SQ = 5.0 * 5.0;
    /** Water blocks required to the nearest bank on each side before a river wave may spawn there. Keeps
     *  waves off the very edge, where the flow direction is least reliable and they jitter. */
    private static final int SPAWN_BANK_MARGIN = 1;

    public boolean nearbyChunksLoaded = false;

    public WavifyWaveHandler(ClientWorld world) {
        this.world = world;
        this.waterHandler = new WaterHandler(this, world);
        this.renderer = new WaveRenderer(this, world);
    }

    public void reloadNearbyChunks() {
        this.nearbyChunksLoaded = false;
    }

    /**
     * General tick method for all tick-related things EXCEPT the actual waves.
     */
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
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

    public void render(BufferBuilder buffer, WorldRenderContext context) {
        this.renderer.render(buffer, context);
    }

    /**
     * Tick method for waves
     */
    public void wavifyTick() {
        if (!this.world.getTickManager().shouldTick()) return;
        double time = this.world.getTime();
        if (WavifyConfig.enableOceanWaves && time % 80 == 0) {
            spawnAllWaves();
        }
        if (WavifyConfig.enableRiverWaves) {
            ClientPlayerEntity riverPlayer = MinecraftClient.getInstance().player;
            if (riverPlayer != null) {
                this.riverFlow.ensureBuilt(this.world, this.waterHandler, riverPlayer.getX(), riverPlayer.getZ(), (long) time);
                if (time % 40 == 0) spawnRiverWavesNearPlayer();
            }
        } else {
            this.riverDebugMarkers = List.of();
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

        ChunkPos playerChunk = MinecraftClient.getInstance().player.getChunkPos();
        ChunkPos start = new ChunkPos(playerChunk.x + chunkRadius, playerChunk.z + chunkRadius);
        ChunkPos end = new ChunkPos(playerChunk.x - chunkRadius, playerChunk.z - chunkRadius);
        Set<BlockPos> waterBlocks = ChunkPos.stream(start, end)
                .map(chunkPos -> this.waterHandler.getWaterCacheAtDistance(chunkPos, distFromShore))
                .filter(map -> map != null)
                .flatMap(Collection::stream)
                .filter(water -> !isRiverBiomeWater(water))
                .collect(ObjectArraySet::new, Set::add, Set::addAll);
        if (!waterBlocks.isEmpty()) spawnWaves(waterBlocks);

        if (DebugHelper.debug()) {
            if (DebugHelper.holdingSpyglass()) debugWaveParticles(waterBlocks);
            if (DebugHelper.offhandClock()) {
                for (BlockPos water : waterBlocks) {
                    Vec3d pos = water.toCenterPos();
                    this.world.addParticleClient(ParticleTypes.END_ROD, pos.getX(), pos.getY() + 2.5, pos.getZ(), 0, 0, 0);
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
            float yOffset = MathHelper.sin(spawned) / 16f + 0.65f;
            BlockPos spawnPos = connected.stream().sorted(Comparator.comparingInt(Vec3i::getZ)).toList().get(connected.size() / 2).add(0, 1, 0);

            BlockPos beneath = spawnPos.add(0, -1, 0);
            if (world.isAir(beneath) || !world.getBlockState(beneath).getFluidState().isStill()) continue;

            if (isRiverBiomeWater(spawnPos)) continue;

            boolean oceanConnected = isOceanConnectedWave(spawnPos, yaw);
            if (!oceanConnected) continue;
            if (!isShoreWavePathSafe(spawnPos, yaw)) continue;

            Wave wave = new Wave(this.world, spawnPos, yaw, yOffset, bigWave);
            int width = (int) MathHelper.clamp(connected.size() * 1.5, 1, 3);
            wave.setWidth(width);
            this.waves.add(wave);
        }
    }

    private boolean isOceanConnectedWave(BlockPos surfaceSpawn, float yaw) {
        double x = surfaceSpawn.getX() + 0.5;
        double z = surfaceSpawn.getZ() + 0.5;
        double rad = Math.toRadians(yaw);
        double dirX = Math.cos(rad);
        double dirZ = Math.sin(rad);
        double speed = 0.115;
        int preferredWaterY = surfaceSpawn.getY() - 1;
        boolean sawOceanWater = this.world.getBiome(surfaceSpawn).isIn(BiomeTags.IS_OCEAN);

        for (int step = 0; step < 96; step++) {
            x -= dirX * speed;
            z -= dirZ * speed;

            BlockPos water = findSurfaceWaterAtPathPoint(x, z, preferredWaterY);
            if (water == null) return sawOceanWater;
            preferredWaterY = water.getY();

            if (isRiverBiomeWater(water)) return false;
            if (this.world.getBiome(water).isIn(BiomeTags.IS_OCEAN)) {
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
        int baseX = MathHelper.floor(x);
        int baseZ = MathHelper.floor(z);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int y = preferredWaterY + 1; y >= preferredWaterY - 2; y--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos check = new BlockPos(baseX + dx, y, baseZ + dz);
                    if (!WavifyWaveHandler.posIsWater(this.world, check)) continue;
                    if (!this.world.getBlockState(check.up()).isAir()) continue;

                    double dist = MathHelper.square(check.getX() + 0.5 - x) + MathHelper.square(check.getZ() + 0.5 - z);
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
        if (this.world.getBiome(pos).isIn(BiomeTags.IS_RIVER)) return true;

        for (BlockPos check : BlockPos.iterate(pos.add(-1, 0, -1), pos.add(1, 0, 1))) {
            if (this.world.getBiome(check).isIn(BiomeTags.IS_RIVER)) return true;
        }

        return false;
    }

    /**
     * Blue-noise scatter of river waves across river water near the player. No flood-fill, no reach
     * building, no accept/reject gate: every chosen spot becomes a visible wave, so coverage is uniform
     * and nothing is ever silently discarded. Cost depends only on nearby water and the density config,
     * never on the size of the river.
     */
    private void spawnRiverWavesNearPlayer() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        double px = player.getX();
        double pz = player.getZ();
        ChunkPos playerChunk = player.getChunkPos();

        double spawnRadius = WavifyConfig.riverWaveSpawnRadius;
        double spawnRadiusSq = spawnRadius * spawnRadius;
        int spawnChunkRadius = MathHelper.ceil(spawnRadius / 16.0);

        List<BlockPos> candidates = new ArrayList<>();
        for (int cdx = -spawnChunkRadius; cdx <= spawnChunkRadius; cdx++) {
            for (int cdz = -spawnChunkRadius; cdz <= spawnChunkRadius; cdz++) {
                long chunkPosL = new ChunkPos(playerChunk.x + cdx, playerChunk.z + cdz).toLong();
                Set<BlockPos> waters = this.waterHandler.waters.get(chunkPosL);
                if (waters == null || waters.isEmpty()) continue;

                for (BlockPos water : waters) {
                    double dx = water.getX() + 0.5 - px;
                    double dz = water.getZ() + 0.5 - pz;
                    if (dx * dx + dz * dz > spawnRadiusSq) continue;
                    if (!isRiverBiomeWater(water)) continue;
                    if (!posIsWater(this.world, water)) continue;
                    if (!this.world.getBlockState(water.up()).isAir()) continue;
                    candidates.add(water);
                }
            }
        }

        if (candidates.isEmpty()) {
            this.riverDebugMarkers = List.of();
            return;
        }

        List<double[]> taken = new ArrayList<>();
        for (Wave wave : this.waves) {
            if (wave instanceof RiverWave riverWave) {
                taken.add(new double[]{riverWave.x, riverWave.z});
            }
        }

        int target = (int) Math.round(candidates.size() / 100.0 * WavifyConfig.riverWaveDensity);
        target = MathHelper.clamp(target, 1, 48);
        int toSpawn = target - taken.size();
        if (toSpawn <= 0) {
            this.riverDebugMarkers = List.of();
            return;
        }

        Random random = this.world.getRandom();
        int spawned = 0;
        int attempts = 0;
        int maxAttempts = toSpawn * 8 + 16;

        while (spawned < toSpawn && attempts++ < maxAttempts) {
            BlockPos water = candidates.get(random.nextInt(candidates.size()));
            double cx = water.getX() + 0.5;
            double cz = water.getZ() + 0.5;
            if (tooCloseToExistingRiverWave(taken, cx, cz)) continue;

            RiverFlow.Flow flow = this.riverFlow.flowAt(cx, cz);
            if (flow == null) continue;
            // Don't spawn against a bank - that's where the flow is least reliable and waves jitter.
            if (RiverFlow.bankClearance(this.world, cx, cz, water.getY(), flow.dirX(), flow.dirZ(), 4) < SPAWN_BANK_MARGIN) continue;

            this.waves.add(new RiverWave(this.world, water, this.riverFlow, flow.dirX(), flow.dirZ(), (float) WavifyConfig.riverWaveTravelBlocks));
            taken.add(new double[]{cx, cz});
            spawned++;
        }

        if (DebugHelper.debug()) {
            List<RiverFlow.DebugMarker> debugMarkers = new ArrayList<>();
            this.riverFlow.addDebugMarkers(this.world, debugMarkers, MathHelper.floor(player.getY()));
            this.riverDebugMarkers = List.copyOf(debugMarkers);
        } else {
            this.riverDebugMarkers = List.of();
        }
    }

    private boolean tooCloseToExistingRiverWave(List<double[]> taken, double x, double z) {
        for (double[] entry : taken) {
            double dx = entry[0] - x;
            double dz = entry[1] - z;
            if (dx * dx + dz * dz <= RIVER_MIN_SPACING_SQ) return true;
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
            for (BlockPos check : BlockPos.iterate(water.add(-1, 0, -1), water.add(1, 0, 1))) {
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
            this.world.addParticleClient(particleEffect, farParticles, false, water.getX(), water.getY() + 2, water.getZ(), 0, 0, 0);
        }
    }

    public List<Wave> getWaves() {
        return this.waves;
    }

    // This isn't perfect, but its close enough I suppose
    public boolean nearbyChunksLoaded(ClientPlayerEntity player) {
        if (nearbyChunksLoaded) return true;
        int chunkRadius = getChunkRadius();

        // using WorldChunk instead of chunk because it has "isEmpty" method
        // could use chunk instanceof EmptyChunk instead, but this felt better
        int chunkX = player.getChunkPos().x;
        int chunkZ = player.getChunkPos().z;
        int chunkRadiusReduced = chunkRadius - (chunkRadius / 3);

        List<WorldChunk> checkChunks = List.of(
                world.getChunk(chunkX + chunkRadius, chunkZ),
                world.getChunk(chunkX - chunkRadius, chunkZ),
                world.getChunk(chunkX, chunkZ + chunkRadius),
                world.getChunk(chunkX, chunkZ - chunkRadius),
                world.getChunk(chunkX + (chunkRadiusReduced), chunkZ + (chunkRadiusReduced)),
                world.getChunk(chunkX - (chunkRadiusReduced), chunkZ + (chunkRadiusReduced)),
                world.getChunk(chunkX - (chunkRadiusReduced), chunkZ - (chunkRadiusReduced)),
                world.getChunk(chunkX + (chunkRadiusReduced), chunkZ - (chunkRadiusReduced))
        );
        return checkChunks.stream().noneMatch(WorldChunk::isEmpty);
        // alternative way - takes slightly longer
//        return MinecraftClient.getInstance().worldRenderer.isTerrainRenderComplete();
    }

    // Gets all loaded nearby chunks - created using ClientChunkManager & ClientChunkManager.ClientChunkMap
    // Unused right now, but could be helpful for making the WaterBodyHandler's scanners empty out when a scanner is done
    public Set<ChunkPos> getNearbyChunkPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ChunkPos playerPos = player.getChunkPos();
        int playerX = playerPos.x;
        int playerZ = playerPos.z;

        int radius = getLoadedChunkRadius();
        ChunkPos start = new ChunkPos(playerX + radius, playerZ + radius);
        ChunkPos end = new ChunkPos(playerX - radius, playerZ - radius);

        Set<ChunkPos> loadedChunks = Sets.newHashSet();
        for (ChunkPos chunkPos : ChunkPos.stream(start, end).toList()) {
            WorldChunk chunk = this.world.getChunk(chunkPos.x, chunkPos.z);
            if (chunk.isEmpty()) continue;
            loadedChunks.add(chunkPos);
        }

        return loadedChunks;
    }

    /**
     * @return The wave chunk radius - pulls from either the Wavify config or the server's render distance(whichever one is smaller).
     */
    public int getChunkRadius() {
        MinecraftClient client = MinecraftClient.getInstance();
        int configRadius = WavifyConfig.chunkRadius;
        int serverRadius = client.options.serverViewDistance;

        return Math.min(configRadius, serverRadius);
    }

    /**
     * @return The loaded chunk radius - used for figuring out all loaded chunks on the client.
     */
    public int getLoadedChunkRadius() {
        MinecraftClient client = MinecraftClient.getInstance();
        int loadRadius = client.options.serverViewDistance;
        return Math.max(2, loadRadius) + 3;
    }

    public void debugTick(MinecraftClient client, ClientPlayerEntity player) {
        if (DebugHelper.offhandSpyglass()) {
            renderRiverDebugMarkers(player);
        }

        // show water direction of water blocks
        if (DebugHelper.holdingCompass() || DebugHelper.offhandCompass()) {
            if (!this.waterHandler.built) return;

            ChunkPos playerChunk = player.getChunkPos();
            if (DebugHelper.offhandCompass()) {
                if (client.world.getTime() % 5 != 0) return;
                int radius = 2;
                ChunkPos start = new ChunkPos(playerChunk.x + radius, playerChunk.z + radius);
                ChunkPos end = new ChunkPos(playerChunk.x - radius, playerChunk.z - radius);
                for (ChunkPos chunkPos : ChunkPos.stream(start, end).toList()) {
                    debugChunkDirectionParticles(chunkPos.toLong(), true);
                }
            } else {
                debugChunkDirectionParticles(playerChunk.toLong(), false);
            }

        }

        // print water direction's yaw
        if (DebugHelper.usingSpyglass()) {
            if (client.world.getTime() % 20 != 0) return;

            BlockPos playerPos = player.getBlockPos();

            List<BlockPos> scannedBlocks = this.waterHandler.waterCache.values().stream().flatMap(map -> map.keySet().stream()).toList();
            if (scannedBlocks.contains(playerPos)) {
                long chunkPosL = new ChunkPos(playerPos).toLong();
                SitePos site = this.waterHandler.waterCache.get(chunkPosL).get(playerPos);
//                System.out.println(world.getBiome(site.getPos()).isIn(BiomeTags.IS_RIVER));
                System.out.println(site.xList.size());
            }
        }
    }

    private void renderRiverDebugMarkers(ClientPlayerEntity player) {
        if (this.riverDebugMarkers.isEmpty()) return;

        for (RiverFlow.DebugMarker marker : this.riverDebugMarkers) {
            if (!new Vec3d(marker.x(), marker.y(), marker.z()).isInRange(new Vec3d(player.getX(), player.getY(), player.getZ()), 96.0)) continue;

            if (marker.arrow()) {
                DebugWaveMovementParticle.DebugWaveMovementParticleEffect effect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(
                        marker.color(),
                        marker.scale(),
                        marker.yaw(),
                        marker.speed(),
                        marker.lifetime()
                );
                this.world.addParticleClient(effect, false, false, marker.x(), marker.y(), marker.z(), 0, 0, 0);
            } else {
                DebugWaterParticle.DebugWaterParticleEffect effect = new DebugWaterParticle.DebugWaterParticleEffect(marker.color(), marker.scale());
                this.world.addParticleClient(effect, marker.x(), marker.y(), marker.z(), 0, 0, 0);
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
            this.world.addParticleClient(particleEffect, farParticles, false, pos.getX(), pos.getY() + 2, pos.getZ(), 0, 0, 0);
        }
    }

    /**
     * @return A random Random with a randomly generated random RandomSeed seed.
     */
    public static Random getRandom() {
        return Random.create();
    }

    /**
     * @return A random with a seed that will, most likely, be synced between clients despite being client side. Lag may cause a small issue, but should be rare.
     */
    public static Random getSyncedRandom() {
        long time = MinecraftClient.getInstance().world.getTime();
        long random = 5L * Math.round(time / 5f); // math.ceil instead?
        return Random.create(random);
    }

    /**
     * Check if a BlockPos is water or is waterlogged
     *
     * @param world World to check in
     * @param pos   BlockPos to check
     * @return If the BlockPos is water or waterlogged
     */
    public static boolean posIsWater(ClientWorld world, BlockPos pos) {
        FluidState state = world.getFluidState(pos);
        return state.isIn(FluidTags.WATER);
    }

    public static boolean stateIsWater(BlockState state) {
        return state.getFluidState().isIn(FluidTags.WATER);
    }

}
