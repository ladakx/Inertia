package com.ladakx.inertia.physics.world.terrain;

import com.ladakx.inertia.infrastructure.nms.jolt.JoltTools;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;

import java.util.Objects;

public final class ChunkSnapshotData {
    private final int minSectionY;
    private final int sectionsCount;
    private final int minHeight;
    private final int height;
    private final short[] profileIds;
    private final boolean[] sectionHasBlocks;
    private final long[] sectionFingerprints;

    private ChunkSnapshotData(int minSectionY,
                              int sectionsCount,
                              int minHeight,
                              int height,
                              short[] profileIds,
                              boolean[] sectionHasBlocks,
                              long[] sectionFingerprints) {
        this.minSectionY = minSectionY;
        this.sectionsCount = sectionsCount;
        this.minHeight = minHeight;
        this.height = height;
        this.profileIds = profileIds;
        this.sectionHasBlocks = sectionHasBlocks;
        this.sectionFingerprints = sectionFingerprints;
    }

    public static ChunkSnapshotData capture(Chunk chunk, JoltTools joltTools) {
        return capture(chunk, joltTools, null);
    }

    public static ChunkSnapshotData capture(Chunk chunk, JoltTools joltTools, short[] materialToProfileId) {
        return capture(chunk, joltTools, materialToProfileId, null, null);
    }

    public static ChunkSnapshotData capture(Chunk chunk,
                                            JoltTools joltTools,
                                            short[] materialToProfileId,
                                            short[] materialToSlabTopProfileId,
                                            short[] materialToSlabDoubleProfileId) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(joltTools, "joltTools");
        int minSectionY = joltTools.getMinSectionY(chunk);
        int sectionsCount = joltTools.getSectionsCount(chunk);
        int minHeight = minSectionY << 4;
        int height = sectionsCount << 4;
        short[] profileIds = new short[height * 256];
        boolean[] sectionHasBlocks = new boolean[sectionsCount];
        long[] sectionFingerprints = new long[sectionsCount];
        ChunkSnapshot snapshot = chunk.getChunkSnapshot();

        for (int localY = 0; localY < height; localY++) {
            int worldY = minHeight + localY;
            int sectionIndex = localY >> 4;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Material material = snapshot.getBlockType(x, worldY, z);
                    short profileId = toProfileId(material, materialToProfileId);
                    if (profileId != 0
                            && materialToSlabTopProfileId != null
                            && materialToSlabDoubleProfileId != null
                            && material != null
                            && material.name().endsWith("_SLAB")) {
                        try {
                            BlockData data = snapshot.getBlockData(x, worldY, z);
                            if (data instanceof Slab slab) {
                                if (slab.getType() == Slab.Type.TOP) {
                                    profileId = materialToSlabTopProfileId[material.ordinal()];
                                } else if (slab.getType() == Slab.Type.DOUBLE) {
                                    profileId = materialToSlabDoubleProfileId[material.ordinal()];
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    profileIds[flattenIndex(x, localY, z)] = profileId;
                    if (profileId != 0) {
                        sectionHasBlocks[sectionIndex] = true;
                    }
                }
            }
        }


        for (int sectionIndex = 0; sectionIndex < sectionsCount; sectionIndex++) {
            int sectionY = minSectionY + sectionIndex;
            int sectionStart = (sectionIndex << 12);
            sectionFingerprints[sectionIndex] = computeSectionFingerprint(profileIds, sectionStart, sectionY);
        }

        return new ChunkSnapshotData(minSectionY, sectionsCount, minHeight, height, profileIds, sectionHasBlocks, sectionFingerprints);
    }

    public static ChunkSnapshotData captureFast(Chunk chunk, JoltTools joltTools) {
        return captureFast(chunk, joltTools, null);
    }

    public static ChunkSnapshotData captureFast(Chunk chunk, JoltTools joltTools, short[] materialToProfileId) {
        return captureFast(chunk, joltTools, materialToProfileId, null, null);
    }

    public static ChunkSnapshotData captureFast(Chunk chunk,
                                                JoltTools joltTools,
                                                short[] materialToProfileId,
                                                short[] materialToSlabTopProfileId,
                                                short[] materialToSlabDoubleProfileId) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(joltTools, "joltTools");
        int minSectionY = joltTools.getMinSectionY(chunk);
        int sectionsCount = joltTools.getSectionsCount(chunk);
        int minHeight = minSectionY << 4;
        int height = sectionsCount << 4;
        short[] profileIds = new short[height * 256];
        boolean[] sectionHasBlocks = new boolean[sectionsCount];
        long[] sectionFingerprints = new long[sectionsCount];

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
                        if (profileId != 0
                                && materialToSlabTopProfileId != null
                                && materialToSlabDoubleProfileId != null
                                && material != null
                                && material.name().endsWith("_SLAB")) {
                            byte slabType = joltTools.getSlabType(chunk, sectionY, x, yInSection, z);
                            if (slabType == 1) {
                                profileId = materialToSlabTopProfileId[material.ordinal()];
                            } else if (slabType == 2) {
                                profileId = materialToSlabDoubleProfileId[material.ordinal()];
                            }
                        }
                        profileIds[flattenIndex(x, localY, z)] = profileId;
                        if (profileId != 0) {
                            sectionHasBlocks[sectionIndex] = true;
                        }
                    }
                }
            }
        }

        for (int sectionIndex = 0; sectionIndex < sectionsCount; sectionIndex++) {
            int sectionY = minSectionY + sectionIndex;
            int sectionStart = (sectionIndex << 12);
            sectionFingerprints[sectionIndex] = computeSectionFingerprint(profileIds, sectionStart, sectionY);
        }

        return new ChunkSnapshotData(minSectionY, sectionsCount, minHeight, height, profileIds, sectionHasBlocks, sectionFingerprints);
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

    public long sectionFingerprint(int sectionIndex) {
        return sectionFingerprints[sectionIndex];
    }

    public long[] sectionFingerprints() {
        return sectionFingerprints.clone();
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

    private static long computeSectionFingerprint(short[] profileIds, int sectionStart, int sectionY) {
        long hash = 0x9E3779B185EBCA87L ^ (((long) sectionY) * 0xC2B2AE3D27D4EB4FL);
        for (int i = 0; i < 4096; i++) {
            long value = profileIds[sectionStart + i] & 0xFFFFL;
            hash ^= value + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
            hash = Long.rotateLeft(hash, 13) * 0x9E3779B185EBCA87L;
        }
        hash ^= (hash >>> 33);
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= (hash >>> 33);
        hash *= 0xC4CEB9FE1A85EC53L;
        hash ^= (hash >>> 33);
        return hash;
    }
}
