package com.ladakx.inertia.rendering.tracker.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VisualTokenService {
    private final Map<Integer, Long> tokenVersions = new ConcurrentHashMap<>();

    public long bump(int visualId) {
        return tokenVersions.merge(visualId, 1L, Long::sum);
    }

    public long current(int visualId) {
        return tokenVersions.getOrDefault(visualId, 0L);
    }

    public boolean isCurrent(Integer visualId, long tokenVersion) {
        if (visualId == null || tokenVersion < 0L) {
            return true;
        }
        return current(visualId.intValue()) == tokenVersion;
    }

    public void clear() {
        tokenVersions.clear();
    }
}
