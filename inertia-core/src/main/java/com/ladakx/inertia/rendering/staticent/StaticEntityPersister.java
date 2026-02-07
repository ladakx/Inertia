package com.ladakx.inertia.rendering.staticent;

import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public interface StaticEntityPersister {
    @Nullable Entity persist(Location location, RenderEntityDefinition definition, Quaternionf leftRotation, StaticEntityMetadata metadata);
}

