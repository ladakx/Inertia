package com.ladakx.inertia.api.rendering.model;

import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Runtime registry for render model definitions.
 * <p>
 * Supports two integration styles:
 * <ul>
 *     <li>programmatic registration (build {@link RenderModelDefinition} directly)</li>
 *     <li>config-based registration (import a YAML section that uses the same format as {@code render.yml})</li>
 * </ul>
 */
@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface RenderModelRegistryService {

    /**
     * Register a single render model definition.
     */
    @NotNull ApiResult<Void> registerModel(@NotNull Plugin owner, @NotNull RenderModelDefinition model);

    /**
     * Register render models from a config section (format-compatible with {@code render.yml}).
     *
     * @return number of registered models
     */
    @NotNull ApiResult<Integer> registerFromConfigSection(@NotNull Plugin owner,
                                                          @NotNull ConfigurationSection section,
                                                          @NotNull RenderIdPolicy idPolicy);

    default @NotNull ApiResult<Integer> registerFromConfigSection(@NotNull Plugin owner, @NotNull ConfigurationSection section) {
        return registerFromConfigSection(owner, section, RenderIdPolicy.NAMESPACE_OWNER_IF_MISSING);
    }

    @Nullable RenderModelDefinition get(@NotNull String id);

    @NotNull Collection<RenderModelDefinition> getAll();

    @NotNull ApiResult<Void> unregister(@NotNull String id);

    void unregisterAll(@NotNull Plugin owner);
}

