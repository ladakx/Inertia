package com.ladakx.inertia.rendering;

import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import org.bukkit.Location;
import org.bukkit.World;

public interface RenderFactory {
    NetworkVisual create(World world, Location origin, RenderEntityDefinition definition);
}
