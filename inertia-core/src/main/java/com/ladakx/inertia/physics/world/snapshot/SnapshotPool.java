package com.ladakx.inertia.physics.world.snapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class SnapshotPool {
    private final Deque<VisualState> statePool = new ArrayDeque<>(1024);
    private final Deque<List<VisualState>> listPool = new ArrayDeque<>(16);

    public synchronized VisualState borrowState() {
        VisualState state = statePool.pollLast();
        if (state == null) {
            return new VisualState();
        }
        return state;
    }

    public synchronized void returnState(VisualState state) {
        state.clear();
        statePool.addLast(state);
    }

    public synchronized List<VisualState> borrowList() {
        List<VisualState> list = listPool.pollLast();
        if (list == null) {
            return new ArrayList<>(512); // Pre-allocate decent size
        }
        list.clear();
        return list;
    }

    public synchronized void returnList(List<VisualState> list) {
        list.clear();
        listPool.addLast(list);
    }
}