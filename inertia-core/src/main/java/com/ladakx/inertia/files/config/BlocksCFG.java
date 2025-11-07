package com.ladakx.inertia.files.config;

import com.ladakx.inertia.bullet.block.BulletBlockSettings;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public class BlocksCFG {

    public final BulletBlockSettings[] properties;

    public BlocksCFG(FileConfiguration cfg) {
        this.properties = new BulletBlockSettings[Material.values().length];
        for (Material mat : Material.values()) {
            properties[mat.ordinal()] = BulletBlockSettings.getBlockSettings(mat, cfg);
        }
    }
}
