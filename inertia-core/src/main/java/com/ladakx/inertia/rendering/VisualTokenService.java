package com.ladakx.inertia.rendering;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class VisualTokenService {
    private final Map<Integer, Long> tokenVersions = new ConcurrentHashMap<>();

    long bump(int visualId) {
        return tokenVersions.merge(visualId, 1L, Long::sum);
    }

    long current(int visualId) {
        return tokenVersions.getOrDefault(visualId, 0L);
    }

    boolean isCurrent(Integer visualId, long tokenVersion) {
        if (visualId == null || tokenVersion < 0L) {
            return true;
        }
        return current(visualId.intValue()) == tokenVersion;
    }

    void clear() {
        tokenVersions.clear();
    }
}
