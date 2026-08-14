package net.superkat.wavify.scan;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.superkat.wavify.wave.WavifyWaveHandler;
import org.apache.commons.compress.utils.Lists;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChunkScanner {
    public final WaterHandler handler;
    public final ClientLevel level;

    public Map<BlockPos, Boolean> cachedBlocks = new Object2ObjectOpenHashMap<>();

    public Set<BlockPos> visitedBlocks = new ObjectOpenHashSet<>();

    public Iterator<BlockPos> cachedIterator = null;

    public int shorelinesSinceSite = 0;

    public ChunkPos chunkPos;

    public ObjectOpenHashSet<BlockPos> waters = new ObjectOpenHashSet<>();
    public ObjectOpenHashSet<BlockPos> shorelines = new ObjectOpenHashSet<>();
    public ObjectOpenHashSet<SitePos> sites = new ObjectOpenHashSet<>();

    public ChunkScanner(WaterHandler handler, ClientLevel level, ChunkPos chunkPos) {
        this.handler = handler;
        this.level = level;
        this.chunkPos = chunkPos;
        BlockPos startPos = chunkPos.getWorldPosition();
        BlockPos endPos = startPos.offset(15, 0, 15);
        this.cachedIterator = stack(startPos, endPos);
    }

    public ScannedChunk scan() {
        BlockPos startPos = chunkPos.getWorldPosition();
        BlockPos endPos = startPos.offset(15, 0, 15);
        for (BlockPos pos : BlockPos.betweenClosed(startPos, endPos)) {
            int y = sampleHeightmap(pos) - 1;
            scanPos(new BlockPos(pos.getX(), y, pos.getZ()));
        }

        return new ScannedChunk(this.chunkPos, this.waters, this.shorelines, this.sites);
    }

    private int sampleHeightmap(BlockPos pos) {
        return this.level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
    }

    public Iterator<BlockPos> stack(BlockPos startPos, BlockPos endPos) {
        cachedIterator = BlockPos.betweenClosed(startPos, endPos).iterator();
        return cachedIterator;
    }

    public void scanPos(BlockPos pos) {
        if(visitedBlocks.contains(pos)) return;
        if(this.level.isEmptyBlock(pos)) return;

        boolean posIsWater = cacheAndIsWater(pos);
        visitedBlocks.add(pos);

        List<BlockPos> nonWaterBlocks = Lists.newArrayList();
        List<BlockPos> waterBlocks = Lists.newArrayList();
        if (posIsWater) waterBlocks.add(pos); else nonWaterBlocks.add(pos);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos checkPos = pos.relative(direction);
            if(this.level.isEmptyBlock(checkPos)) continue;
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
        return cachedBlocks.computeIfAbsent(pos, pos1 -> WavifyWaveHandler.posIsWater(this.level, pos1));
    }
}
