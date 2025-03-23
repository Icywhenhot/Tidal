package net.superkat.tidal.scan;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.superkat.tidal.DebugHelper;
import net.superkat.tidal.Tidal;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.debug.DebugShoreParticle;
import net.superkat.tidal.particles.debug.DebugWaterParticle;
import net.superkat.tidal.wave.TidalWaveHandler;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Handles water/shoreline blocks & SitePos'
 * <br><br>
 * How this goofy thing works:<br><br>
 *
 * Chunk loaded -> {@link WaterHandler#loadChunk(Chunk)} -> adds the ChunkPos to {@link WaterHandler#loadedChunks}.<br><br>
 *
 * {@link WaterHandler#checkUnscannedChunks()} adds unscanned chunks within scanning distance to {@link WaterHandler#unscannedChunkQueue}.<br>
 * In {@link WaterHandler#tick()}, that unscannedChunkQueue is iterated though via {@link WaterHandler#scheduleChunkScans()}, where a {@link ChunkScanner} is created, and returns a {@link ScannedChunk} with that chunk's water blocks, shoreline blocks, and created {@link SitePos} sites.<br><br>
 * Currently, nothing is done to the ChunkScanner if the chunk is unloaded during its scan process, as the CompletableFuture does not get cancelled. That could be an improvement.<br><br>
 * Once all queued ChunkScanners are finished, the scanner provides the info to here, the WaterHandler.<br><br>
 * Then, all known water blocks have their closest SitePos calculated via {@link  WaterHandler#scheduleWaterCache()}, and once that is done, the values for {@link WaterHandler#waterCache} & {@link WaterHandler#waterDistCache} are set.<br><br>
 *
 * Chunk unloaded -> {@link WaterHandler#unloadChunk(Chunk)}. Because nearly everything is split per chunk via Maps, all keys with that ChunkPos(as a long) are removed, removing the values with it.<br><br>
 *
 * Join world -> Nearby chunks are added via loadChunk(), then once all nearby chunks are loaded via {@link TidalWaveHandler#nearbyChunksLoaded(ClientPlayerEntity)}, the scheduleChunkScans method is called.<br><br>
 *
 * Block updated -> {@link WaterHandler#onBlockUpdate(BlockPos, BlockState)}. A count of all block updates per chunk is kept track of in {@link WaterHandler#chunkUpdates}.<br>After enough block updates in a chunk(configurable), that chunk will be rescanned via {@link WaterHandler#rescanChunkPos(ChunkPos)}.
 *
 * @see TidalWaveHandler
 * @see ChunkScanner
 * @see SitePos
 */
public class WaterHandler {
    public final TidalWaveHandler tidalWaveHandler;
    public final ClientWorld world;
    //using fastutils because... it has fast in its name? I've been told its fast! And I gotta go fast!

    //Keep track of how many block updates have happened in a chunk - used to rescan chunks after enough(configurable) updates
    public Long2IntOpenHashMap chunkUpdates = new Long2IntOpenHashMap(81, 0.25f);

    //Set of shoreline sites, used to determine angle of area
    public Long2ObjectOpenHashMap<ObjectOpenHashSet<SitePos>> sites = new Long2ObjectOpenHashMap<>(81, 0.25f);

    //Caches all the sites into one set, not split by chunk.
    public ObjectOpenHashSet<SitePos> cachedSiteSet = new ObjectOpenHashSet<>();

    //Keep track of which SitePos is closest to all scanned water blocks
    public Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<BlockPos, SitePos>> waterCache = new Long2ObjectOpenHashMap<>();

    //This is being kept as its own map only for now. I considered a few other options, but didn't know which one was better:
    // - it's own map(already done)
    // - Changing the waterCache's Object2ObjectMap to have a ObjectIntPair<SitePos> for the BlockPos value, instead of the current SitePos
    // - Changing the waterCache's Object2ObjectMap to a Table<SitePos, Integer, Set<BlockPos>> instead

    //That third option would be the easiest to work with after everything was cached,
    //but I was afraid that the process of checking if a Set<BlockPos> had already been created for a Table,
    //and then checking if that set contained the BlockPos given to WaterHandler#findClosestSite would be too expensive

    //The second option I was afraid would be too expensive when trying to find all water blocks within a specific distance,
    //as you'd need to filter through all of them each time you wanted to find a block within a specific distance

    //this is the equal to Map<ChunkPos, Map<Integer, Set<BlockPos>>>, which may not be great performance-wise?
    //alternatively, use Long2ObjectOpenHashMap<HashMultimap<Integer, BlockPos>>????
    public Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<ObjectOpenHashSet<BlockPos>>> waterDistCache = new Long2ObjectOpenHashMap<>();

    //All scanned shoreline blocks
    public Long2ObjectOpenHashMap<Set<BlockPos>> shoreBlocks = new Long2ObjectOpenHashMap<>(81, 0.25f);

    //boolean for if the initial joining/chunk reloading build is finished or not
    public boolean built = false;

    //CompletableFuture for scanning all chunks - e.g. finding water blocks, shore blocks, & sites
    public CompletableFuture<List<ScannedChunk>> chunkScanFuture = null;

    //Executor for list of available threads I think
    private final Executor executor;

    //Set of all loaded chunks
    public Set<ChunkPos> loadedChunks = Sets.newHashSet();

    //Set of all chunks that are loaded, but haven't been scanned
    public Set<ChunkPos> unscannedChunks = Sets.newHashSet();

    //Set of all chunks ready to be scanned(e.g. within wave spawning distance)
    public Queue<ChunkPos> unscannedChunkQueue = Queues.newArrayDeque();

    //List of all known water blocks, split by chunk
    public Map<Long, Set<BlockPos>> waters = Maps.newHashMap();

    //idea: if no site is within configurable distance, that water is considered open ocean and extra effects can be added there
    //idea 2: if the amount of blocks associated with a SitePos is really small, non-directional ambient particles spawn

    //TODO - fastutils new maps/sets
    //TODO - QuickSort algorithm for finding nearest SitePos???
    //TODO - update waterDistCache to be better?

    public WaterHandler(TidalWaveHandler tidalWaveHandler, ClientWorld world) {
        this.tidalWaveHandler = tidalWaveHandler;
        this.world = world;
        this.executor = Util.getMainWorkerExecutor();
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if(!this.unscannedChunkQueue.isEmpty() && tidalWaveHandler.nearbyChunksLoaded) {
            if(this.chunkScanFuture == null) { //I don't know if there's a better way to do this or not but okay
                long start = Util.getMeasuringTimeMs();
                this.chunkScanFuture = scheduleChunkScans();
                this.chunkScanFuture.thenCompose(chunks -> {
                    for (ScannedChunk chunk : chunks) {
                        long chunkPosL = chunk.chunkPos;
                        if(chunk.waters != null && !chunk.waters.isEmpty()) {
                            this.waters.computeIfAbsent(chunkPosL, aLong -> Sets.newHashSet()).addAll(chunk.waters);
                        }

                        if(chunk.sites != null && !chunk.sites.isEmpty()) {
                            this.sites.computeIfAbsent(chunkPosL, aLong -> new ObjectOpenHashSet<>()).addAll(chunk.sites);
                        }

                        if(chunk.shorelines != null && !chunk.shorelines.isEmpty()) {
                            this.shoreBlocks.computeIfAbsent(chunkPosL, aLong -> new ObjectOpenHashSet<>()).addAll(chunk.shorelines);
                        }
                    }

                    this.cacheSiteSet();

                    return this.scheduleWaterCache();
                }).thenAccept(waterCacheResult -> {
                    this.waterCache = waterCacheResult.waterCache;

                    this.sites.values().forEach(siteSet -> siteSet.forEach(SitePos::clearPositions));

                    for (Object2ObjectOpenHashMap<BlockPos, SitePos> waterSiteMap : this.waterCache.values()) {
                        for (Map.Entry<BlockPos, SitePos> entry : waterSiteMap.entrySet()) {
                            BlockPos water = entry.getKey();
                            SitePos site = entry.getValue();
                            site.addPos(water);
                        }
                    }

                    this.waterDistCache = waterCacheResult.distCache;
                }).thenRun(() -> {
                    calcAllSiteCenters();
                    this.built = true;
                });

                this.chunkScanFuture.whenComplete((chunks, throwable) -> {
                    Tidal.LOGGER.info("Scan time: {} ms", Util.getMeasuringTimeMs() - start);
                    this.chunkScanFuture = null;
                });
            }
        }

        if(DebugHelper.debug()) debugTick(client, player);
    }

    public CompletableFuture<List<ScannedChunk>> scheduleChunkScans() {
        //scan all chunks
        this.built = false;
        List<CompletableFuture<ScannedChunk>> futures = Lists.newArrayList();

        int chunkQueueSize = this.unscannedChunkQueue.size();
        for (int i = 0; i < chunkQueueSize; i++) {
            ChunkPos chunk = unscannedChunkQueue.poll();
            futures.add(scheduleChunkScan(chunk));
        }

        return Util.combineSafe(futures);
    }

    private CompletableFuture<ScannedChunk> scheduleChunkScan(ChunkPos pos) {
        return CompletableFuture.supplyAsync(() -> {
            ChunkScanner chunkScanner = new ChunkScanner(this, this.world, pos);
            return chunkScanner.scan();
        }, executor);
    }

    //Gives waterCache/waterDistCache maps to replace current maps with, instead of trying to modify current maps
    //Trying to modify the current maps via the CompletableFutures, even from `.thenApply()`, (supposed to be main thread I think)
    //kept resulting with weird, seemingly desync-related issues, so I gave up.
    public record WaterCacheResult(Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<BlockPos, SitePos>> waterCache, Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<ObjectOpenHashSet<BlockPos>>> distCache) {}

    public CompletableFuture<WaterCacheResult> scheduleWaterCache() {
        //calculate all water block's closest sites first, then recalc centers
        List<CompletableFuture<WaterSiteChunk>> futures = Lists.newArrayList();

        for (Map.Entry<Long, Set<BlockPos>> entry : this.waters.entrySet()) {
            long chunkPosL = entry.getKey();
            if(!this.loadedChunks.contains(new ChunkPos(chunkPosL))) continue;

            futures.add(scheduleWaterScan(chunkPosL, entry.getValue()));
        }

        return Util.combineSafe(futures).thenApply(chunks -> {
            Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<BlockPos, SitePos>> waterCache = new Long2ObjectOpenHashMap<>();
            Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<ObjectOpenHashSet<BlockPos>>> distCache = new Long2ObjectOpenHashMap<>();

            for (WaterSiteChunk chunk : chunks) {
                long chunkPosL = chunk.chunkPos;
                waterCache.put(chunkPosL, chunk.waterSiteMap);
                distCache.put(chunkPosL, chunk.distWaterMap);
            }

            return new WaterCacheResult(waterCache, distCache);
        });
    }
    private CompletableFuture<WaterSiteChunk> scheduleWaterScan(long chunkPosL, Set<BlockPos> waters) {
        return CompletableFuture.supplyAsync(() -> {
            Object2ObjectOpenHashMap<BlockPos, SitePos> siteMap = new Object2ObjectOpenHashMap<>();
            Int2ObjectOpenHashMap<ObjectOpenHashSet<BlockPos>> distMap = new Int2ObjectOpenHashMap<>();
            for (BlockPos water : waters) {
                IntObjectPair<SitePos> closestSite = calcClosestSite(water);
                if(closestSite == null) continue;
                int dist = closestSite.firstInt();
                SitePos site = closestSite.second();
                siteMap.put(water, site);
                distMap.computeIfAbsent(dist, aInt -> new ObjectOpenHashSet<>()).add(water);
            }
            return new WaterSiteChunk(chunkPosL, siteMap, distMap);
        }, executor);
    }

    @Nullable //FIXME - optimize this(Hama said it should be easy)
    public IntObjectPair<SitePos> calcClosestSite(BlockPos pos) {
        double distance = 0;
        SitePos closest = null;

        for (SitePos site : this.cachedSiteSet) {
            double dx = pos.getX() + 0.5 - site.getX();
            double dz = pos.getZ() + 0.5 - site.getZ();
            double checkDist = dx * dx + dz * dz;
//            double checkDist = Math.max(Math.abs(dx), Math.abs(dz)); //alt distance formulas for future config
//            double checkDist = Math.abs(dx) + Math.abs(dz);

            if(closest == null || checkDist < distance) {
                closest = site;
                distance = checkDist;
            }
        }

        int intDistance = (int) Math.sqrt(distance);
        return IntObjectPair.of(intDistance, closest);
    }

    /**
     * Gets a Set of BlockPos' that are a specified distance away from their closest SitePos within a ChunkPos. Used for spawning waves.
     *
     * @param chunkPos The ChunkPos to get the water blocks from
     * @param distance The distance to check for
     * @return The Set of BlockPos, or null if none are found.
     */
    @Nullable
    public Set<BlockPos> getWaterCacheAtDistance(ChunkPos chunkPos, int distance) {
        long chunkPosL = chunkPos.toLong();
        if(this.waterDistCache.containsKey(chunkPosL)) return this.waterDistCache.get(chunkPosL).get(distance);
        return null;
    }

    /**
     * Cache and or return the closest SitePos of a BlockPos(assumed to be, but technically doesn't have to be, a water block).
     *
     * @param pos BlockPos to use for finding the closest SitePos.
     * @return The BlockPos' closest SitePos, or {@link BlockPos#ORIGIN} if the site is null.
     */
    public SitePos getSiteForPos(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        return this.waterCache
                .computeIfAbsent(chunkPosL,
                        chunkPosL2 -> new Object2ObjectOpenHashMap<>()
                ).computeIfAbsent(pos, pos1 -> {
                    SitePos closest = findAndCacheClosestSite(chunkPosL, pos);
                    if(closest != null) closest.addPos(pos);
                    return closest;
                });
    }

    /**
     * Calculates the closest SitePos from a BlockPos. Used by {@link WaterHandler#getSiteForPos(BlockPos)}.
     *
     * @param pos The BlockPos to use for finding the closest SitePos
     * @return The closest SitePos, or null if no SitePos' are currently stored.
     */
    @Nullable
    public SitePos findAndCacheClosestSite(long chunkPosL, BlockPos pos) {
        if(this.sites.isEmpty()) return null;

        if(this.cachedSiteSet == null || this.cachedSiteSet.isEmpty()) {
            this.cacheSiteSet();
        }

        IntObjectPair<SitePos> siteDistPair = this.calcClosestSite(pos);
        if(siteDistPair == null) return null;
        int distance = siteDistPair.firstInt();
        SitePos site = siteDistPair.second();

        if(site != null) {
            this.waterDistCache.computeIfAbsent(
                    chunkPosL, chunkPosL2 -> new Int2ObjectOpenHashMap<>()
            ).computeIfAbsent(
                    distance, dist -> new ObjectOpenHashSet<>()
            ).add(pos);
        }

        return site;
    }

    private void debugTick(MinecraftClient client, ClientPlayerEntity player) {
        if(this.world.getTime() % 10 != 0) return;
        boolean farParticles = false;

        //display all shoreline blocks
        //display all sitePos'
        List<SitePos> allSites = this.sites.values().stream().flatMap(Collection::stream).toList();
        for (SitePos site : allSites) {
            this.world.addParticle(ParticleTypes.EGG_CRACK, true, site.getX() + 0.5, site.getY() + 2, site.getZ() + 0.5, 0, 0, 0);
        }

        if(!DebugHelper.debug()) return;
        if(!DebugHelper.spyglassInHotbar()) return;

        //display all shoreline blocks
        List<BlockPos> allShoreBLocks = this.shoreBlocks.values().stream().flatMap(Collection::stream).toList();
        ParticleEffect shoreEffect = new DebugShoreParticle.DebugShoreParticleEffect(Vec3d.unpackRgb(Color.WHITE.getRGB()).toVector3f(), 1f);
        for (BlockPos shore : allShoreBLocks) {
            Vec3d pos = shore.toCenterPos();
            this.world.addParticle(shoreEffect, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
        }

        //display all water blocks pos', colored by closest site
        int totalSites = allSites.size();
        for (Object2ObjectOpenHashMap<BlockPos, SitePos> posSiteMap : this.waterCache.values()) {
            for (Map.Entry<BlockPos, SitePos> entry : posSiteMap.entrySet()) {
                BlockPos blockPos = entry.getKey();
                if(!blockPos.isWithinDistance(player.getPos(), 100)) continue;
                SitePos site = entry.getValue();

                int siteIndex = allSites.indexOf(site);
                Color color = DebugHelper.debugColor(siteIndex, totalSites);

                Vec3d pos = blockPos.toCenterPos();
                ParticleEffect particleEffect = new DebugWaterParticle.DebugWaterParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                this.world.addParticle(particleEffect, farParticles, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
            }
        }
    }

    /**
     * Called during a block update. Used to count how many block updates have happened in any given chunk. After enough updates in a chunk, that chunk is rescanned.
     *
     * @param pos The BlockPos that was updated
     * @param state The new BlockState of the updated BlockPos
     */
    public void onBlockUpdate(BlockPos pos, BlockState state) {
        long chunkPosL = new ChunkPos(pos).toLong();
        int currentUpdates = this.chunkUpdates.getOrDefault(chunkPosL, 0) + 1;
        if (currentUpdates >= TidalConfig.chunkUpdatesRescanAmount) {
            if(this.rescanChunkPos(new ChunkPos(chunkPosL))) {
                currentUpdates = 0;
            }
        }
        this.chunkUpdates.put(chunkPosL, currentUpdates);
    }

    /**
     * Easy method to clear & rescan chunks - called from chunk reload(f3+a)
     */
    public void rebuild() {
        this.clear(); //clear all data(ticking scanners -> null, sites/shoreblocks/waterblocks all cleared)

        this.unscannedChunks.addAll(this.loadedChunks);
        this.chunkScanFuture = null;
        this.checkUnscannedChunks();
    }

    //I feel comfortable doing this because this calculation is usually only taken 1-3ms for me
    public void calcAllSiteCenters() {
        for (SitePos site : this.sites.values().stream().flatMap(Collection::stream).toList()) {
            site.updateCenter();
        }
    }

    public void cacheSiteSet() {
        //caching this list saved ~200ms during a build/rebuild
        this.cachedSiteSet = new ObjectOpenHashSet<>(this.sites.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
    }

    /**
     * Schedules a chunk to be scanned water blocks, shoreblocks, sites, etc. Called when a new chunk is loaded.
     *
     * @param chunk Chunk to schedule
     * @see WaterHandler#addChunkPos(ChunkPos)
     */
    public void loadChunk(Chunk chunk) {
        addChunkPos(chunk.getPos());
    }

    /**
     * Schedules a chunk to be scanned for water blocks, shoreblocks, sites, etc.
     *
     * @param chunkPos ChunkPos of the chunk to be scanned
     * @see WaterHandler#loadChunk(Chunk)
     */
    public void addChunkPos(ChunkPos chunkPos) {
        this.loadedChunks.add(chunkPos);
        this.unscannedChunks.add(chunkPos);
        checkUnscannedChunks();
    }

    /**
     * Searches through all loaded, unscanned chunks, and queues unscanned chunks which are within scanning distance to {@link WaterHandler#unscannedChunkQueue}
     */
    public void checkUnscannedChunks() {
        ChunkPos cameraChunk = new ChunkPos(MinecraftClient.getInstance().gameRenderer.getCamera().getBlockPos());
        double radius = TidalConfig.chunkRadius * TidalConfig.chunkRadius;
        Iterator<ChunkPos> iterator = this.unscannedChunks.iterator();
        while (iterator.hasNext()) {
            ChunkPos chunk = iterator.next();
            double distance = cameraChunk.getSquaredDistance(chunk);
            if (distance > radius) continue;

            if(this.unscannedChunkQueue.offer(chunk)) {
                iterator.remove();
            }
        }
    }

    /**
     * Schedules a ChunkPos to be rescanned, removing the chunk's blocks from all trackers.
     *
     * @param chunkPos ChunkPos to remove
     * @return If the reschedule was successful. It will return false if a scanner is already associated with the ChunkPos
     */
    public boolean rescanChunkPos(ChunkPos chunkPos) {
        long chunkPosL = chunkPos.toLong();
        this.clearChunk(chunkPosL);
        this.unscannedChunks.add(chunkPos);
        this.checkUnscannedChunks();
        return true;
    }

    /**
     * Fully removes a chunk form all trackers, scanners, and updates. Called once a chunk is unloaded.
     *
     * @param chunk The ChunkPos(as a long) to remove
     */
    public void unloadChunk(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long chunkPosL = chunkPos.toLong();
        this.clearChunk(chunkPosL);
        this.chunkUpdates.remove(chunkPosL);
        this.loadedChunks.remove(chunkPos);
        this.unscannedChunks.remove(chunkPos);
    }

    /**
     * Removes a chunk from all trackers, e.g. waitingWaterBlocks, sites, siteCache, shoreblocks.
     * <br><br>The chunk remains in the scannedChunks & chunkUpdates maps, as it is assumed it is still loaded.
     *
     * @param chunkPosL The ChunkPos(as a long) to remove
     */
    public void clearChunk(long chunkPosL) {
        this.shoreBlocks.remove(chunkPosL);
        this.waterCache.remove(chunkPosL);
        this.waterDistCache.remove(chunkPosL);
        this.sites.remove(chunkPosL);
        this.waters.remove(chunkPosL);
        this.cachedSiteSet.clear(); //resets it
    }

    /**
     * Clears all maps/sets EXCEPT {@link WaterHandler#loadedChunks}! Used for rebuilding via f3+a
     */
    public void clear() {
        this.shoreBlocks.clear();
        this.sites.clear();
        this.waterCache.clear();
        this.waterDistCache.clear();
        this.cachedSiteSet.clear();
        this.unscannedChunks.clear();
        this.chunkUpdates.clear();
    }
}
