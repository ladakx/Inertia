package com.ladakx.inertia.rendering;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реєстр активних packet-based entity. Лише базове зберігання без логіки видимості.
 */
public class NetworkEntityTracker {

    private final Map<Integer, NetworkVisual> visualsById = new ConcurrentHashMap<>();

    public void register(@NotNull NetworkVisual visual) {
        Objects.requireNonNull(visual, "visual");
        visualsById.put(visual.getId(), visual);
    }

    public void unregister(@NotNull NetworkVisual visual) {
        Objects.requireNonNull(visual, "visual");
        visualsById.remove(visual.getId());
    }

    public void unregisterById(int id) {
        visualsById.remove(id);
    }

    public @Nullable NetworkVisual getById(int id) {
        return visualsById.get(id);
    }

    public @NotNull Collection<NetworkVisual> getAll() {
        return Collections.unmodifiableCollection(visualsById.values());
    }

    public void clear() {
        visualsById.clear();
    }
}
