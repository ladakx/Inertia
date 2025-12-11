package com.ladakx.inertia.nms.render;

import com.ladakx.inertia.nms.render.runtime.VisualObject;
import com.ladakx.inertia.render.config.RenderEntityDefinition;
import org.bukkit.Location;
import org.bukkit.World;

public interface RenderFactory {
    /**
     * Створює візуальний об'єкт.
     * Реалізація вирішує, чи це буде ArmorStand (на 1.16) чи Display (на 1.21).
     */
    VisualObject create(World world, Location origin, RenderEntityDefinition definition);
}