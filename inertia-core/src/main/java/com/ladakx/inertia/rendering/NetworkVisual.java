package com.ladakx.inertia.rendering;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;

/**
 * Абстракція над packet-based entity. Об'єкт не має Bukkit-Entity та керує лише
 * ID, позицією, поворотом і метаданими, що відправляються гравцю напряму.
 */
public interface NetworkVisual {

    int getId();

    void spawnFor(Player player);

    void destroyFor(Player player);

    void updatePositionFor(Player player, Location location, Quaternionf rotation);

    void updateMetadataFor(Player player);

    void setGlowing(boolean glowing);
}
