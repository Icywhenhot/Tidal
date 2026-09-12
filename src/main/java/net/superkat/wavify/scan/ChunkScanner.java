package net.superkat.wavify.scan;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.superkat.wavify.river.RiverFlow;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.apache.commons.compress.utils.Lists;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChunkScanner {
    public final WaterHandler handler;
    public final ClientWorld world;

    public Map<BlockPos, Boolean> cachedBlocks = new Object2ObjectOpenHashMap<>();

    public Set<BlockPos> visitedBlocks = new ObjectOpenHashSet<>();

    public Iterator<BlockPos> cachedIterator = null;

    public int shorelinesSinceSite = 0;

    public ChunkPos chunkPos;

    public ObjectOpenHashSet<BlockPos> waters = new ObjectOpenHashSet<>();
    public ObjectOpenHashSet<BlockPos> rivers = new ObjectOpenHashSet<>();
    public ObjectOpenHashSet<BlockPos> shorelines = new ObjectOpenHashSet<>();
    public ObjectOpenHashSet<SitePos> sites = new ObjectOpenHashSet<>();

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

        for (BlockPos water : this.waters) {
            if (RiverFlow.isRiverWater(this.world, water)) this.rivers.add(water);
        }

        return new ScannedChunk(this.chunkPos, this.waters, this.rivers, this.shorelines, this.sites);
    }

    private int sampleHeightmap(BlockPos pos) {
        return this.world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
    }

    public Iterator<BlockPos> stack(BlockPos startPos, BlockPos endPos) {
        cachedIterator = BlockPos.iterate(startPos, endPos).iterator();
        return cachedIterator;
    }

    public void scanPos(BlockPos pos) {

        if(visitedBlocks.contains(pos)) return;
        if(world.isAir(pos)) return;

        boolean posIsWater = cacheAndIsWater(pos);
        visitedBlocks.add(pos);

        List<BlockPos> nonWaterBlocks = Lists.newArrayList();
        List<BlockPos> waterBlocks = Lists.newArrayList();
        if (posIsWater) waterBlocks.add(pos); else nonWaterBlocks.add(pos);

        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos checkPos = pos.offset(direction);
            if(world.isAir(checkPos)) continue;
            if(direction == Direction.NORTH && pos.getZ() % 16 == 0) continue;
            if(direction == Direction.WEST && pos.getX() % 16 == 0) continue;
            if(direction == Direction.SOUTH && (pos.getZ() - 1) % 16 == 0) continue;
            if(direction == Direction.EAST && (pos.getX() + 1) % 16 == 0) continue;

            boolean neighborIsWater = cacheAndIsWater(checkPos);

            if(neighborIsWater) waterBlocks.add(checkPos);
            else nonWaterBlocks.add(checkPos);
        }

        if(waterBlocks.isEmpty() || !posIsWater) return;

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

    public boolean cacheAndIsWater(BlockPos pos) {
        return cachedBlocks.computeIfAbsent(pos, pos1 -> WavifyWaveHandler.posIsWater(world, pos1));
    }
}
