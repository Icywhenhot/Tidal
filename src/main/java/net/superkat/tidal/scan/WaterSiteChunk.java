package net.superkat.tidal.scan;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Set;

public class WaterSiteChunk {
    public final long chunkPos;
    public Map<BlockPos, SitePos> waterSiteMap = new Object2ObjectOpenHashMap<>();
    public Map<Integer, Set<BlockPos>> distWaterMap = new Int2ObjectOpenHashMap<>();

    public WaterSiteChunk(long chunkPos, Map<BlockPos, SitePos> waterSiteMap, Map<Integer, Set<BlockPos>> distWaterMap) {
        this.chunkPos = chunkPos;
        this.waterSiteMap = waterSiteMap;
        this.distWaterMap = distWaterMap;
    }

    public WaterSiteChunk(long chunkPos) {
        this.chunkPos = chunkPos;
    }

}
