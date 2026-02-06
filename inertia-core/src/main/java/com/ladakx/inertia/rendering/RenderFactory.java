package com.ladakx.inertia.rendering;

import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

public interface RenderFactory {
    NetworkVisual create(World world, Location origin, RenderEntityDefinition definition);

    // Новый метод для создания дебаг-линий
    NetworkVisual createDebugLine(World world, Vector start, Vector end, float thickness, Color color);
}
