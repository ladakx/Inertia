package com.ladakx.inertia.rendering;

import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Абстракція над Entity (ArmorStand або Display Entity).
 * Дозволяє керувати візуалізацією без прив'язки до версії API.
 */
public interface VisualEntity {

    /**
     * Повне оновлення позиції та трансформації.
     */
    void update(Location location, Quaternionf rotation, Vector3f center, boolean rotLocalOff);

    void setVisible(boolean visible);

    void remove();

    void setGlowing(boolean glowing);

    boolean isValid();
}