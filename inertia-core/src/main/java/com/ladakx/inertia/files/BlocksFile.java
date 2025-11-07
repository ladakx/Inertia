package com.ladakx.inertia.files;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.files.config.MessagesCFG;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class BlocksFile {
    private final String path;
    private File file;
    private FileConfiguration config;

    /** Create file */
    public BlocksFile() {
        path = "blocks.yml";

        if (!new File(InertiaPlugin.getInstance().getDataFolder(), path).exists()) {
            InertiaPlugin.getInstance().saveResource(path, false);

            file = new File(InertiaPlugin.getInstance().getDataFolder(), path);
            config = YamlConfiguration.loadConfiguration(file);
        } else {
            file = new File(InertiaPlugin.getInstance().getDataFolder(), path);
            config = YamlConfiguration.loadConfiguration(file);
        }
    }

    /** Save file */
    public void save() {
        file = new File(InertiaPlugin.getInstance().getDataFolder(), path);

        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file "+path, e);
        }

        config = YamlConfiguration.loadConfiguration(file);
        MessagesCFG.refreshAll();
    }

    /** Reload file */
    public void reload() {
        file = new File(InertiaPlugin.getInstance().getDataFolder(), path);
        config = YamlConfiguration.loadConfiguration(file);
        MessagesCFG.refreshAll();
    }

    /** Get config */
    public FileConfiguration getConfig() {
        return config;
    }
}
