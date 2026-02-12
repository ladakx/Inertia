package com.ladakx.inertia.rendering.tracker.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VisualTombstoneService {
    private final Map<Integer, Long> tombstones = new ConcurrentHashMap<>();

    public void add(int visualId, long expiresAtTick) {
        tombstones.put(visualId, expiresAtTick);
    }

    public void clear(int visualId) {
        tombstones.remove(visualId);
    }

    public boolean isTombstoned(int visualId, long currentTick) {
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

    public void pruneExpired(long currentTick) {
        tombstones.entrySet().removeIf(entry -> currentTick > entry.getValue());
    }

    public void clear() {
        tombstones.clear();
    }
}
