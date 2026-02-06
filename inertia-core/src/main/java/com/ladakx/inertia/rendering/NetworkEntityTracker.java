package com.ladakx.inertia.rendering;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реєстр активних packet-based entity. Лише базове зберігання без логіки видимості.
 */
public class NetworkEntityTracker {

    private final Map<Integer, TrackedVisual> visualsById = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> visibleByPlayer = new ConcurrentHashMap<>();

    public void register(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
        Objects.requireNonNull(visual, "visual");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        visualsById.put(visual.getId(), new TrackedVisual(visual, location.clone(), new Quaternionf(rotation)));
    }

    public void unregister(@NotNull NetworkVisual visual) {
        Objects.requireNonNull(visual, "visual");
        unregisterById(visual.getId());
    }

    public void unregisterById(int id) {
        visualsById.remove(id);
        for (Set<Integer> visible : visibleByPlayer.values()) {
            visible.remove(id);
        }
    }

    public @Nullable NetworkVisual getById(int id) {
        TrackedVisual tracked = visualsById.get(id);
        return tracked == null ? null : tracked.visual();
    }

    public @NotNull Collection<NetworkVisual> getAll() {
        return Collections.unmodifiableCollection(visualsById.values().stream().map(TrackedVisual::visual).toList());
    }

    public void updateState(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
        Objects.requireNonNull(visual, "visual");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        visualsById.computeIfPresent(
                visual.getId(),
                (id, tracked) -> tracked.withState(location, rotation)
        );
    }

    public void tick(@NotNull Collection<? extends Player> players, double viewDistanceSquared) {
        for (Player player : players) {
            if (player == null) continue;
            UUID playerId = player.getUniqueId();
            Set<Integer> visible = visibleByPlayer.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet());
            Location playerLocation = player.getLocation();

            for (TrackedVisual tracked : visualsById.values()) {
                if (tracked.location().getWorld() != playerLocation.getWorld()) {
                    int visualId = tracked.visual().getId();
                    if (visible.remove(visualId)) {
                        tracked.visual().destroyFor(player);
                    }
                    continue;
                }
                double distanceSquared = tracked.location().distanceSquared(playerLocation);
                int visualId = tracked.visual().getId();
                boolean wasVisible = visible.contains(visualId);

                if (distanceSquared <= viewDistanceSquared) {
                    if (!wasVisible) {
                        tracked.visual().spawnFor(player);
                        visible.add(visualId);
                    }
                    tracked.visual().updatePositionFor(player, tracked.location(), tracked.rotation());
                } else if (wasVisible) {
                    tracked.visual().destroyFor(player);
                    visible.remove(visualId);
                }
            }
        }
    }

    public void clear() {
        visualsById.clear();
        visibleByPlayer.clear();
    }

    private record TrackedVisual(NetworkVisual visual, Location location, Quaternionf rotation) {
        private TrackedVisual {
            Objects.requireNonNull(visual, "visual");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(rotation, "rotation");
        }

        private TrackedVisual withState(Location location, Quaternionf rotation) {
            return new TrackedVisual(visual, location.clone(), new Quaternionf(rotation));
        }
    }
}
