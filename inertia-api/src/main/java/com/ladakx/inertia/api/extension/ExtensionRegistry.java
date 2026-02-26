package com.ladakx.inertia.api.extension;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Registers {@link InertiaExtension} instances for the current Inertia runtime.
 */
public interface ExtensionRegistry {

    @NotNull ExtensionHandle register(@NotNull Plugin owner, @NotNull InertiaExtension extension);

    void unregisterAll(@NotNull Plugin owner);
}

