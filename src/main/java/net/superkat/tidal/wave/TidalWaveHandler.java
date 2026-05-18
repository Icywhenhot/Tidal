package net.superkat.tidal.wave;

import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.vertex.BufferBuilder;
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
import net.superkat.tidal.DebugHelper;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;
import net.superkat.tidal.renderer.WaveRenderer;
import net.superkat.tidal.scan.SitePos;
import net.superkat.tidal.scan.WaterHandler;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The main handler, used for spawning/handling tidal waves, as well as ticking the world's {@link WaterHandler}
 */
public class TidalWaveHandler {
    public final ClientLevel level;
    public WaterHandler waterHandler;
    public WaveRenderer renderer;

    public List<Wave> waves = new ObjectArrayList<>();
    // Set of BlockPos's currently being covered by waves - used for rendering wet overlay
    public Set<BlockPos> coveredBlocks = new ObjectArraySet<>();

    public boolean nearbyChunksLoaded = false;

    public TidalWaveHandler(ClientLevel level) {
        this.level = world;
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
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        assert player != null;

        if (!this.nearbyChunksLoaded) {
            this.nearbyChunksLoaded = nearbyChunksLoaded(player);
        }

        this.waterHandler.tick();
        tidalTick();

        if (DebugHelper.debug()) {
            debugTick(client, player);
        }

    }

    public void render(BufferBuilder buffer, LevelRenderContext context) {
        this.renderer.render(buffer, context);
    }

    /**
     * Tick method for waves
     */
    public void tidalTick() {
        if (!this.level.tickRateManager().runsNormally()) return;
        double time = this.level.getGameTime();
        if (time % 80 == 0) {
            spawnAllWaves();
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
        int distFromShore = TidalConfig.waveDistFromShore;
        int chunkRadius = TidalConfig.chunkRadius - 2;

        ChunkPos playerChunk = Minecraft.getInstance().player.chunkPosition();
        ChunkPos start = new ChunkPos(playerChunk.x() + chunkRadius, playerChunk.z() + chunkRadius);
        ChunkPos end = new ChunkPos(playerChunk.x() - chunkRadius, playerChunk.z() - chunkRadius);
        Set<BlockPos> waterBlocks = ChunkPos.rangeClosed(start, end).map(chunkPos -> this.waterHandler.getWaterCacheAtDistance(chunkPos, distFromShore)).filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toSet());
        if (waterBlocks.isEmpty()) return;
        spawnWaves(waterBlocks);

        if (DebugHelper.debug()) {
            if (DebugHelper.holdingSpyglass()) debugWaveParticles(waterBlocks);
            if (DebugHelper.offhandClock()) {
                for (BlockPos water : waterBlocks) {
                    Vec3 pos = water.getCenter();
                    this.level.addParticle(ParticleTypes.END_ROD, pos.getX(), pos.getY() + 2.5, pos.getZ(), 0, 0, 0);
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
            if (world.isEmptyBlock(beneath) || !world.getBlockState(beneath).getFluidState().isSource()) continue;

            if (world.getBiome(spawnPos).is(BiomeTags.IS_RIVER)) bigWave = false;

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
            this.level.addParticle(particleEffect, farParticles, false, water.getX(), water.getY() + 2, water.getZ(), 0, 0, 0);
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
                world.getChunk(chunkX + chunkRadius, chunkZ),
                world.getChunk(chunkX - chunkRadius, chunkZ),
                world.getChunk(chunkX, chunkZ + chunkRadius),
                world.getChunk(chunkX, chunkZ - chunkRadius),
                world.getChunk(chunkX + (chunkRadiusReduced), chunkZ + (chunkRadiusReduced)),
                world.getChunk(chunkX - (chunkRadiusReduced), chunkZ + (chunkRadiusReduced)),
                world.getChunk(chunkX - (chunkRadiusReduced), chunkZ - (chunkRadiusReduced)),
                world.getChunk(chunkX + (chunkRadiusReduced), chunkZ - (chunkRadiusReduced))
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
     * @return The tidal wave chunk radius - pulls from either the Tidal config or the server's render distance(whichever one is smaller).
     */
    public int getChunkRadius() {
        Minecraft client = Minecraft.getInstance();
        int configRadius = TidalConfig.chunkRadius;
        int serverRadius = client.options.serverViewDistance;

        return Math.min(configRadius, serverRadius);
    }

    /**
     * @return The loaded chunk radius - used for figuring out all loaded chunks on the client.
     */
    public int getLoadedChunkRadius() {
        Minecraft client = Minecraft.getInstance();
        int loadRadius = client.options.serverViewDistance;
        return Math.max(2, loadRadius) + 3;
    }

    public void debugTick(Minecraft client, LocalPlayer player) {
        // show water direction of water blocks
        if (DebugHelper.holdingCompass() || DebugHelper.offhandCompass()) {
            if (!this.waterHandler.built) return;

            ChunkPos playerChunk = player.chunkPosition();
            if (DebugHelper.offhandCompass()) {
                if (client.level.getGameTime() % 5 != 0) return;
                int radius = 2;
                ChunkPos start = new ChunkPos(playerChunk.x() + radius, playerChunk.z() + radius);
                ChunkPos end = new ChunkPos(playerChunk.x() - radius, playerChunk.z() - radius);
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

            BlockPos playerPos = player.getBlockPos();

            List<BlockPos> scannedBlocks = this.waterHandler.waterCache.values().stream().flatMap(map -> map.keySet().stream()).toList();
            if (scannedBlocks.contains(playerPos)) {
                long chunkPosL = new ChunkPos(playerPos).toLong();
                SitePos site = this.waterHandler.waterCache.get(chunkPosL).get(playerPos);
//                System.out.println(world.getBiome(site.getPos()).is(BiomeTags.IS_RIVER));
                System.out.println(site.xList.size());
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
            this.level.addParticle(particleEffect, farParticles, false, pos.getX(), pos.getY() + 2, pos.getZ(), 0, 0, 0);
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
        long time = Minecraft.getInstance().world.getGameTime();
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
        FluidState state = world.getFluidState(pos);
        return state.is(FluidTags.WATER);
    }

    public static boolean stateIsWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

}
