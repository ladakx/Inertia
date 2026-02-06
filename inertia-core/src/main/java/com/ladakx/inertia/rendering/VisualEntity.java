package com.ladakx.inertia.rendering;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Абстракція над Entity (ArmorStand або Display Entity).
 * Дозволяє керувати візуалізацією без прив'язки до версії API.
 */
public interface VisualEntity {

    void update(Location location, Quaternionf rotation, Vector3f center, boolean rotLocalOff);

    void setVisible(boolean visible);

    void remove();

    void setGlowing(boolean glowing);

    /**
     * Optional capability: update the displayed item for ITEM_DISPLAY / emulated item displays.
     *
     * @return true if this visual entity supports updating the item stack.
     */
    default boolean setItemStack(ItemStack stack) {
        return false;
    }

    PersistentDataContainer getPersistentDataContainer();

    boolean isValid();

    boolean getPersistent();

    void setPersistent(boolean persistent);
}
