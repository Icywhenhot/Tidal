package net.superkat.tidal.scan;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.superkat.tidal.wave.TidalWaveHandler;
import org.apache.commons.compress.utils.Lists;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans a chunk for water blocks and shoreline blocks.
 * @see WaterHandler
 */
public class ChunkScanner {
    public final WaterHandler handler;
    public final ClientWorld world;

    //blocks which have been checked to be water or not water
    public Map<BlockPos, Boolean> cachedBlocks = new Object2ObjectOpenHashMap<>();

    //blocks which have had their neighbours checked(scanned) as water or not water, and added to water body/shoreline
    public Set<BlockPos> visitedBlocks = new ObjectOpenHashSet<>();

    //cached iterator idk
    public Iterator<BlockPos> cachedIterator = null;

    //amount of shoreline blocks since the last created SitePos
    public int shorelinesSinceSite = 0;

    public ChunkPos chunkPos;

    public ObjectOpenHashSet<BlockPos> waters = new ObjectOpenHashSet<>();
    public ObjectOpenHashSet<BlockPos> shorelines = new ObjectOpenHashSet<>();
    public ObjectOpenHashSet<SitePos> sites = new ObjectOpenHashSet<>();

    //TODO(unimportant for now) - scan above and below for water to jumps in the water
    public ChunkScanner(WaterHandler handler, ClientWorld world, ChunkPos chunkPos) {
        this.handler = handler;
        this.world = world;
        this.chunkPos = chunkPos;
        BlockPos startPos = chunkPos.getStartPos();
        BlockPos endPos = startPos.add(15, 0, 15);
        this.cachedIterator = stack(startPos, endPos);
    }

    public ScannedChunk scan() {
        BlockPos startPos = chunkPos.getStartPos();
        BlockPos endPos = startPos.add(15, 0, 15);
        for (BlockPos pos : BlockPos.iterate(startPos, endPos)) {
            int y = sampleHeightmap(pos) - 1;
            scanPos(pos.withY(y));
        }

        return new ScannedChunk(this.chunkPos, this.waters, this.shorelines, this.sites);
    }

    private int sampleHeightmap(BlockPos pos) {
        return this.world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
    }

    /**
     * @return An iterator which represents the next blocks in line to be scanned
     */
    public Iterator<BlockPos> stack(BlockPos startPos, BlockPos endPos) {
        cachedIterator = BlockPos.iterate(startPos, endPos).iterator();
        return cachedIterator;
    }

    /**
     * Scan a block to be water or not water.
     * <br><br>If not already visited, the immediately surrounding neighbors are also checked for water and cached in a "+" shape. The corners for a square are NOT scanned.
     *
     * @param pos Block pos to scan
     */
    public void scanPos(BlockPos pos) {
        //if already visited or is air -> return
        if(visitedBlocks.contains(pos)) return;
        if(world.isAir(pos)) return;

        //mark visited
        boolean posIsWater = cacheAndIsWater(pos);
        visitedBlocks.add(pos);

        //shorelines need to be checked for still
        List<BlockPos> nonWaterBlocks = Lists.newArrayList();
        List<BlockPos> waterBlocks = Lists.newArrayList();
        if (posIsWater) waterBlocks.add(pos); else nonWaterBlocks.add(pos); //they keep getting more cursed

        //check and cache neighbours
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos checkPos = pos.offset(direction);
            if(world.isAir(checkPos)) continue;
            if(direction == Direction.NORTH && pos.getZ() % 16 == 0) continue;
            if(direction == Direction.WEST && pos.getX() % 16 == 0) continue;
            if(direction == Direction.SOUTH && (pos.getZ() - 1) % 16 == 0) continue;
            if(direction == Direction.EAST && (pos.getX() + 1) % 16 == 0) continue;

            boolean neighborIsWater = cacheAndIsWater(checkPos);
            //that is super cursed but okay - no that's actually incredibly cursed(wow I spelt that right first try)
            //if init scan pos is water OR if the check pos is the top of water
            if(neighborIsWater) waterBlocks.add(checkPos);
            else nonWaterBlocks.add(checkPos);
        }

        //no water blocks should be queued from scanned non-water blocks
        if(waterBlocks.isEmpty() || !posIsWater) return;

        //shoreline creation - neighbouring water blocks scan shoreline blocks and add them
        if(!nonWaterBlocks.isEmpty()) {
            this.shorelines.addAll(nonWaterBlocks);

            this.shorelinesSinceSite += nonWaterBlocks.size();
            if(this.shorelinesSinceSite >= 8) {
                this.sites.add(new SitePos(pos));
                this.shorelinesSinceSite = 0;
            }
        }

        this.waters.addAll(waterBlocks);
    }

    /**
     * Cache a block and return if it is water, all in the same method!
     *
     * @param pos BlockPos to cache and check if its water
     * @return Returns if the BlockPos is water or not - NOT if the block was cached successfully(!!!), as you'd normally expect from a method like this
     */
    public boolean cacheAndIsWater(BlockPos pos) {
        return cachedBlocks.computeIfAbsent(pos, pos1 -> TidalWaveHandler.posIsWater(world, pos1));
    }
}
