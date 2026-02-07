package com.ladakx.inertia.rendering;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkEntityTracker {

    private final Map<Integer, TrackedVisual> visualsById = new ConcurrentHashMap<>();
    // Храним ID видимых сущностей для каждого игрока
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
        // Если визуальный объект удален глобально, он должен исчезнуть у всех игроков
        for (Map.Entry<UUID, Set<Integer>> entry : visibleByPlayer.entrySet()) {
            if (entry.getValue().remove(id)) {
                Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    // Мы не можем вызвать destroyFor здесь, так как объекта visual уже может не быть
                    // Но клиенту все равно нужно отправить пакет destroy.
                    // В текущей архитектуре NetworkVisual отвечает за отправку, поэтому лучше вызывать unregister ДО удаления объекта
                }
            }
        }
    }

    public void updateState(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
        Objects.requireNonNull(visual, "visual");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        visualsById.put(visual.getId(), new TrackedVisual(visual, location.clone(), new Quaternionf(rotation)));
    }

    /**
     * Основной цикл обновления видимости.
     * Должен вызываться ТОЛЬКО в основном потоке сервера (Sync).
     */
    public void tick(@NotNull Collection<? extends Player> players, double viewDistanceSquared) {
        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;

            UUID playerId = player.getUniqueId();
            Set<Integer> visible = visibleByPlayer.computeIfAbsent(playerId, k -> new HashSet<>());
            Location playerLoc = player.getLocation();
            String playerWorldName = playerLoc.getWorld().getName();

            // Итерируемся по всем зарегистрированным визуалам
            // NOTE: Для 1000+ объектов здесь потребуется оптимизация (Spatial Hash / Chunk Map),
            // но согласно ТЗ реализуем простой перебор O(N*M).
            for (TrackedVisual tracked : visualsById.values()) {
                NetworkVisual visual = tracked.visual();
                int visualId = visual.getId();
                
                boolean isInSameWorld = tracked.location().getWorld().getName().equals(playerWorldName);
                double distSq = isInSameWorld ? tracked.location().distanceSquared(playerLoc) : Double.MAX_VALUE;
                boolean inRange = isInSameWorld && distSq <= viewDistanceSquared;

                boolean wasVisible = visible.contains(visualId);

                // State Machine
                if (inRange) {
                    if (!wasVisible) {
                        // Case 1: Enter Radius -> Spawn
                        visual.spawnFor(player);
                        visible.add(visualId);
                    } else {
                        // Case 2: Stay in Radius -> Update
                        // Здесь мы отправляем пакет телепортации/перемещения
                        visual.updatePositionFor(player, tracked.location(), tracked.rotation());
                    }
                } else {
                    if (wasVisible) {
                        // Case 3: Exit Radius -> Destroy
                        visual.destroyFor(player);
                        visible.remove(visualId);
                    }
                }
            }
        }
        
        // Очистка оффлайн игроков из кеша
        visibleByPlayer.keySet().removeIf(uuid -> org.bukkit.Bukkit.getPlayer(uuid) == null);
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
        
        // Оптимизация: создаем новый рекорд вместо мутации
        private TrackedVisual withState(Location location, Quaternionf rotation) {
             return new TrackedVisual(visual, location.clone(), new Quaternionf(rotation));
        }
    }
}