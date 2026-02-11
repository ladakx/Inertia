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
    private final short[] profileIds;
    private final boolean[] sectionHasBlocks;

    private ChunkSnapshotData(int minSectionY,
                              int sectionsCount,
                              int minHeight,
                              int height,
                              short[] profileIds,
                              boolean[] sectionHasBlocks) {
        this.minSectionY = minSectionY;
        this.sectionsCount = sectionsCount;
        this.minHeight = minHeight;
        this.height = height;
        this.profileIds = profileIds;
        this.sectionHasBlocks = sectionHasBlocks;
    }

    public static ChunkSnapshotData capture(Chunk chunk, JoltTools joltTools) {
        return capture(chunk, joltTools, null);
    }

    public static ChunkSnapshotData capture(Chunk chunk, JoltTools joltTools, short[] materialToProfileId) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(joltTools, "joltTools");
        int minSectionY = joltTools.getMinSectionY(chunk);
        int sectionsCount = joltTools.getSectionsCount(chunk);
        int minHeight = minSectionY << 4;
        int height = sectionsCount << 4;
        short[] profileIds = new short[height * 256];
        boolean[] sectionHasBlocks = new boolean[sectionsCount];
        ChunkSnapshot snapshot = chunk.getChunkSnapshot();

        for (int localY = 0; localY < height; localY++) {
            int worldY = minHeight + localY;
            int sectionIndex = localY >> 4;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Material material = snapshot.getBlockType(x, worldY, z);
                    short profileId = toProfileId(material, materialToProfileId);
                    profileIds[flattenIndex(x, localY, z)] = profileId;
                    if (profileId != 0) {
                        sectionHasBlocks[sectionIndex] = true;
                    }
                }
            }
        }

        return new ChunkSnapshotData(minSectionY, sectionsCount, minHeight, height, profileIds, sectionHasBlocks);
    }

    public static ChunkSnapshotData captureFast(Chunk chunk, JoltTools joltTools) {
        return captureFast(chunk, joltTools, null);
    }

    public static ChunkSnapshotData captureFast(Chunk chunk, JoltTools joltTools, short[] materialToProfileId) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(joltTools, "joltTools");
        int minSectionY = joltTools.getMinSectionY(chunk);
        int sectionsCount = joltTools.getSectionsCount(chunk);
        int minHeight = minSectionY << 4;
        int height = sectionsCount << 4;
        short[] profileIds = new short[height * 256];
        boolean[] sectionHasBlocks = new boolean[sectionsCount];

        for (int sectionIndex = 0; sectionIndex < sectionsCount; sectionIndex++) {
            int sectionY = minSectionY + sectionIndex;
            if (joltTools.hasOnlyAir(chunk, sectionY) || joltTools.getSectionsNonEmptyBlocks(chunk, sectionY) == 0) {
                continue;
            }

            int localYBase = sectionIndex << 4;
            for (int yInSection = 0; yInSection < 16; yInSection++) {
                int localY = localYBase + yInSection;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Material material = joltTools.getMaterial(chunk, sectionY, x, yInSection, z);
                        short profileId = toProfileId(material, materialToProfileId);
                        profileIds[flattenIndex(x, localY, z)] = profileId;
                        if (profileId != 0) {
                            sectionHasBlocks[sectionIndex] = true;
                        }
                    }
                }
            }
        }

        return new ChunkSnapshotData(minSectionY, sectionsCount, minHeight, height, profileIds, sectionHasBlocks);
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

    public short getProfileId(int x, int localY, int z) {
        return profileIds[flattenIndex(x, localY, z)];
    }

    public boolean sectionHasBlocks(int sectionIndex) {
        return sectionHasBlocks[sectionIndex];
    }

    private static int flattenIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    private static short toProfileId(Material material, short[] materialToProfileId) {
        if (material == null || material.isAir()) {
            return 0;
        }
        if (materialToProfileId == null || material.ordinal() >= materialToProfileId.length) {
            return (short) (material.ordinal() + 1);
        }
        return materialToProfileId[material.ordinal()];
    }
}
