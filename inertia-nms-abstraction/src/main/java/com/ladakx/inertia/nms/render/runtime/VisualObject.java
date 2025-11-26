package com.ladakx.inertia.nms.render.runtime;

import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Абстракція над Entity (ArmorStand або Display Entity).
 * Дозволяє керувати візуалізацією без прив'язки до версії API.
 */
public interface VisualObject {

    /**
     * Повне оновлення позиції та трансформації.
     */
    void update(Location location, Quaternionf rotation, Vector3f scale);

    void setVisible(boolean visible);

    void remove();

    boolean isValid();
}