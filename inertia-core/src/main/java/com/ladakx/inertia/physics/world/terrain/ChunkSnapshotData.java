package com.ladakx.inertia.physics.world.terrain;

import com.ladakx.inertia.infrastructure.nms.jolt.JoltTools;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

import java.util.Objects;

public final class ChunkSnapshotData {
    private final int minSectionY;
    private final int sectionsCount;
    private final int minHeight;
    private final int height;
    private final Material[] materials;
    private final boolean[] sectionHasBlocks;

    private ChunkSnapshotData(int minSectionY,
                              int sectionsCount,
                              int minHeight,
                              int height,
                              Material[] materials,
                              boolean[] sectionHasBlocks) {
        this.minSectionY = minSectionY;
        this.sectionsCount = sectionsCount;
        this.minHeight = minHeight;
        this.height = height;
        this.materials = materials;
        this.sectionHasBlocks = sectionHasBlocks;
    }

    public static ChunkSnapshotData capture(Chunk chunk, JoltTools joltTools) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(joltTools, "joltTools");
        int minSectionY = joltTools.getMinSectionY(chunk);
        int sectionsCount = joltTools.getSectionsCount(chunk);
        int minHeight = minSectionY << 4;
        int height = sectionsCount << 4;
        Material[] materials = new Material[height * 256];
        boolean[] sectionHasBlocks = new boolean[sectionsCount];
        ChunkSnapshot snapshot = chunk.getChunkSnapshot();

        for (int localY = 0; localY < height; localY++) {
            int worldY = minHeight + localY;
            int sectionIndex = localY >> 4;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Material material = snapshot.getBlockType(x, worldY, z);
                    materials[flattenIndex(x, localY, z)] = material;
                    if (material != null && !material.isAir()) {
                        sectionHasBlocks[sectionIndex] = true;
                    }
                }
            }
        }

        return new ChunkSnapshotData(minSectionY, sectionsCount, minHeight, height, materials, sectionHasBlocks);
    }

    public static ChunkSnapshotData captureFast(Chunk chunk, JoltTools joltTools) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(joltTools, "joltTools");
        int minSectionY = joltTools.getMinSectionY(chunk);
        int sectionsCount = joltTools.getSectionsCount(chunk);
        int minHeight = minSectionY << 4;
        int height = sectionsCount << 4;
        Material[] materials = new Material[height * 256];
        boolean[] sectionHasBlocks = new boolean[sectionsCount];

        for (int sectionIndex = 0; sectionIndex < sectionsCount; sectionIndex++) {
            int sectionY = minSectionY + sectionIndex;
            if (joltTools.hasOnlyAir(chunk, sectionY) || joltTools.getSectionsNonEmptyBlocks(chunk, sectionY) == 0) {
                continue;
            }

            sectionHasBlocks[sectionIndex] = true;
            int localYBase = sectionIndex << 4;
            for (int yInSection = 0; yInSection < 16; yInSection++) {
                int localY = localYBase + yInSection;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        materials[flattenIndex(x, localY, z)] = joltTools.getMaterial(chunk, sectionY, x, yInSection, z);
                    }
                }
            }
        }

        return new ChunkSnapshotData(minSectionY, sectionsCount, minHeight, height, materials, sectionHasBlocks);
    }

    public int minSectionY() {
        return minSectionY;
    }

    public int sectionsCount() {
        return sectionsCount;
    }

    public int minHeight() {
        return minHeight;
    }

    public int height() {
        return height;
    }

    public Material getMaterial(int x, int localY, int z) {
        return materials[flattenIndex(x, localY, z)];
    }

    public boolean sectionHasBlocks(int sectionIndex) {
        return sectionHasBlocks[sectionIndex];
    }

    private static int flattenIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }
}
