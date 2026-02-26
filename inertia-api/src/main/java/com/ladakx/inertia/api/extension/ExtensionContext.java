package com.ladakx.inertia.api.extension;

import com.ladakx.inertia.api.services.ServiceRegistry;
import com.ladakx.inertia.api.InertiaApi;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Stable context passed to {@link InertiaExtension} instances.
 */
public interface ExtensionContext {
    @NotNull InertiaApi api();

    @NotNull Plugin owner();

    @NotNull ServiceRegistry services();
}
