package com.ladakx.inertia.features.integrations;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorldEditIntegration {

    // Храним измененные чанки глобально: <Мир, Список<КлючЧанка>>
    // Используем ConcurrentHashMap для потокобезопасности (FAWE работает в асинхроне)
    private final Set<WorldChunkKey> dirtyChunks = ConcurrentHashMap.newKeySet();

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null &&
                Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            return;
        }

        try {
            WorldEdit.getInstance().getEventBus().register(this);
            InertiaLogger.info("WorldEdit/FAWE integration enabled.");

            // Запускаем задачу, которая будет периодически (каждые 5 тиков) обновлять физику
            // в измененных чанках. Это заменяет логику commit(), который теперь final.
            Bukkit.getScheduler().runTaskTimer(InertiaPlugin.getInstance(), this::flushUpdates, 10L, 5L);

        } catch (Throwable e) {
            InertiaLogger.warn("Failed to hook into WorldEdit: " + e.getMessage());
        }
    }

    private void flushUpdates() {
        if (dirtyChunks.isEmpty()) return;

        // Перебираем накопленные изменения
        for (WorldChunkKey key : dirtyChunks) {
            PhysicsWorld space = InertiaPlugin.getInstance().getSpaceManager().getSpace(key.world);
            if (space != null) {
                // Вызываем обновление всего чанка в физическом мире
                space.onChunkChange(key.x, key.z);
            }
        }

        // Очищаем список после обработки
        dirtyChunks.clear();
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        if (event.getWorld() == null) return;

        // Используем BukkitAdapter.adapt(), чтобы корректно получить org.bukkit.World
        org.bukkit.World bukkitWorld = BukkitAdapter.adapt(event.getWorld());
        if (bukkitWorld == null) return;

        event.setExtent(new InertiaExtentDelegate(event.getExtent(), bukkitWorld));
    }

    /**
     * Обертка над Extent WorldEdit'а.
     * Просто фиксирует координаты чанков, которые были затронуты.
     */
    private class InertiaExtentDelegate extends AbstractDelegateExtent {
        private final org.bukkit.World world;

        protected InertiaExtentDelegate(Extent extent, org.bukkit.World world) {
            super(extent);
            this.world = world;
        }

        @Override
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block) throws WorldEditException {
            // Вызываем оригинальный метод. Он может бросить WorldEditException.
            boolean success = super.setBlock(position, block);

            if (success) {
                // Если блок установлен успешно, помечаем чанк как "грязный"
                markDirty(position.getX(), position.getZ());
            }
            return success;
        }

        private void markDirty(int x, int z) {
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            dirtyChunks.add(new WorldChunkKey(world, chunkX, chunkZ));
        }
    }

    // Вспомогательный рекорд для хранения ключа чанка вместе с миром
    private record WorldChunkKey(World world, int x, int z) {}
}