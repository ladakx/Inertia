package com.ladakx.inertia.utils.chunk;

import org.bukkit.Chunk;

/**
 * Utility class for working with chunk coordinates
 */
public class ChunkUtils {
    /**
     * Mask for the lower 32 bits of a long
     */
    private static final long MASK_32_BITS = 0xFFFFFFFFL;

    /**
     * Encodes chunkX and chunkZ into a 64-bit key
     * @param chunkX The x-coordinate of the chunk
     * @param chunkZ The z-coordinate of the chunk
     * @return The 64-bit key
     */
    public static long getChunkKey(int chunkX, int chunkZ) {
        // Each coordinate is masked to avoid sign-extension issues if negative
        return (chunkX & MASK_32_BITS) | ((chunkZ & MASK_32_BITS) << 32);
    }

    /**
     * Encodes a chunk into a 64-bit key
     * @param chunk The chunk to encode
     * @return The 64-bit key
     */
    public static long getChunkKey(Chunk chunk) {
        // Each coordinate is masked to avoid sign-extension issues if negative
        return (chunk.getX() & MASK_32_BITS) | ((chunk.getZ() & MASK_32_BITS) << 32);
    }

    /**
     * Decodes chunkX from a 64-bit key
     * @param chunkKey The 64-bit key
     * @return The x-coordinate of the chunk
     */
    public static int getChunkX(long chunkKey) {
        // Cast the lower 32 bits back to int
        return (int)(chunkKey & MASK_32_BITS);
    }

    /**
     * Decodes chunkZ from a 64-bit key
     * @param chunkKey The 64-bit key
     * @return The z-coordinate of the chunk
     */
    public static int getChunkZ(long chunkKey) {
        // Shift right 32 bits, then cast to int
        return (int)((chunkKey >>> 32) & MASK_32_BITS);
    }
}
