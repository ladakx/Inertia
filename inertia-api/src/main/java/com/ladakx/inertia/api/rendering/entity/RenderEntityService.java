package com.ladakx.inertia.api.rendering.entity;

import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

/**
 * High-level rendering API: creates tracked entities/models with transform composition and enable/disable support.
 */
public interface RenderEntityService {

    @NotNull RenderEntity createEntity(@NotNull World world,
                                       @NotNull Location location,
                                       @NotNull Quaternionf rotation,
                                       @NotNull RenderEntityDefinition definition);

    @NotNull RenderModelInstance createModel(@NotNull World world,
                                             @NotNull Location location,
                                             @NotNull Quaternionf rotation,
                                             @NotNull RenderModelDefinition modelDefinition);
}

