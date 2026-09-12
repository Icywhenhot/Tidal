package net.superkat.wavify.wave;

import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.WorldChunk;
import net.superkat.wavify.Wavify;
import net.superkat.wavify.config.WavifyConfig;
import net.superkat.wavify.renderer.WaveRenderer;
import net.superkat.wavify.river.RiverFlow;
import net.superkat.wavify.river.RiverFlowField;
import net.superkat.wavify.scan.SitePos;
import net.superkat.wavify.scan.WaterHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class WavifyWaveHandler {
    public final ClientWorld world;
    public WaterHandler waterHandler;
    public WaveRenderer renderer;

    public List<Wave> waves = new ObjectArrayList<>();

    public Set<BlockPos> coveredBlocks = new ObjectArraySet<>();
    private final RiverSpawner rivers;

    public boolean nearbyChunksLoaded = false;

    public WavifyWaveHandler(ClientWorld world) {
        this.world = world;
        this.waterHandler = new WaterHandler(this, world);
        this.renderer = new WaveRenderer(this, world);
        this.rivers = new RiverSpawner(world, this.waterHandler);
    }

    public void reloadNearbyChunks() {
        this.nearbyChunksLoaded = false;
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if (!this.nearbyChunksLoaded) {
            this.nearbyChunksLoaded = nearbyChunksLoaded(player);
        }

        this.waterHandler.tick();
        wavifyTick();
    }

    public void render(BufferBuilder buffer, WorldRenderContext context) {
        this.renderer.render(buffer, context);
    }

    public void wavifyTick() {
        if (!this.world.getTickManager().shouldTick()) return;
        long time = this.world.getTime();
        if (WavifyConfig.enableOceanWaves && time % 80 == 0) {
            spawnAllWaves();
        }

        this.rivers.tick(this.waves, MinecraftClient.getInstance().player, time);

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

        ChunkPos playerChunk = MinecraftClient.getInstance().player.getChunkPos();
        ChunkPos start = new ChunkPos(playerChunk.x + chunkRadius, playerChunk.z + chunkRadius);
        ChunkPos end = new ChunkPos(playerChunk.x - chunkRadius, playerChunk.z - chunkRadius);
        Set<BlockPos> waterBlocks = ChunkPos.stream(start, end)
                .map(chunkPos -> this.waterHandler.getWaterCacheAtDistance(chunkPos, distFromShore))
                .filter(map -> map != null)
                .flatMap(Collection::stream)
                .filter(water -> !RiverFlow.isRiverWater(this.world, water))
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
            float yOffset = MathHelper.sin(spawned) / 16f + 0.65f;
            BlockPos spawnPos = connected.stream().sorted(Comparator.comparingInt(Vec3i::getZ)).toList().get(connected.size() / 2).add(0, 1, 0);

            BlockPos beneath = spawnPos.add(0, -1, 0);
            if (world.isAir(beneath) || !world.getBlockState(beneath).getFluidState().isStill()) continue;

            if (RiverFlow.isRiverWater(this.world, spawnPos)) continue;

            if (!ShoreCheck.isOceanConnected(this.world, spawnPos, yaw)) continue;
            if (!ShoreCheck.isPathSafe(this.world, spawnPos, yaw)) continue;
            if (ShoreCheck.isIsolatedObject(this.world, site)) continue;

            Wave wave = new Wave(this.world, spawnPos, yaw, yOffset, bigWave);
            int width = (int) MathHelper.clamp(connected.size() * 1.5, 1, 3);
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
            for (BlockPos check : BlockPos.iterate(water.add(-1, 0, -1), water.add(1, 0, 1))) {
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

    public boolean nearbyChunksLoaded(ClientPlayerEntity player) {
        if (nearbyChunksLoaded) return true;
        int chunkRadius = getChunkRadius();

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

    }

    public int getChunkRadius() {
        MinecraftClient client = MinecraftClient.getInstance();
        int configRadius = WavifyConfig.chunkRadius;
        int serverRadius = client.options.serverViewDistance;

        return Math.min(configRadius, serverRadius);
    }

    public static Random getRandom() {
        return Random.create();
    }

    public static boolean posIsWater(ClientWorld world, BlockPos pos) {
        FluidState state = world.getFluidState(pos);
        return state.isIn(FluidTags.WATER);
    }

    public static boolean stateIsWater(BlockState state) {
        return state.getFluidState().isIn(FluidTags.WATER);
    }

}
