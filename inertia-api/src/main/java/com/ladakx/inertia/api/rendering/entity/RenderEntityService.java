package com.ladakx.inertia.api.rendering.entity;

import com.ladakx.inertia.api.InertiaApiAccess;
import com.ladakx.inertia.api.rendering.model.RenderingModelServices;
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

    /**
     * Convenience overload: create a model instance by ID using the runtime registry.
     * <p>
     * Models can be registered by other plugins via {@code RenderingModelServices.MODELS}.
     *
     * @throws IllegalArgumentException if the model id is not registered
     */
    default @NotNull RenderModelInstance createModel(@NotNull World world,
                                                     @NotNull Location location,
                                                     @NotNull Quaternionf rotation,
                                                     @NotNull String modelId) {
        RenderModelDefinition def = InertiaApiAccess.resolve()
                .services()
                .require(RenderingModelServices.MODELS)
                .get(modelId);
        if (def == null) {
            throw new IllegalArgumentException("Unknown render model id: " + modelId);
        }
        return createModel(world, location, rotation, def);
    }
}
