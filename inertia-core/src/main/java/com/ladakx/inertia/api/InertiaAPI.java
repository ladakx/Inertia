package com.ladakx.inertia.api;

import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Головна точка входу в Inertia API.
 * Забезпечує доступ до створення та керування фізичними об'єктами.
 */
public abstract class InertiaAPI {

    private static InertiaAPI instance;

    /**
     * Отримати екземпляр API.
     *
     * @return Синглтон API.
     * @throws IllegalStateException Якщо Inertia ще не ініціалізована.
     */
    public static InertiaAPI get() {
        if (instance == null) {
            throw new IllegalStateException("InertiaAPI is not initialized. Check if Inertia plugin is enabled.");
        }
        return instance;
    }

    /**
     * Внутрішній метод для реєстрації реалізації API.
     * Викликається лише ядром Inertia.
     */
    public static void setImplementation(@NotNull InertiaAPI implementation) {
        if (instance != null) {
            throw new IllegalStateException("InertiaAPI implementation is already registered.");
        }
        instance = implementation;
    }

    /**
     * Створює фізичний об'єкт у світі.
     *
     * @param location Локація спавну (світ, координати, ротація).
     * @param bodyId   ID тіла, визначений у bodies.yml (наприклад, "blocks.stone" або "ragdolls.steve").
     * @return Створений об'єкт або null, якщо світ не є фізичним або bodyId не знайдено.
     */
    @Nullable
    public abstract InertiaPhysicsBody createBody(@NotNull Location location, @NotNull String bodyId);

    /**
     * Перевіряє, чи є світ фізичним (чи завантажений він у SpaceManager).
     *
     * @param worldName Назва світу.
     * @return true, якщо фізика у світі активна.
     */
    public abstract boolean isWorldSimulated(@NotNull String worldName);
}