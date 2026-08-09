package net.superkat.wavify.scan;

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
import net.superkat.wavify.DebugHelper;
import net.superkat.wavify.Wavify;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.particles.debug.DebugShoreParticle;
import net.superkat.wavify.particles.debug.DebugWaterParticle;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

// handles water/shoreline blocks and sites
// chunk loads, we note the pos, then queue it if it's close enough to matter
// tick() drains that queue into ChunkScanners, each one hands back a chunk's water, shoreline and sites
// if a chunk unloads mid scan we just let the future finish, could be smarter
// once the scans land, every water block gets its closest site worked out and cached
// chunk unloads and we drop every map key for that pos, values go with it
// enough block updates in one chunk (configurable) and we rescan it
public class WaterHandler {
    public final WavifyWaveHandler wavifyWaveHandler;
    public final ClientWorld world;
    // fastutils because... it has fast in its name? i've been told its fast! and i gotta go fast!

    // how many block updates a chunk has seen, enough of them and we rescan it
    public Map<Long, Integer> chunkUpdates = new Long2IntOpenHashMap(81, 0.25f);

    // shoreline sites, these give us the angle of an area
    public Map<Long, ObjectOpenHashSet<SitePos>> sites = new Long2ObjectOpenHashMap<>(81, 0.25f);

    // same sites but flattened, not split by chunk
    public Set<SitePos> cachedSiteSet = new ObjectOpenHashSet<>();

    // closest site for every water block we've scanned
    public Map<Long, Map<BlockPos, SitePos>> waterCache = new Long2ObjectOpenHashMap<>();

    // own map for now, packing the distance into waterCache or going Table<SitePos, Integer, Set<BlockPos>>
    // both looked more expensive than just keeping this separate
    public Map<Long, Map<Integer, Set<BlockPos>>> waterDistCache = new Long2ObjectOpenHashMap<>();

    // every shoreline block we've scanned
    public Map<Long, Set<BlockPos>> shoreBlocks = new Long2ObjectOpenHashMap<>(81, 0.25f);

    // whether the first build after joining or reloading has finished
    public boolean built = false;

    // the in flight scan of all queued chunks
    public CompletableFuture<List<ScannedChunk>> chunkScanFuture = null;

    // list of available threads i think
    private final Executor executor;

    // every loaded chunk
    public Set<ChunkPos> loadedChunks = Sets.newHashSet();

    // loaded but not scanned yet
    public Set<ChunkPos> unscannedChunks = Sets.newHashSet();

    // close enough to scan, waiting their turn
    public Queue<ChunkPos> unscannedChunkQueue = Queues.newArrayDeque();

    // every water block we know about, split by chunk
    public Map<Long, Set<BlockPos>> waters = Maps.newHashMap();

    // always use MathHelper when working with floats!

    // idea: water with no site nearby is open ocean, could do extra effects there
    // idea 2: sites with barely any blocks could spawn non directional ambient particles

    // todo: own thread pool
    // todo: fastutils new maps/sets
    // todo: quicksort for finding nearest site???
    // todo: make waterDistCache less silly

