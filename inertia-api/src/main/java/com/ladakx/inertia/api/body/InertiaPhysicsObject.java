package com.ladakx.inertia.api.body;

import com.ladakx.inertia.jolt.object.PhysicsObjectType;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Представляє фізичний об'єкт у світі Inertia.
 * Цей інтерфейс надає методи для керування об'єктом без прямого доступу до Jolt-сутностей.
 */
public interface InertiaPhysicsObject {

    /**
     * Отримати унікальний ідентифікатор тіла (з конфігурації bodies.yml).
     */
    @NotNull
    String getBodyId();

    /**
     * Отримати тип фізичного об'єкта (BLOCK, CHAIN, RAGDOLL).
     */
    @NotNull
    PhysicsObjectType getType();

    /**
     * Повністю видаляє фізичне тіло та його візуальну частину зі світу.
     */
    void remove();

    /**
     * Перевіряє, чи об'єкт все ще існує у фізичному просторі.
     */
    boolean isValid();

    /**
     * Телепортує об'єкт у нову локацію.
     * Це "жорстке" переміщення, яке миттєво змінює позицію фізичного тіла.
     *
     * @param location Нова позиція та ротація.
     */
    void teleport(@NotNull Location location);

    /**
     * Задає лінійну швидкість об'єкта.
     *
     * @param velocity Вектор швидкості (в метрах за секунду).
     */
    void setLinearVelocity(@NotNull Vector velocity);

    /**
     * Отримати поточну інтерпольовану позицію об'єкта (візуальну).
     */
    @NotNull
    Location getLocation();
}