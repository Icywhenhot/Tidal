package net.superkat.tidal.wave;

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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.WorldChunk;
import net.superkat.tidal.DebugHelper;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;
import net.superkat.tidal.renderer.WaveRenderer;
import net.superkat.tidal.scan.SitePos;
import net.superkat.tidal.scan.WaterHandler;

import java.awt.*;
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
    public final ClientWorld world;
    public WaterHandler waterHandler;
    public WaveRenderer renderer;

    public List<Wave> waves = new ObjectArrayList<>();
    // Set of BlockPos's currently being covered by waves - used for rendering wet overlay
    public Set<BlockPos> coveredBlocks = new ObjectArraySet<>();

    public boolean nearbyChunksLoaded = false;

    public TidalWaveHandler(ClientWorld world) {
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
        tidalTick();

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
    public void tidalTick() {
        if (!this.world.getTickManager().shouldTick()) return;
        double time = this.world.getTime();
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
        int chunkRadius = 3;
        int spawnBlockRadius = 1;

        ChunkPos playerChunk = MinecraftClient.getInstance().player.getChunkPos();
        ChunkPos start = new ChunkPos(playerChunk.x + chunkRadius, playerChunk.z + chunkRadius);
        ChunkPos end = new ChunkPos(playerChunk.x - chunkRadius, playerChunk.z - chunkRadius);
        Set<BlockPos> waterBlocks = ChunkPos.stream(start, end).map(chunkPos -> this.waterHandler.getWaterCacheAtDistance(chunkPos, distFromShore)).filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toSet());
        if (waterBlocks.isEmpty()) return;
        spawnWaves(waterBlocks);

        if (DebugHelper.debug()) {
            if (DebugHelper.holdingSpyglass()) debugWaveParticles(waterBlocks);
            if (DebugHelper.offhandClock()) {
                for (BlockPos water : waterBlocks) {
                    Vec3d pos = water.toCenterPos();
                    this.world.addParticle(ParticleTypes.END_ROD, pos.getX(), pos.getY() + 2.5, pos.getZ(), 0, 0, 0);
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

            if (world.getBiome(spawnPos).isIn(BiomeTags.IS_RIVER)) bigWave = false;

            Wave wave = new Wave(this.world, spawnPos, yaw, yOffset, bigWave);
            int width = (int) MathHelper.clamp(connected.size() * 1.5, 1, 3);
            wave.setWidth(width);
            this.waves.add(wave);
        }
    }

    private void spawnWavesFromConnected(List<BlockPos> waters) {
        int spawned = 0;
        int maxLength = 3;
        for (int i = 0; i < waters.size(); i++) {
            if (i % maxLength != 0) continue;
            BlockPos spawnPos = waters.get(i).add(0, 1, 0);
            SitePos site = this.waterHandler.getSiteForPos(spawnPos);
            float yaw = site.getYaw();

            boolean bigWave = site.xList.size() >= 100;

            spawned++;
            float yOffset = MathHelper.sin(spawned) / 16f + 0.65f;
//            BlockPos spawnPos = connected.stream().sorted(Comparator.comparingInt(Vec3i::getZ)).toList().get(connected.size() / 2).add(0, 1, 0);

            BlockPos beneath = spawnPos.add(0, -1, 0);
            if (world.isAir(beneath) || !world.getBlockState(beneath).getFluidState().isStill()) continue;

            if (world.getBiome(spawnPos).isIn(BiomeTags.IS_RIVER)) bigWave = false;

//            this.world.addParticle(ParticleTypes.WITCH, spawnPos.getX() + 0.5, spawnPos.getY() + 2, spawnPos.getZ() + 0.5, 0, 0, 0);

            Wave wave = new Wave(this.world, spawnPos, yaw, yOffset, bigWave);
            int width = (int) MathHelper.clamp(waters.size() * 1.5, 1, 3);
            wave.setWidth(width);
            this.waves.add(wave);
            this.world.addParticle(ParticleTypes.CHERRY_LEAVES, spawnPos.getX() + 0.5, spawnPos.getY() + 5, spawnPos.getZ() + 0.5, 0, 0, 0);
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
        Color color = Color.WHITE; // activates the movement particle's custom colors
//        Color color = Color.LIGHT_GRAY; //deactivates the movement particle's custom colors
        boolean farParticles = false;

        for (BlockPos water : waterBlocks) {
            SitePos site = this.waterHandler.getSiteForPos(water);
            if (site == null || !site.yawCalculated) continue;
//            if(site.xList.size() < 50) continue;

            DebugWaveMovementParticle.DebugWaveMovementParticleEffect particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(
                    Vec3d.unpackRgb(color.getRGB()).toVector3f(),
                    1f,
                    site.getYaw(),
                    0.3f,
                    20);
            this.world.addParticle(particleEffect, farParticles, water.getX(), water.getY() + 2, water.getZ(), 0, 0, 0);
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
     * @return The tidal wave chunk radius - pulls from either the Tidal config or the server's render distance(whichever one is smaller).
     */
    public int getChunkRadius() {
        MinecraftClient client = MinecraftClient.getInstance();
        int configRadius = TidalConfig.chunkRadius;
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

    public void debugChunkDirectionParticles(long chunkPosL, boolean farParticles) {
        Color color = Color.WHITE; // activates the movement particle's custom colors
//        Color color = Color.LIGHT_GRAY; //deactivates the movement particle's custom colors

        Map<BlockPos, SitePos> map = this.waterHandler.waterCache.get(chunkPosL);
        if (map == null) return;

        for (Map.Entry<BlockPos, SitePos> entry : map.entrySet()) {
            BlockPos pos = entry.getKey();
            SitePos sitePos = entry.getValue();
            if (sitePos == null || !sitePos.yawCalculated) continue;
            DebugWaveMovementParticle.DebugWaveMovementParticleEffect particleEffect = new DebugWaveMovementParticle.DebugWaveMovementParticleEffect(
                    Vec3d.unpackRgb(color.getRGB()).toVector3f(),
                    1f,
                    sitePos.getYaw(),
                    0.3f,
                    20);
            this.world.addParticle(particleEffect, farParticles, pos.getX(), pos.getY() + 2, pos.getZ(), 0, 0, 0);
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
