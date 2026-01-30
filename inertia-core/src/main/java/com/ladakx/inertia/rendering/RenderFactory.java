package com.ladakx.inertia.rendering;

import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import org.bukkit.Location;
import org.bukkit.World;

public interface RenderFactory {
    /**
     * Створює візуальний об'єкт.
     * Реалізація вирішує, чи це буде ArmorStand (на 1.16) чи Display (на 1.21).
     */
    VisualEntity create(World world, Location origin, RenderEntityDefinition definition);
}