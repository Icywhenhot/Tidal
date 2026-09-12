package net.superkat.wavify.wave;

import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.LevelChunk;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.mixin.OptionsAccessor;
import net.superkat.wavify.renderer.WaveRenderer;
import net.superkat.wavify.river.RiverFlow;
import net.superkat.wavify.scan.SitePos;
import net.superkat.wavify.scan.WaterHandler;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class WavifyWaveHandler {
    public final ClientLevel level;
    public WaterHandler waterHandler;
    public WaveRenderer renderer;

    public List<Wave> waves = new ObjectArrayList<>();

    public Set<BlockPos> coveredBlocks = new ObjectArraySet<>();

    private final RiverSpawner rivers;

    public boolean nearbyChunksLoaded = false;

    public WavifyWaveHandler(ClientLevel level) {
        this.level = level;
        this.waterHandler = new WaterHandler(this, level);
        this.renderer = new WaveRenderer(this, level);
        this.rivers = new RiverSpawner(level, this.waterHandler);
    }

    public void reloadNearbyChunks() {
        this.nearbyChunksLoaded = false;
    }

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        assert player != null;

        if (!this.nearbyChunksLoaded) {
            this.nearbyChunksLoaded = nearbyChunksLoaded(player);
        }

        this.waterHandler.tick();
        wavifyTick();
    }

    public void render(MultiBufferSource bufferSource, RenderType layer) {
        this.renderer.render(bufferSource, layer);
    }

    public void wavifyTick() {
        if (Minecraft.getInstance().isPaused()) return;
        long time = this.level.getGameTime();

        if (WavifyConfig.enableOceanWaves && time % 80 == 0) {
            spawnAllWaves();
        }

        this.rivers.tick(this.waves, Minecraft.getInstance().player, time);

        boolean updateCoveredBlocks = WavifyConfig.enableWetOverlay && time % 10 == 0;
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

        if (updateCoveredBlocks) {
            this.coveredBlocks = updatedCovered;
        } else if (!WavifyConfig.enableWetOverlay && !this.coveredBlocks.isEmpty()) {
            this.coveredBlocks = updatedCovered;
        }
    }

    public void spawnAllWaves() {
        int distFromShore = WavifyConfig.spawnDistance;
        int chunkRadius = WavifyConfig.chunkRadius - 2;

        ChunkPos playerChunk = Minecraft.getInstance().player.chunkPosition();
        ChunkPos start = new ChunkPos(playerChunk.x + chunkRadius, playerChunk.z + chunkRadius);
        ChunkPos end = new ChunkPos(playerChunk.x - chunkRadius, playerChunk.z - chunkRadius);
        Set<BlockPos> waterBlocks = ChunkPos.rangeClosed(start, end)
                .map(chunkPos -> this.waterHandler.getWaterCacheAtDistance(chunkPos, distFromShore))
                .filter(map -> map != null)
                .flatMap(Collection::stream)
                .filter(water -> !RiverFlow.isRiverWater(this.level, water))
                .collect(ObjectArraySet::new, Set::add, Set::addAll);
        if (!waterBlocks.isEmpty()) spawnWaves(waterBlocks);

    }

    public void spawnWaves(Set<BlockPos> waterBlocks) {
        Set<BlockPos> visited = Sets.newHashSet();
        int spawned = 0;

        for (BlockPos water : waterBlocks) {
            if (visited.contains(water)) continue;
            SitePos site = this.waterHandler.getSiteForPos(water);
            if (site == null || !site.yawCalculated) continue;
            if (site.posCount() < 50) continue;

            float yaw = site.getYaw();
            Set<BlockPos> connected = findConnected(water, yaw, waterBlocks, visited);
            visited.addAll(connected);

            boolean bigWave = site.posCount() >= 100;

            spawned++;
            float yOffset = Mth.sin(spawned) / 16f + 0.65f;
            BlockPos spawnPos = connected.stream().sorted(Comparator.comparingInt(Vec3i::getZ)).toList().get(connected.size() / 2).offset(0, 1, 0);

            BlockPos beneath = spawnPos.offset(0, -1, 0);
            if (this.level.isEmptyBlock(beneath) || !this.level.getBlockState(beneath).getFluidState().isSource()) continue;

            if (RiverFlow.isRiverWater(this.level, spawnPos)) continue;

            if (!ShoreCheck.isOceanConnected(this.level, spawnPos, yaw)) continue;
            if (!ShoreCheck.isPathSafe(this.level, spawnPos, yaw)) continue;
            if (ShoreCheck.isIsolatedObject(this.level, site)) continue;

            Wave wave = new Wave(this.level, spawnPos, yaw, yOffset, bigWave);
            int width = (int) Mth.clamp(connected.size() * 1.5, 1, 3);
            wave.setWidth(width);
            this.waves.add(wave);
        }
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
                if (check.equals(water)) continue;
                if (ignoreSet.contains(check)) continue;
                if (!waterBlocks.contains(check)) continue;

                SitePos site = this.waterHandler.getSiteForPos(check);
                if (site == null || !site.yawCalculated || site.posCount() < 50) continue;
                if (Math.abs(site.yaw - yaw) > 15) continue;
                stack.add(new BlockPos(check));
            }

            if (stack.isEmpty()) break;
        }
        return connected;
    }

    public List<Wave> getWaves() {
        return this.waves;
    }

    public boolean nearbyChunksLoaded(LocalPlayer player) {
        if (nearbyChunksLoaded) return true;
        int chunkRadius = getChunkRadius();

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
    }

    public int getChunkRadius() {
        Minecraft client = Minecraft.getInstance();
        int configRadius = WavifyConfig.chunkRadius;
        int serverRadius = ((OptionsAccessor) client.options).wavify$getServerRenderDistance();

        return Math.min(configRadius, serverRadius);
    }

    public static boolean posIsWater(ClientLevel level, BlockPos pos) {
        FluidState state = level.getFluidState(pos);
        return state.is(FluidTags.WATER);
    }

    public static boolean stateIsWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

}
