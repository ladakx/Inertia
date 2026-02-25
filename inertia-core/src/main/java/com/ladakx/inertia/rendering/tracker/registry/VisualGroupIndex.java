package com.ladakx.inertia.rendering.tracker.registry;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks visual group membership (groupKey -> member ids) in a read-optimized form.
 * <p>
 * Used to apply atomic per-group updates to avoid composite-model "tearing" when
 * packet budgets / scheduling would otherwise split parts across ticks.
 */
public final class VisualGroupIndex {

    private final ConcurrentHashMap<Integer, int[]> membersByGroupKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> groupKeyByVisualId = new ConcurrentHashMap<>();

    public void onRegister(int visualId, int groupKey) {
        groupKeyByVisualId.put(visualId, groupKey);
        membersByGroupKey.compute(groupKey, (k, existing) -> addUnique(existing, visualId));
    }

    public void onUnregister(int visualId) {
        Integer groupKey = groupKeyByVisualId.remove(visualId);
        if (groupKey == null) {
            return;
        }
        membersByGroupKey.computeIfPresent(groupKey, (k, existing) -> remove(existing, visualId));
    }

    public Integer groupKeyOf(int visualId) {
        return groupKeyByVisualId.get(visualId);
    }

    public int[] membersOf(int groupKey) {
        return membersByGroupKey.get(groupKey);
    }

    private static int[] addUnique(int[] existing, int visualId) {
        if (existing == null || existing.length == 0) {
            return new int[]{visualId};
        }
        for (int id : existing) {
            if (id == visualId) {
                return existing;
            }
        }
        int[] out = Arrays.copyOf(existing, existing.length + 1);
        out[out.length - 1] = visualId;
        return out;
    }

    private static int[] remove(int[] existing, int visualId) {
        if (existing == null || existing.length == 0) {
            return null;
        }
        int idx = -1;
        for (int i = 0; i < existing.length; i++) {
            if (existing[i] == visualId) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return existing;
        }
        if (existing.length == 1) {
            return null;
        }
        int[] out = new int[existing.length - 1];
        if (idx > 0) {
            System.arraycopy(existing, 0, out, 0, idx);
        }
        if (idx < existing.length - 1) {
            System.arraycopy(existing, idx + 1, out, idx, existing.length - idx - 1);
        }
        return out;
    }
}

