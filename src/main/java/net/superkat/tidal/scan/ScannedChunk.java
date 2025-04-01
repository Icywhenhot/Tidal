package net.superkat.tidal.scan;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Set;

public class ScannedChunk {
    public final long chunkPos;
    public Set<BlockPos> waters;
    public Set<BlockPos> shorelines;
    public Set<SitePos> sites;

    public ScannedChunk(ChunkPos chunkPos, Set<BlockPos> waters, Set<BlockPos> shorelines, Set<SitePos> sites) {
        this.chunkPos = chunkPos.toLong();
        this.waters = waters;
        this.shorelines = shorelines;
        this.sites = sites;
    }
}
