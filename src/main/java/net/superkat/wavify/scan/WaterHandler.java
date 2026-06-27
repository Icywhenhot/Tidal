package net.superkat.wavify.scan;

import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.ChunkAccess;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class WaterHandler {
    public final WavifyWaveHandler wavifyWaveHandler;
    public final ClientLevel level;

    // Keep track of how many block updates have happened in a chunk - used to rescan chunks after enough(configurable) updates
    public Map<Long, Integer> chunkUpdates = new Long2IntOpenHashMap(81, 0.25f);

    // Set of shoreline sites, used to determine angle of area
    public Map<Long, ObjectOpenHashSet<SitePos>> sites = new Long2ObjectOpenHashMap<>(81, 0.25f);

    // Caches all the sites into one set, not split by chunk.
    public Set<SitePos> cachedSiteSet = new ObjectOpenHashSet<>();

    // Keep track of which SitePos is closest to all scanned water blocks
    public Map<Long, Map<BlockPos, SitePos>> waterCache = new Long2ObjectOpenHashMap<>();

    public Map<Long, Map<Integer, Set<BlockPos>>> waterDistCache = new Long2ObjectOpenHashMap<>();

    // All scanned shoreline blocks
    public Map<Long, Set<BlockPos>> shoreBlocks = new Long2ObjectOpenHashMap<>(81, 0.25f);

    // boolean for if the initial joining/chunk reloading build is finished or not
    public boolean built = false;

    // CompletableFuture for scanning all chunks - e.g. finding water blocks, shore blocks, & sites
    public CompletableFuture<List<ScannedChunk>> chunkScanFuture = null;

    // Executor for list of available threads I think
    private final Executor executor;

    // Set of all loaded chunks
    public Set<ChunkPos> loadedChunks = Sets.newHashSet();

    // Set of all chunks that are loaded, but haven't been scanned
    public Set<ChunkPos> unscannedChunks = Sets.newHashSet();

    // Set of all chunks ready to be scanned(e.g. within wave spawning distance)
    public Queue<ChunkPos> unscannedChunkQueue = Queues.newArrayDeque();

    public Map<Long, Set<BlockPos>> waters = new ConcurrentHashMap<>();

    public WaterHandler(WavifyWaveHandler wavifyWaveHandler, ClientLevel level) {
        this.wavifyWaveHandler = wavifyWaveHandler;
        this.level = level;
        this.executor = Util.backgroundExecutor();
    }

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        assert player != null;

        if (!this.unscannedChunkQueue.isEmpty() && wavifyWaveHandler.nearbyChunksLoaded) {
            if (this.chunkScanFuture == null) { // I don't know if there's a better way to do this or not but okay
                long start = Util.getMillis();
                this.chunkScanFuture = scheduleChunkScans();
                this.chunkScanFuture.thenComposeAsync(chunks -> {
                    for (ScannedChunk chunk : chunks) {
                        long chunkPosL = chunk.chunkPos;
                        if (chunk.waters != null && !chunk.waters.isEmpty()) {
                            this.waters.computeIfAbsent(chunkPosL, aLong -> ConcurrentHashMap.newKeySet()).addAll(chunk.waters);
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
                }, client).thenAcceptAsync(waterCacheResult -> {
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
                }, client).thenRunAsync(() -> {
                    calcAllSiteCenters();
                    this.built = true;
                }, client);

                this.chunkScanFuture.whenCompleteAsync((chunks, throwable) -> {
                    if(DebugHelper.debug()) Wavify.LOGGER.info("Scan time: {} ms", Util.getMillis() - start);
                    this.chunkScanFuture = null;
                }, client);
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

        return Util.sequence(futures);
    }

    private CompletableFuture<ScannedChunk> scheduleChunkScan(ChunkPos pos) {
        return CompletableFuture.supplyAsync(() -> {
            ChunkScanner chunkScanner = new ChunkScanner(this, this.level, pos);
            return chunkScanner.scan();
        }, executor);
    }

    public record WaterCacheResult(Map<Long, Map<BlockPos, SitePos>> waterCache,
                                   Map<Long, Map<Integer, Set<BlockPos>>> distCache) {
    }

    public CompletableFuture<WaterCacheResult> scheduleWaterCache() {
        // calculate all water block's closest sites first, then recalc centers
        List<CompletableFuture<WaterSiteChunk>> futures = Lists.newArrayList();

        for (Map.Entry<Long, Set<BlockPos>> entry : this.waters.entrySet()) {
            long chunkPosL = entry.getKey();
            if (!this.loadedChunks.contains(new ChunkPos(ChunkPos.getX(chunkPosL), ChunkPos.getZ(chunkPosL)))) continue;

            futures.add(scheduleWaterScan(chunkPosL, entry.getValue()));
        }

        return Util.sequence(futures).thenApply(chunks -> {
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

    @Nullable //FIXME - optimize this(Hama said it should be easy)
    public IntObjectPair<SitePos> calcClosestSite(BlockPos pos) {
        double distance = 0;
        SitePos closest = null;

        for (SitePos site : this.cachedSiteSet) {
            double dx = pos.getX() + 0.5 - site.getX();
            double dz = pos.getZ() + 0.5 - site.getZ();
            double checkDist = dx * dx + dz * dz;

            if (closest == null || checkDist < distance) {
                closest = site;
                distance = checkDist;
            }
        }

        int intDistance = (int) Math.sqrt(distance);
        return IntObjectPair.of(intDistance, closest);
    }

    @Nullable
    public Set<BlockPos> getWaterCacheAtDistance(ChunkPos chunkPos, int distance) {
        long chunkPosL = chunkPos.toLong();
        if (this.waterDistCache.containsKey(chunkPosL)) return this.waterDistCache.get(chunkPosL).get(distance);
        return null;
    }

    public SitePos getSiteForPos(BlockPos pos) {
        long chunkPosL = ChunkPos.asLong(pos);
        return this.waterCache
                .computeIfAbsent(chunkPosL,
                        chunkPosL2 -> new Object2ObjectOpenHashMap<>()
                ).computeIfAbsent(pos, pos1 -> {
                    SitePos closest = findAndCacheClosestSite(chunkPosL, pos);
                    if (closest != null) closest.addPos(pos);
                    return closest;
                });
    }

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

    private void debugTick(Minecraft client, LocalPlayer player) {
        if (this.level.getGameTime() % 10 != 0) return;
        boolean farParticles = false;

        List<SitePos> allSites = this.sites.values().stream().flatMap(Collection::stream).toList();
        for (SitePos site : allSites) {
            this.level.addParticle(ParticleTypes.EGG_CRACK, true, site.getX() + 0.5, site.getY() + 2, site.getZ() + 0.5, 0, 0, 0);
        }

        if (!DebugHelper.debug()) return;
        if (!DebugHelper.spyglassInHotbar()) return;

        // display all shoreline blocks
        List<BlockPos> allShoreBLocks = this.shoreBlocks.values().stream().flatMap(Collection::stream).toList();
        ParticleOptions shoreEffect = new DebugShoreParticle.DebugShoreParticleEffect(new Vector3f(1f, 1f, 1f), 1f);
        for (BlockPos shore : allShoreBLocks) {
            Vec3 pos = shore.getCenter();
            this.level.addParticle(shoreEffect, pos.x(), pos.y() + 1, pos.z(), 0, 0, 0);
        }

        // display all water blocks pos', colored by closest site
        int totalSites = allSites.size();
        for (Map<BlockPos, SitePos> posSiteMap : this.waterCache.values()) {
            for (Map.Entry<BlockPos, SitePos> entry : posSiteMap.entrySet()) {
                BlockPos blockPos = entry.getKey();
                if (!blockPos.closerToCenterThan(new Vec3(player.getX(), player.getY(), player.getZ()), 100)) continue;
                SitePos site = entry.getValue();

                int siteIndex = allSites.indexOf(site);
                Vector3f color = DebugHelper.debugColor(siteIndex, totalSites);

                Vec3 pos = blockPos.getCenter();
                ParticleOptions particleEffect = new DebugWaterParticle.DebugWaterParticleEffect(color, 1f);
                this.level.addParticle(particleEffect, farParticles, pos.x(), pos.y() + 1, pos.z(), 0, 0, 0);
            }
        }
    }

    public void onBlockUpdate(BlockPos pos, BlockState state) {
        long chunkPosL = ChunkPos.asLong(pos);
        int currentUpdates = this.chunkUpdates.getOrDefault(chunkPosL, 0) + 1;
        if (currentUpdates >= WavifyConfig.chunkUpdatesRescanAmount) {
            if (this.rescanChunkPos(new ChunkPos(ChunkPos.getX(chunkPosL), ChunkPos.getZ(chunkPosL)))) {
                currentUpdates = 0;
            }
        }
        this.chunkUpdates.put(chunkPosL, currentUpdates);
    }

    public void rebuild() {
        this.clear(); // clear all data(ticking scanners -> null, sites/shoreblocks/waterblocks all cleared)

        this.unscannedChunks.addAll(this.loadedChunks);
        this.chunkScanFuture = null;
        this.checkUnscannedChunks();
    }

    // I feel comfortable doing this because this calculation is usually only taken 1-3ms for me
    public void calcAllSiteCenters() {
        for (SitePos site : this.sites.values().stream().flatMap(Collection::stream).toList()) {
            site.updateCenter();
        }
    }

    public void cacheSiteSet() {
        // caching this list saved ~200ms during a build/rebuild
        this.cachedSiteSet = new ObjectOpenHashSet<>(this.sites.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
    }

    public void loadChunk(ChunkAccess chunk) {
        addChunkPos(chunk.getPos());
    }

    public void addChunkPos(ChunkPos chunkPos) {
        this.loadedChunks.add(chunkPos);
        this.unscannedChunks.add(chunkPos);
        checkUnscannedChunks();
    }

    public void checkUnscannedChunks() {
        net.minecraft.world.phys.Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        ChunkPos cameraChunk = new ChunkPos(((int) Math.floor(camPos.x)) >> 4, ((int) Math.floor(camPos.z)) >> 4);
        double radius = WavifyConfig.chunkRadius * WavifyConfig.chunkRadius;
        Iterator<ChunkPos> iterator = this.unscannedChunks.iterator();
        while (iterator.hasNext()) {
            ChunkPos chunk = iterator.next();
            double dxChunk = cameraChunk.x - (double) chunk.x;
            double dzChunk = cameraChunk.z - (double) chunk.z;
            double distance = dxChunk * dxChunk + dzChunk * dzChunk;
            if (distance > radius) continue;

            if (this.unscannedChunkQueue.offer(chunk)) {
                iterator.remove();
            }
        }
    }

    public boolean rescanChunkPos(ChunkPos chunkPos) {
        long chunkPosL = chunkPos.toLong();
        this.clearChunk(chunkPosL);
        this.unscannedChunks.add(chunkPos);
        this.checkUnscannedChunks();
        return true;
    }

    public void unloadChunk(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long chunkPosL = chunkPos.toLong();
        this.clearChunk(chunkPosL);
        this.chunkUpdates.remove(chunkPosL);
        this.loadedChunks.remove(chunkPos);
        this.unscannedChunks.remove(chunkPos);
    }

    public void clearChunk(long chunkPosL) {
        this.shoreBlocks.remove(chunkPosL);
        this.waterCache.remove(chunkPosL);
        this.waterDistCache.remove(chunkPosL);
        this.sites.remove(chunkPosL);
        this.waters.remove(chunkPosL);
        this.cachedSiteSet.clear(); //resets it
    }

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
