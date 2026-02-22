package com.ladakx.inertia.api.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Public config loader facade.
 * <p>
 * Supports loading YAML from:
 * <ul>
 *     <li>the Inertia plugin data folder</li>
 *     <li>another plugin's data folder</li>
 *     <li>another plugin's bundled resource (optional fallback)</li>
 * </ul>
 * plus optional validation.
 */
public interface ConfigService {

    @NotNull FileConfiguration loadYaml(@NotNull Plugin plugin,
                                        @NotNull String relativePath,
                                        @Nullable ConfigValidator validator);

    default @NotNull FileConfiguration loadYaml(@NotNull Plugin plugin, @NotNull String relativePath) {
        return loadYaml(plugin, relativePath, null);
    }

    @NotNull FileConfiguration loadYamlFromPlugin(@NotNull String pluginName,
                                                  @NotNull String relativePath,
                                                  @Nullable ConfigValidator validator);

    default @NotNull FileConfiguration loadYamlFromPlugin(@NotNull String pluginName, @NotNull String relativePath) {
        return loadYamlFromPlugin(pluginName, relativePath, null);
    }
}

