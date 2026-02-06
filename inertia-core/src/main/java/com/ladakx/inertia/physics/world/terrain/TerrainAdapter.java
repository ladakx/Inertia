package com.ladakx.inertia.physics.world.terrain;

import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.bukkit.Material;

/**
 * Определяет стратегию генерации статичной физической среды (ландшафта).
 */
public interface TerrainAdapter {

    /**
     * Инициализация адаптера при создании мира.
     * @param world Ссылка на физический мир.
     */
    void onEnable(PhysicsWorld world);

    /**
     * Очистка ресурсов при выгрузке мира.
     */
    void onDisable();

    /**
     * Вызывается при загрузке чанка (для динамической генерации ландшафта).
     */
    void onChunkLoad(int x, int z);

    /**
     * Вызывается при выгрузке чанка.
     */
    void onChunkUnload(int x, int z);

    /**
     * Вызывается при изменении блока, чтобы инвалидировать кеш terrain-данных.
     */
    default void onBlockChange(int x, int y, int z, Material oldMaterial, Material newMaterial) {
    }

    default void onChunkChange(int x, int z) {
    }
}
