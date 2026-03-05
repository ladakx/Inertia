package com.ladakx.inertia.api.transport;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record TransportOwner(@NotNull String pluginName) {
    public TransportOwner {
        Objects.requireNonNull(pluginName, "pluginName");
        if (pluginName.isBlank()) {
            throw new IllegalArgumentException("pluginName cannot be blank");
        }
    }

    public static @NotNull TransportOwner of(@NotNull Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new TransportOwner(plugin.getName());
    }
}
