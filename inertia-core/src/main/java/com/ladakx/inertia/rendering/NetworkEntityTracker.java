package com.ladakx.inertia.rendering;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
        TrackedVisual tracked = visualsById.remove(id);
        // Исправление: получаем visual, чтобы отправить пакет destroy
        NetworkVisual visual = tracked != null ? tracked.visual() : null;

        for (Map.Entry<UUID, Set<Integer>> entry : visibleByPlayer.entrySet()) {
            if (entry.getValue().remove(id)) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && visual != null) {
                    // Исправление: отправляем пакет уничтожения
                    visual.destroyFor(player);
                }
            }
        }
    }

    public void updateState(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
        Objects.requireNonNull(visual, "visual");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        // Обновляем состояние, но используем put, чтобы перезаписать старое
        visualsById.put(visual.getId(), new TrackedVisual(visual, location.clone(), new Quaternionf(rotation)));
    }

    public void tick(@NotNull Collection<? extends Player> players, double viewDistanceSquared) {
        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;

            UUID playerId = player.getUniqueId();
            Set<Integer> visible = visibleByPlayer.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()); // Thread-safe set
            Location playerLoc = player.getLocation();
            String playerWorldName = playerLoc.getWorld().getName();

            for (TrackedVisual tracked : visualsById.values()) {
                NetworkVisual visual = tracked.visual();
                int visualId = visual.getId();

                boolean isInSameWorld = tracked.location().getWorld().getName().equals(playerWorldName);
                double distSq = isInSameWorld ? tracked.location().distanceSquared(playerLoc) : Double.MAX_VALUE;
                boolean inRange = isInSameWorld && distSq <= viewDistanceSquared;
                boolean wasVisible = visible.contains(visualId);

                if (inRange) {
                    if (!wasVisible) {
                        visual.spawnFor(player);
                        visible.add(visualId);
                    } else {
                        visual.updatePositionFor(player, tracked.location(), tracked.rotation());
                    }
                } else {
                    if (wasVisible) {
                        visual.destroyFor(player);
                        visible.remove(visualId);
                    }
                }
            }
        }
        // Очистка оффлайн игроков
        visibleByPlayer.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    /**
     * Полностью очищает видимость для конкретного игрока (например, при смене мира)
     */
    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Set<Integer> visible = visibleByPlayer.remove(uuid);
        if (visible != null) {
            for (Integer id : visible) {
                TrackedVisual tracked = visualsById.get(id);
                if (tracked != null) {
                    tracked.visual().destroyFor(player);
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
            Objects.requireNonNull(visual);
            Objects.requireNonNull(location);
            Objects.requireNonNull(rotation);
        }
    }
}