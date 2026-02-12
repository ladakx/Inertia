package com.ladakx.inertia.rendering;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

final class PendingDestroyState {
    private final IntOpenHashSet ids = new IntOpenHashSet();
    private long backlogSinceNanos;
    private long firstUnregisterTick = -1L;

    IntOpenHashSet ids() {
        return ids;
    }

    long backlogSinceNanos() {
        return backlogSinceNanos;
    }

    long firstUnregisterTick() {
        return firstUnregisterTick;
    }

    void markBacklogStart(long nowNanos, long tick) {
        this.backlogSinceNanos = nowNanos;
        if (this.firstUnregisterTick < 0L) {
            this.firstUnregisterTick = tick;
        }
    }

    void addAll(IntArrayList values) {
        for (int i = 0; i < values.size(); i++) {
            ids.add(values.getInt(i));
        }
    }
}

