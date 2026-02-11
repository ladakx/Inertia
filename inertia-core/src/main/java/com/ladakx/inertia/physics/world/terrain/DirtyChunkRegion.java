package com.ladakx.inertia.physics.world.terrain;

public record DirtyChunkRegion(int minSectionY,
                               int maxSectionY,
                               int minX,
                               int minY,
                               int minZ,
                               int maxX,
                               int maxY,
                               int maxZ) {

    public DirtyChunkRegion {
        if (minSectionY > maxSectionY) {
            throw new IllegalArgumentException("minSectionY must be <= maxSectionY");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Dirty bounds are invalid");
        }
    }

    public static DirtyChunkRegion singleBlock(int sectionY, int localX, int localY, int localZ) {
        return new DirtyChunkRegion(sectionY, sectionY, localX, localY, localZ, localX, localY, localZ);
    }

    public DirtyChunkRegion merge(DirtyChunkRegion other) {
        return new DirtyChunkRegion(
                Math.min(minSectionY, other.minSectionY),
                Math.max(maxSectionY, other.maxSectionY),
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY),
                Math.max(maxZ, other.maxZ)
        );
    }

    public DirtyChunkRegion expanded(int blocks) {
        if (blocks <= 0) {
            return this;
        }
        return new DirtyChunkRegion(
                (minY - blocks) >> 4,
                (maxY + blocks) >> 4,
                Math.max(0, minX - blocks),
                minY - blocks,
                Math.max(0, minZ - blocks),
                Math.min(15, maxX + blocks),
                maxY + blocks,
                Math.min(15, maxZ + blocks)
        );
    }
}
