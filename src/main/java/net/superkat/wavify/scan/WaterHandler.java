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
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.Nullable;

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

    public Long2IntOpenHashMap chunkUpdates = new Long2IntOpenHashMap(81, 0.25f);

    public Long2ObjectOpenHashMap<ObjectOpenHashSet<SitePos>> sites = new Long2ObjectOpenHashMap<>(81, 0.25f);

    public Set<SitePos> cachedSiteSet = new ObjectOpenHashSet<>();

    public Long2ObjectOpenHashMap<Map<BlockPos, SitePos>> waterCache = new Long2ObjectOpenHashMap<>();

    public Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<Set<BlockPos>>> waterDistCache = new Long2ObjectOpenHashMap<>();

    public Long2ObjectOpenHashMap<Set<BlockPos>> shoreBlocks = new Long2ObjectOpenHashMap<>(81, 0.25f);

    public boolean built = false;

    public CompletableFuture<List<ScannedChunk>> chunkScanFuture = null;

    private final Executor executor;

    public Set<ChunkPos> loadedChunks = Sets.newHashSet();

    public Set<ChunkPos> unscannedChunks = Sets.newHashSet();

    public Queue<ChunkPos> unscannedChunkQueue = Queues.newArrayDeque();

    public Map<Long, Set<BlockPos>> waters = new ConcurrentHashMap<>();

    public Map<Long, Set<BlockPos>> riverWaters = new ConcurrentHashMap<>();

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
            if (this.chunkScanFuture == null) {
                        this.chunkScanFuture = scheduleChunkScans();
                this.chunkScanFuture.thenComposeAsync(chunks -> {
                    for (ScannedChunk chunk : chunks) {
                        long chunkPosL = chunk.chunkPos;
                        if (chunk.waters != null && !chunk.waters.isEmpty()) {
                            this.waters.computeIfAbsent(chunkPosL, aLong -> ConcurrentHashMap.newKeySet()).addAll(chunk.waters);
                        }

                        if (chunk.rivers != null && !chunk.rivers.isEmpty()) {
                            this.riverWaters.computeIfAbsent(chunkPosL, aLong -> ConcurrentHashMap.newKeySet()).addAll(chunk.rivers);
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

                this.chunkScanFuture.whenCompleteAsync((chunks, throwable) -> this.chunkScanFuture = null, client);
            }
        }
    }

    public CompletableFuture<List<ScannedChunk>> scheduleChunkScans() {

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

    public record WaterCacheResult(Long2ObjectOpenHashMap<Map<BlockPos, SitePos>> waterCache,
                                   Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<Set<BlockPos>>> distCache) {
    }

    public CompletableFuture<WaterCacheResult> scheduleWaterCache() {

        List<CompletableFuture<WaterSiteChunk>> futures = Lists.newArrayList();

        for (Map.Entry<Long, Set<BlockPos>> entry : this.waters.entrySet()) {
            long chunkPosL = entry.getKey();
            if (!this.loadedChunks.contains(new ChunkPos(ChunkPos.getX(chunkPosL), ChunkPos.getZ(chunkPosL)))) continue;

            futures.add(scheduleWaterScan(chunkPosL, entry.getValue()));
        }

        return Util.sequence(futures).thenApply(chunks -> {
            Long2ObjectOpenHashMap<Map<BlockPos, SitePos>> waterCache = new Long2ObjectOpenHashMap<>();
            Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<Set<BlockPos>>> distCache = new Long2ObjectOpenHashMap<>();

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
            Int2ObjectOpenHashMap<Set<BlockPos>> distMap = new Int2ObjectOpenHashMap<>();
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

    @Nullable
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

        if (closest == null) return null;
        return IntObjectPair.of((int) Math.sqrt(distance), closest);
    }

    @Nullable
    public Set<BlockPos> getWaterCacheAtDistance(ChunkPos chunkPos, int distance) {
        Int2ObjectOpenHashMap<Set<BlockPos>> byDistance = this.waterDistCache.get(chunkPos.pack());
        return byDistance == null ? null : byDistance.get(distance);
    }

    public SitePos getSiteForPos(BlockPos pos) {
        long chunkPosL = ChunkPos.pack(pos);
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

        this.waterDistCache.computeIfAbsent(
                chunkPosL, (long key) -> new Int2ObjectOpenHashMap<Set<BlockPos>>()
        ).computeIfAbsent(
                siteDistPair.firstInt(), (int dist) -> new ObjectOpenHashSet<BlockPos>()
        ).add(pos);

        return siteDistPair.second();
    }

    public void onBlockUpdate(BlockPos pos, BlockState state) {
        long chunkPosL = ChunkPos.pack(pos);
        int currentUpdates = this.chunkUpdates.getOrDefault(chunkPosL, 0) + 1;
        if (currentUpdates >= WavifyConfig.chunkUpdatesRescanAmount) {
            if (this.rescanChunkPos(new ChunkPos(ChunkPos.getX(chunkPosL), ChunkPos.getZ(chunkPosL)))) {
                currentUpdates = 0;
            }
        }
        this.chunkUpdates.put(chunkPosL, currentUpdates);
    }

    public void rebuild() {
        this.clear();

        this.unscannedChunks.addAll(this.loadedChunks);
        this.chunkScanFuture = null;
        this.checkUnscannedChunks();
    }

    public void calcAllSiteCenters() {
        for (SitePos site : this.sites.values().stream().flatMap(Collection::stream).toList()) {
            site.updateCenter();
        }
    }

    public void cacheSiteSet() {

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
        net.minecraft.world.phys.Vec3 camPos = Minecraft.getInstance().gameRenderer.mainCamera().position();
        ChunkPos cameraChunk = new ChunkPos(((int) Math.floor(camPos.x)) >> 4, ((int) Math.floor(camPos.z)) >> 4);
        double radius = WavifyConfig.chunkRadius * WavifyConfig.chunkRadius;
        Iterator<ChunkPos> iterator = this.unscannedChunks.iterator();
        while (iterator.hasNext()) {
            ChunkPos chunk = iterator.next();
            double dxChunk = cameraChunk.x() - (double) chunk.x();
            double dzChunk = cameraChunk.z() - (double) chunk.z();
            double distance = dxChunk * dxChunk + dzChunk * dzChunk;
            if (distance > radius) continue;

            if (this.unscannedChunkQueue.offer(chunk)) {
                iterator.remove();
            }
        }
    }

    public boolean rescanChunkPos(ChunkPos chunkPos) {
        long chunkPosL = chunkPos.pack();
        this.clearChunk(chunkPosL);
        this.unscannedChunks.add(chunkPos);
        this.checkUnscannedChunks();
        return true;
    }

    public void unloadChunk(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long chunkPosL = chunkPos.pack();
        this.clearChunk(chunkPosL);
        this.chunkUpdates.remove(chunkPosL);
        this.loadedChunks.remove(chunkPos);
        this.unscannedChunks.remove(chunkPos);
    }

    public void clearChunk(long chunkPosL) {
        this.shoreBlocks.remove(chunkPosL);
        this.waterCache.remove(chunkPosL);
        this.waterDistCache.remove(chunkPosL);
        this.waters.remove(chunkPosL);
        this.riverWaters.remove(chunkPosL);

        ObjectOpenHashSet<SitePos> dropped = this.sites.remove(chunkPosL);
        if (dropped != null) this.cachedSiteSet.removeAll(dropped);
    }

    public void clear() {
        this.shoreBlocks.clear();
        this.waterCache.clear();
        this.waterDistCache.clear();
        this.sites.clear();
        this.waters.clear();
        this.riverWaters.clear();
        this.cachedSiteSet.clear();
        this.unscannedChunks.clear();
        this.chunkUpdates.clear();
    }
}