    public WaterHandler(WavifyWaveHandler wavifyWaveHandler, ClientWorld world) {
        this.wavifyWaveHandler = wavifyWaveHandler;
        this.world = world;
        this.executor = Util.getMainWorkerExecutor();
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if (!this.unscannedChunkQueue.isEmpty() && wavifyWaveHandler.nearbyChunksLoaded) {
            if (this.chunkScanFuture == null) { // probably a better way to do this but okay
                long start = Util.getMeasuringTimeMs();
                this.chunkScanFuture = scheduleChunkScans();
                this.chunkScanFuture.thenCompose(chunks -> {
                    for (ScannedChunk chunk : chunks) {
                        long chunkPosL = chunk.chunkPos;
                        if (chunk.waters != null && !chunk.waters.isEmpty()) {
                            this.waters.computeIfAbsent(chunkPosL, aLong -> Sets.newHashSet()).addAll(chunk.waters);
                        }

                        if (chunk.sites != null && !chunk.sites.isEmpty()) {
                            this.sites.computeIfAbsent(chunkPosL, aLong -> new ObjectOpenHashSet<>()).addAll(chunk.sites);
                        }

                        if (chunk.shorelines != null && !chunk.shorelines.isEmpty()) {
                            this.shoreBlocks.computeIfAbsent(chunkPosL, aLong -> new ObjectOpenHashSet<>()).addAll(chunk.shorelines);
                        }
                    }

                    this.cacheSiteSet();

                    return this.scheduleWaterCache();
                }).thenAccept(waterCacheResult -> {
                    this.waterCache = waterCacheResult.waterCache;

                    this.sites.values().forEach(siteSet -> siteSet.forEach(SitePos::clearPositions));

                    for (Map<BlockPos, SitePos> waterSiteMap : this.waterCache.values()) {
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
                    if(DebugHelper.debug()) Wavify.LOGGER.info("Scan time: {} ms", Util.getMeasuringTimeMs() - start);
                    this.chunkScanFuture = null;
                });
            }
        }

        if (DebugHelper.debug()) debugTick(client, player);
    }

    public CompletableFuture<List<ScannedChunk>> scheduleChunkScans() {
        // scan all chunks
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

    // fresh maps to swap in, editing the live ones from the futures gave weird desync issues so i gave up
    public record WaterCacheResult(Map<Long, Map<BlockPos, SitePos>> waterCache,
                                   Map<Long, Map<Integer, Set<BlockPos>>> distCache) {
    }

    public CompletableFuture<WaterCacheResult> scheduleWaterCache() {
        // calculate all water block's closest sites first, then recalc centers
        List<CompletableFuture<WaterSiteChunk>> futures = Lists.newArrayList();

        for (Map.Entry<Long, Set<BlockPos>> entry : this.waters.entrySet()) {
            long chunkPosL = entry.getKey();
            if (!this.loadedChunks.contains(new ChunkPos(chunkPosL))) continue;

            futures.add(scheduleWaterScan(chunkPosL, entry.getValue()));
        }

        return Util.combineSafe(futures).thenApply(chunks -> {
            Map<Long, Map<BlockPos, SitePos>> waterCache = new Long2ObjectOpenHashMap<>();
            Map<Long, Map<Integer, Set<BlockPos>>> distCache = new Long2ObjectOpenHashMap<>();

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
            Map<BlockPos, SitePos> siteMap = new Object2ObjectOpenHashMap<>();
            Map<Integer, Set<BlockPos>> distMap = new Int2ObjectOpenHashMap<>();
            for (BlockPos water : waters) {
                IntObjectPair<SitePos> closestSite = calcClosestSite(water);
                if (closestSite == null) continue;
                int dist = closestSite.firstInt();
                SitePos site = closestSite.second();
                siteMap.put(water, site);
                distMap.computeIfAbsent(dist, aInt -> new ObjectOpenHashSet<>()).add(water);
            }
            return new WaterSiteChunk(chunkPosL, siteMap, distMap);
        }, executor);
    }

    @Nullable // fixme: optimize this, hama said it should be easy
    public IntObjectPair<SitePos> calcClosestSite(BlockPos pos) {
        double distance = 0;
        SitePos closest = null;

        for (SitePos site : this.cachedSiteSet) {
            double dx = pos.getX() + 0.5 - site.getX();
            double dz = pos.getZ() + 0.5 - site.getZ();
            double checkDist = dx * dx + dz * dz;
//            double checkDist = Math.max(Math.abs(dx), Math.abs(dz)); // other distance formulas if we ever config this
//            double checkDist = Math.abs(dx) + Math.abs(dz);

            if (closest == null || checkDist < distance) {
                closest = site;
                distance = checkDist;
            }
        }

        int intDistance = (int) Math.sqrt(distance);
        return IntObjectPair.of(intDistance, closest);
    }

    // water blocks sitting exactly this far from their site, this is what wave spawning runs off
    @Nullable
    public Set<BlockPos> getWaterCacheAtDistance(ChunkPos chunkPos, int distance) {
        long chunkPosL = chunkPos.toLong();
        if (this.waterDistCache.containsKey(chunkPosL)) return this.waterDistCache.get(chunkPosL).get(distance);
        return null;
    }

    // closest site for a pos, caching it on the way out, usually a water block but doesn't have to be
    public SitePos getSiteForPos(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        return this.waterCache
                .computeIfAbsent(chunkPosL,
                        chunkPosL2 -> new Object2ObjectOpenHashMap<>()
                ).computeIfAbsent(pos, pos1 -> {
                    SitePos closest = findAndCacheClosestSite(chunkPosL, pos);
                    if (closest != null) closest.addPos(pos);
                    return closest;
                });
    }

    // the actual search behind getSiteForPos, null if we have no sites yet
    @Nullable
    public SitePos findAndCacheClosestSite(long chunkPosL, BlockPos pos) {
        if (this.sites.isEmpty()) return null;

        if (this.cachedSiteSet == null || this.cachedSiteSet.isEmpty()) {
            this.cacheSiteSet();
        }

        IntObjectPair<SitePos> siteDistPair = this.calcClosestSite(pos);
        if (siteDistPair == null) return null;
        int distance = siteDistPair.firstInt();
        SitePos site = siteDistPair.second();

        if (site != null) {
            this.waterDistCache.computeIfAbsent(
                    chunkPosL, chunkPosL2 -> new Int2ObjectOpenHashMap<>()
            ).computeIfAbsent(
                    distance, dist -> new ObjectOpenHashSet<>()
            ).add(pos);
        }

        return site;
    }

    private void debugTick(MinecraftClient client, ClientPlayerEntity player) {
        if (this.world.getTime() % 10 != 0) return;
        boolean farParticles = false;

        // show every site
        List<SitePos> allSites = this.sites.values().stream().flatMap(Collection::stream).toList();
        for (SitePos site : allSites) {
            this.world.addParticle(ParticleTypes.EGG_CRACK, true, site.getX() + 0.5, site.getY() + 2, site.getZ() + 0.5, 0, 0, 0);
        }

        if (!DebugHelper.debug()) return;
        if (!DebugHelper.spyglassInHotbar()) return;

        // show every shoreline block
        List<BlockPos> allShoreBLocks = this.shoreBlocks.values().stream().flatMap(Collection::stream).toList();
        ParticleEffect shoreEffect = new DebugShoreParticle.DebugShoreParticleEffect(new Vector3f(1f, 1f, 1f), 1f);
        for (BlockPos shore : allShoreBLocks) {
            Vec3d pos = shore.toCenterPos();
            this.world.addParticle(shoreEffect, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
        }

        // show every water block, colored by whichever site owns it
        int totalSites = allSites.size();
        for (Map<BlockPos, SitePos> posSiteMap : this.waterCache.values()) {
            for (Map.Entry<BlockPos, SitePos> entry : posSiteMap.entrySet()) {
                BlockPos blockPos = entry.getKey();
                if (!blockPos.isWithinDistance(new Vec3d(player.getX(), player.getY(), player.getZ()), 100)) continue;
                SitePos site = entry.getValue();

                int siteIndex = allSites.indexOf(site);
                Vector3f color = DebugHelper.debugColor(siteIndex, totalSites);

                Vec3d pos = blockPos.toCenterPos();
                ParticleEffect particleEffect = new DebugWaterParticle.DebugWaterParticleEffect(color, 1f);
                this.world.addParticle(particleEffect, farParticles, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
            }
        }
    }

    // counts updates per chunk, enough of them and that chunk gets rescanned
    public void onBlockUpdate(BlockPos pos, BlockState state) {
        long chunkPosL = new ChunkPos(pos).toLong();
        int currentUpdates = this.chunkUpdates.getOrDefault(chunkPosL, 0) + 1;
        if (currentUpdates >= WavifyConfig.chunkUpdatesRescanAmount) {
            if (this.rescanChunkPos(new ChunkPos(chunkPosL))) {
                currentUpdates = 0;
            }
        }
        this.chunkUpdates.put(chunkPosL, currentUpdates);
    }

    // wipe and rescan everything, this is what f3+a hits
    public void rebuild() {
        this.clear(); // scanners nulled, sites/shoreblocks/waterblocks all gone

        this.unscannedChunks.addAll(this.loadedChunks);
        this.chunkScanFuture = null;
        this.checkUnscannedChunks();
    }

    // fine to brute force, this only takes 1 to 3ms for me
    public void calcAllSiteCenters() {
        for (SitePos site : this.sites.values().stream().flatMap(Collection::stream).toList()) {
            site.updateCenter();
        }
    }

    public void cacheSiteSet() {
        // caching this saved about 200ms on a build or rebuild
        this.cachedSiteSet = new ObjectOpenHashSet<>(this.sites.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
    }

    // new chunk showed up, get it in line for a scan
    public void loadChunk(Chunk chunk) {
        addChunkPos(chunk.getPos());
    }

    // same thing but you already have the pos
    public void addChunkPos(ChunkPos chunkPos) {
        this.loadedChunks.add(chunkPos);
        this.unscannedChunks.add(chunkPos);
        checkUnscannedChunks();
    }

    // move any unscanned chunk that's close enough into the queue
    public void checkUnscannedChunks() {
        ChunkPos cameraChunk = new ChunkPos(MinecraftClient.getInstance().gameRenderer.getCamera().getBlockPos());
        double radius = WavifyConfig.chunkRadius * WavifyConfig.chunkRadius;
        Iterator<ChunkPos> iterator = this.unscannedChunks.iterator();
        while (iterator.hasNext()) {
            ChunkPos chunk = iterator.next();
            double distance = cameraChunk.getSquaredDistance(chunk);
            if (distance > radius) continue;

            if (this.unscannedChunkQueue.offer(chunk)) {
                iterator.remove();
            }
        }
    }

    // drop what we know about a chunk and queue it again
    public boolean rescanChunkPos(ChunkPos chunkPos) {
        long chunkPosL = chunkPos.toLong();
        this.clearChunk(chunkPosL);
        this.unscannedChunks.add(chunkPos);
        this.checkUnscannedChunks();
        return true;
    }

    // chunk's gone, forget it completely
    public void unloadChunk(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long chunkPosL = chunkPos.toLong();
        this.clearChunk(chunkPosL);
        this.chunkUpdates.remove(chunkPosL);
        this.loadedChunks.remove(chunkPos);
        this.unscannedChunks.remove(chunkPos);
    }

    // drop a chunk's scanned data but leave it in scannedChunks and chunkUpdates, it's still loaded
    public void clearChunk(long chunkPosL) {
        this.shoreBlocks.remove(chunkPosL);
        this.waterCache.remove(chunkPosL);
        this.waterDistCache.remove(chunkPosL);
        this.sites.remove(chunkPosL);
        this.waters.remove(chunkPosL);
        this.cachedSiteSet.clear(); // resets it
    }

    // clears everything except loadedChunks, that one stays
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
