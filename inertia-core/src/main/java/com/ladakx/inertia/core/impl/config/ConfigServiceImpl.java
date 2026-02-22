package com.ladakx.inertia.core.impl.config;

import com.ladakx.inertia.api.config.ConfigService;
import com.ladakx.inertia.api.config.ConfigValidator;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ConfigServiceImpl implements ConfigService {

    @Override
    public @NotNull FileConfiguration loadYaml(@NotNull Plugin plugin,
                                               @NotNull String relativePath,
                                               @Nullable ConfigValidator validator) {
        Objects.requireNonNull(plugin, "plugin");
        validateRelativePath(relativePath);

        File file = new File(plugin.getDataFolder(), relativePath);
        FileConfiguration config;

        if (file.exists() && file.isFile()) {
            config = YamlConfiguration.loadConfiguration(file);
        } else {
            // Optional fallback: try reading from plugin jar resources.
            try (InputStream in = plugin.getResource(relativePath)) {
                if (in == null) {
                    throw new IllegalArgumentException("Config not found: plugin=" + plugin.getName() + ", path=" + relativePath);
                }
                config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
            }
        }

        if (validator != null) {
            validator.validate(config);
        }
        return config;
    }

    @Override
    public @NotNull FileConfiguration loadYamlFromPlugin(@NotNull String pluginName,
                                                         @NotNull String relativePath,
                                                         @Nullable ConfigValidator validator) {
        Objects.requireNonNull(pluginName, "pluginName");
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin not found: " + pluginName);
        }
        return loadYaml(plugin, relativePath, validator);
    }

    private void validateRelativePath(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath is blank");
        }
        String path = relativePath.replace('\\', '/');
        if (path.startsWith("/") || path.contains("..")) {
            throw new IllegalArgumentException("Invalid relativePath: " + relativePath);
        }
    }
}

