package net.superkat.wavify.scan;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.apache.commons.compress.utils.Lists;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

// digs through a chunk looking for water and shoreline blocks
public class ChunkScanner {
    public final WaterHandler handler;
    public final ClientWorld world;

    // blocks we've already decided are water or not
    public Map<BlockPos, Boolean> cachedBlocks = new Object2ObjectOpenHashMap<>();

    // blocks whose neighbours we've been through and sorted into water or shoreline
    public Set<BlockPos> visitedBlocks = new ObjectOpenHashSet<>();

    // cached iterator idk
    public Iterator<BlockPos> cachedIterator = null;

    // shoreline blocks piled up since we last made a site
    public int shorelinesSinceSite = 0;

    public ChunkPos chunkPos;

    public ObjectOpenHashSet<BlockPos> waters = new ObjectOpenHashSet<>();
    public ObjectOpenHashSet<BlockPos> shorelines = new ObjectOpenHashSet<>();
    public ObjectOpenHashSet<SitePos> sites = new ObjectOpenHashSet<>();

    // todo, not urgent: scan up and down too so we catch jumps in the water
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

    // next blocks in line to get scanned
    public Iterator<BlockPos> stack(BlockPos startPos, BlockPos endPos) {
        cachedIterator = BlockPos.iterate(startPos, endPos).iterator();
        return cachedIterator;
    }

    // water or not water, and if we haven't seen it before the neighbours get checked too
    // only in a plus shape though, corners don't get looked at
    public void scanPos(BlockPos pos) {
        // seen it already or it's air, nothing to do
        if(visitedBlocks.contains(pos)) return;
        if(world.isAir(pos)) return;

        // mark visited
        boolean posIsWater = cacheAndIsWater(pos);
        visitedBlocks.add(pos);

        // still need to work out shorelines
        List<BlockPos> nonWaterBlocks = Lists.newArrayList();
        List<BlockPos> waterBlocks = Lists.newArrayList();
        if (posIsWater) waterBlocks.add(pos); else nonWaterBlocks.add(pos); // they keep getting more cursed

        // check and cache neighbours
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos checkPos = pos.offset(direction);
            if(world.isAir(checkPos)) continue;
            if(direction == Direction.NORTH && pos.getZ() % 16 == 0) continue;
            if(direction == Direction.WEST && pos.getX() % 16 == 0) continue;
            if(direction == Direction.SOUTH && (pos.getZ() - 1) % 16 == 0) continue;
            if(direction == Direction.EAST && (pos.getX() + 1) % 16 == 0) continue;

            boolean neighborIsWater = cacheAndIsWater(checkPos);
            // that is super cursed but okay, no that's actually incredibly cursed
            // true if the starting pos is water or the checked pos is the top of some water
            if(neighborIsWater) waterBlocks.add(checkPos);
            else nonWaterBlocks.add(checkPos);
        }

        // don't queue water off the back of a non water block
        if(waterBlocks.isEmpty() || !posIsWater) return;

        // make shorelines, water blocks next door pick up the dry ones and add them
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

    // caches a block and tells you if it's water, both at once
    // returns whether it's water, not whether the caching worked, which is what you'd probably expect
    public boolean cacheAndIsWater(BlockPos pos) {
        return cachedBlocks.computeIfAbsent(pos, pos1 -> WavifyWaveHandler.posIsWater(world, pos1));
    }
}
