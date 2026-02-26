package com.ladakx.inertia.api.rendering.entity;

import com.ladakx.inertia.rendering.NetworkVisual;
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

    /**
     * Registers a custom {@link NetworkVisual} and returns a managed {@link RenderEntity} wrapper.
     * <p>
     * This is the recommended integration point for external plugins that implement their own visuals
     * (packet-level or otherwise) but want Inertia's transform composition and tracker lifecycle.
     * <p>
     * The returned entity:
     * <ul>
     *     <li>is immediately registered in the tracker</li>
     *     <li>supports {@link RenderEntity#transforms()} composition</li>
     *     <li>unregisters the visual when {@link RenderEntity#close()} is called</li>
     * </ul>
     */
    @NotNull RenderEntity registerVisual(@NotNull Location location,
                                         @NotNull Quaternionf rotation,
                                         @NotNull NetworkVisual visual);

    @NotNull RenderModelInstance createModel(@NotNull World world,
                                             @NotNull Location location,
                                             @NotNull Quaternionf rotation,
                                             @NotNull RenderModelDefinition modelDefinition);
}
