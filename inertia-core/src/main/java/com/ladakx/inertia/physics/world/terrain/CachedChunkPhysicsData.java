package com.ladakx.inertia.physics.world.terrain;

import com.ladakx.inertia.physics.world.terrain.greedy.GreedyMeshData;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public record CachedChunkPhysicsData(GreedyMeshData meshData, long[] sectionFingerprints) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public CachedChunkPhysicsData {
        Objects.requireNonNull(meshData, "meshData");
        sectionFingerprints = sectionFingerprints == null ? new long[0] : sectionFingerprints.clone();
    }

    @Override
    public long[] sectionFingerprints() {
        return sectionFingerprints.clone();
    }

    public boolean sectionFingerprintMatches(ChunkSnapshotData snapshot, int sectionIndex) {
        if (snapshot == null || sectionIndex < 0 || sectionIndex >= snapshot.sectionsCount()) {
            return false;
        }
        if (sectionIndex >= sectionFingerprints.length) {
            return false;
        }
        return sectionFingerprints[sectionIndex] == snapshot.sectionFingerprint(sectionIndex);
    }

    @Override
    public String toString() {
        return "CachedChunkPhysicsData[meshData=" + meshData + ", sectionFingerprints=" + Arrays.toString(sectionFingerprints) + ']';
    }
}
