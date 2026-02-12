package com.ladakx.inertia.rendering;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class VisualTombstoneService {
    private final Map<Integer, Long> tombstones = new ConcurrentHashMap<>();

    void add(int visualId, long expiresAtTick) {
        tombstones.put(visualId, expiresAtTick);
    }

    void clear(int visualId) {
        tombstones.remove(visualId);
    }

    boolean isTombstoned(int visualId, long currentTick) {
        Long expiresAtTick = tombstones.get(visualId);
        if (expiresAtTick == null) {
            return false;
        }
        if (currentTick > expiresAtTick) {
            tombstones.remove(visualId, expiresAtTick);
            return false;
        }
        return true;
    }

    void pruneExpired(long currentTick) {
        tombstones.entrySet().removeIf(entry -> currentTick > entry.getValue());
    }

    void clear() {
        tombstones.clear();
    }
}
