package com.ladakx.inertia.rendering;

import com.ladakx.inertia.common.chunk.ChunkUtils;
import com.ladakx.inertia.configuration.dto.InertiaConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkEntityTracker {
    private final Map<Integer, TrackedVisual> visualsById = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> visibleByPlayer = new ConcurrentHashMap<>();
    // Spatial Partitioning: Карта ChunkKey -> Набор ID визуалов в этом чанке
    private final Map<Long, Set<Integer>> chunkGrid = new ConcurrentHashMap<>();

    // Пороги для дельта-компрессии (чтобы не спамить пакетами при микродвижениях)
    private volatile float posThresholdSq = 0.0001f; // 0.01 блока (squared)
    private volatile float rotThresholdDot = 0.3f; // quaternion dot

    public NetworkEntityTracker() {
    }

    public NetworkEntityTracker(InertiaConfig.RenderingSettings.NetworkEntityTrackerSettings settings) {
        applySettings(settings);
    }

    public void applySettings(InertiaConfig.RenderingSettings.NetworkEntityTrackerSettings settings) {
        if (settings == null) return;
        this.posThresholdSq = settings.posThresholdSq;
        this.rotThresholdDot = settings.rotThresholdDot;
    }

    public void register(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
        Objects.requireNonNull(visual, "visual");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");

        TrackedVisual tracked = new TrackedVisual(visual, location.clone(), new Quaternionf(rotation));
        visualsById.put(visual.getId(), tracked);
        addToGrid(visual.getId(), location);
    }

    public void unregister(@NotNull NetworkVisual visual) {
        Objects.requireNonNull(visual, "visual");
        unregisterById(visual.getId());
    }

    public void unregisterById(int id) {
        TrackedVisual tracked = visualsById.remove(id);
        if (tracked != null) {
            removeFromGrid(id, tracked.location());
            NetworkVisual visual = tracked.visual();

            // Удаляем у всех игроков, кто видел этот объект
            Iterator<Map.Entry<UUID, Set<Integer>>> it = visibleByPlayer.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Set<Integer>> entry = it.next();
                if (entry.getValue().remove(id)) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null) {
                        visual.destroyFor(player);
                    }
                }
            }
        }
    }

    public void updateState(@NotNull NetworkVisual visual, @NotNull Location location, @NotNull Quaternionf rotation) {
        Objects.requireNonNull(visual, "visual");
        TrackedVisual tracked = visualsById.get(visual.getId());

        if (tracked != null) {
            // Обновляем грид, если объект сменил чанк
            long oldChunkKey = ChunkUtils.getChunkKey(tracked.location().getBlockX() >> 4, tracked.location().getBlockZ() >> 4);
            long newChunkKey = ChunkUtils.getChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);

            tracked.update(location, rotation);

            if (oldChunkKey != newChunkKey) {
                removeFromGrid(visual.getId(), oldChunkKey);
                addToGrid(visual.getId(), newChunkKey);
            }
        } else {
            // Если объекта не было (редкий кейс рассинхрона), регистрируем
            register(visual, location, rotation);
        }
    }

    public void tick(@NotNull Collection<? extends Player> players, double viewDistanceSquared) {
        // Оптимизация: вместо O(Players * Entities) делаем O(Players * VisibleChunks * EntitiesInChunk)
        int viewDistanceChunks = (int) Math.ceil(Math.sqrt(viewDistanceSquared) / 16.0);
        float localPosThresholdSq = this.posThresholdSq;
        float localRotThresholdDot = this.rotThresholdDot;

        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;

            UUID playerId = player.getUniqueId();
            Set<Integer> visible = visibleByPlayer.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

            Location playerLoc = player.getLocation();
            int pChunkX = playerLoc.getBlockX() >> 4;
            int pChunkZ = playerLoc.getBlockZ() >> 4;
            String playerWorldName = playerLoc.getWorld().getName();

            // 1. Проход по чанкам вокруг игрока для поиска новых или обновляемых объектов
            for (int x = -viewDistanceChunks; x <= viewDistanceChunks; x++) {
                for (int z = -viewDistanceChunks; z <= viewDistanceChunks; z++) {
                    long chunkKey = ChunkUtils.getChunkKey(pChunkX + x, pChunkZ + z);
                    Set<Integer> entityIdsInChunk = chunkGrid.get(chunkKey);

                    if (entityIdsInChunk != null && !entityIdsInChunk.isEmpty()) {
                        for (Integer id : entityIdsInChunk) {
                            TrackedVisual tracked = visualsById.get(id);
                            if (tracked == null) continue; // Рассинхрон грида и мапы (безопасно пропускаем)

                            // Проверка мира обязательна, так как ключи чанков одинаковы для разных миров
                            if (!tracked.location().getWorld().getName().equals(playerWorldName)) continue;

                            double distSq = tracked.location().distanceSquared(playerLoc);
                            boolean inRange = distSq <= viewDistanceSquared;

                            if (inRange) {
                                if (visible.add(id)) {
                                    // Новый объект в зоне видимости -> спавним
                                    tracked.visual().spawnFor(player);
                                    // Форсируем отправку позиции, чтобы не было "дергания" при спавне
                                    tracked.markSent(player);
                                } else {
                                    // Объект уже виден -> проверяем, нужно ли обновить позицию (Дельта-компрессия)
                                    if (tracked.isDirtyFor(player, localPosThresholdSq, localRotThresholdDot)) {
                                        tracked.visual().updatePositionFor(player, tracked.location(), tracked.rotation());
                                        tracked.markSent(player);
                                    }
                                }
                            } else {
                                // Вышел из радиуса
                                if (visible.remove(id)) {
                                    tracked.visual().destroyFor(player);
                                }
                            }
                        }
                    }
                }
            }

            // 2. Очистка объектов, которые игрок видит, но они переместились слишком далеко или в другой мир
            // (Это нужно, если объект быстро улетел из зоны видимых чанков)
            visible.removeIf(id -> {
                TrackedVisual tracked = visualsById.get(id);
                if (tracked == null) return true; // Объект удален глобально

                boolean sameWorld = tracked.location().getWorld().getName().equals(playerWorldName);
                if (!sameWorld || tracked.location().distanceSquared(playerLoc) > viewDistanceSquared) {
                    tracked.visual().destroyFor(player);
                    return true;
                }
                return false;
            });
        }

        // Очистка оффлайн игроков
        visibleByPlayer.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

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
        chunkGrid.clear();
    }

    // --- Grid Helpers ---

    private void addToGrid(int id, Location loc) {
        long key = ChunkUtils.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        addToGrid(id, key);
    }

    private void addToGrid(int id, long chunkKey) {
        chunkGrid.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(id);
    }

    private void removeFromGrid(int id, Location loc) {
        long key = ChunkUtils.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        removeFromGrid(id, key);
    }

    private void removeFromGrid(int id, long chunkKey) {
        Set<Integer> set = chunkGrid.get(chunkKey);
        if (set != null) {
            set.remove(id);
            if (set.isEmpty()) {
                chunkGrid.remove(chunkKey);
            }
        }
    }

    // --- Inner Class ---

    private static class TrackedVisual {
        private final NetworkVisual visual;
        private final Location location;
        private final Quaternionf rotation;

        // Кэширование для дельта-компрессии (последняя отправленная позиция для КАЖДОГО игрока может быть дорогой,
        // поэтому мы упростим: храним просто "последнее состояние" и сравниваем с ним.
        // В идеале нужно хранить per-player state, но для физики достаточно сравнить "изменился ли объект с прошлого тика".
        // НО: так как tick() итеративный, мы будем хранить "previous tick state" здесь.
        private final Vector3f lastPos = new Vector3f();
        private final Quaternionf lastRot = new Quaternionf();

        // Храним состояние, которое было успешно отправлено (или зафиксировано как стабильное)
        // Для оптимизации памяти в высоконагруженных системах мы не будем хранить мапу Player -> LastPos,
        // а будем полагаться на то, что physics loop обновляет TrackedVisual только если есть реальные изменения.
        // Однако метод updateState вызывается каждый тик из PhysicsWorld.

        // Решение: TrackedVisual хранит ТЕКУЩЕЕ состояние. А также состояние, которое было в ПРОШЛЫЙ раз, когда мы считали его "грязным".
        // Но так как у нас много игроков, "грязный для одного" = "грязный для всех".
        private final Vector3f syncedPos = new Vector3f();
        private final Quaternionf syncedRot = new Quaternionf();
        private boolean isDirtyGlobal = true;

        private TrackedVisual(NetworkVisual visual, Location location, Quaternionf rotation) {
            this.visual = visual;
            this.location = location;
            this.rotation = rotation;
            this.syncedPos.set((float)location.getX(), (float)location.getY(), (float)location.getZ());
            this.syncedRot.set(rotation);
        }

        public NetworkVisual visual() { return visual; }
        public Location location() { return location; }
        public Quaternionf rotation() { return rotation; }

        public void update(Location newLoc, Quaternionf newRot) {
            this.location.setWorld(newLoc.getWorld());
            this.location.setX(newLoc.getX());
            this.location.setY(newLoc.getY());
            this.location.setZ(newLoc.getZ());
            this.location.setYaw(newLoc.getYaw());
            this.location.setPitch(newLoc.getPitch());
            this.rotation.set(newRot);
        }

        /**
         * Проверяет, изменился ли объект достаточно сильно по сравнению с ПОСЛЕДНИМ ОТПРАВЛЕННЫМ состоянием.
         */
        public boolean isDirtyFor(Player player, float posThresholdSq, float rotThresholdDot) {
            // Рассчитываем дельты
            float dx = (float)location.getX() - syncedPos.x;
            float dy = (float)location.getY() - syncedPos.y;
            float dz = (float)location.getZ() - syncedPos.z;

            float distSq = dx*dx + dy*dy + dz*dz;
            if (distSq > posThresholdSq) return true;

            // Dot product кватернионов: 1.0 = одинаковые, -1.0 = одинаковые (противоположные), 0 = 90 градусов
            float dot = Math.abs(rotation.dot(syncedRot));
            return dot < rotThresholdDot;
        }

        /**
         * Обновляет "синхронизированное" состояние. Вызывается после отправки пакетов.
         * Важно: в данной архитектуре мы обновляем synced state глобально.
         * Это компромисс: если объект дернулся, все игроки получат пакет.
         */
        public void markSent(Player player) {
            // Обновляем synced значения к текущим
            this.syncedPos.set((float)location.getX(), (float)location.getY(), (float)location.getZ());
            this.syncedRot.set(rotation);
        }
    }
}
