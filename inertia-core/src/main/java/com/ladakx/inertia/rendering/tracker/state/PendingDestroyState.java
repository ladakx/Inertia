package com.ladakx.inertia.rendering.tracker.state;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public final class PendingDestroyState {
    private final IntOpenHashSet ids = new IntOpenHashSet();
    private long backlogSinceNanos;
    private long firstUnregisterTick = -1L;

    public IntOpenHashSet ids() {
        return ids;
    }

    public long backlogSinceNanos() {
        return backlogSinceNanos;
    }

    public long firstUnregisterTick() {
        return firstUnregisterTick;
    }

    public void markBacklogStart(long nowNanos, long tick) {
        this.backlogSinceNanos = nowNanos;
        if (this.firstUnregisterTick < 0L) {
            this.firstUnregisterTick = tick;
        }
    }

    public void addAll(IntArrayList values) {
        for (int i = 0; i < values.size(); i++) {
            ids.add(values.getInt(i));
        }
    }
}
