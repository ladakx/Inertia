package com.ladakx.inertia.configuration.files;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public abstract class CustomConfiguration {

    protected final JavaPlugin plugin;
    protected final String fileName;
    protected final File file;
    protected FileConfiguration config;

    public CustomConfiguration(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);

        saveDefault();
        reload();
    }

    private void saveDefault() {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                // Ensure nested directories exist (e.g. "lang/lang_en.yml")
                // so saveResource() can write the file.
                parent.mkdirs();
            }
            plugin.saveResource(fileName, false);
        }
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (config == null) return;
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + fileName, e);
        }
    }

    public FileConfiguration getConfig() {
        if (config == null) reload();
        return config;
    }
}
