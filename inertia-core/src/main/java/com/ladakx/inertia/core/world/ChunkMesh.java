package com.ladakx.inertia.core.world;

/**
 * Represents the physical mesh of a single Minecraft chunk.
 * In our current simple strategy, it holds the ID of the single compound body for the chunk.
 */
public class ChunkMesh {

    private final long chunkKey;
    private final int bodyId;

    public ChunkMesh(long chunkKey, int bodyId) {
        this.chunkKey = chunkKey;
        this.bodyId = bodyId;
    }

    public long getChunkKey() {
        return chunkKey;
    }

    public int getBodyId() {
        return bodyId;
    }
}

