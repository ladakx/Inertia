//package com.ladakx.inertia.files.config;
//
//import com.ladakx.inertia.bullet.block.BulletBlockSettings;
//import org.bukkit.Material;
//import org.bukkit.configuration.file.FileConfiguration;
//
//import java.util.EnumMap;
//import java.util.Map;
//
///**
// * Optimized configuration for block physics settings.
// * Uses EnumMap for efficient memory usage instead of large arrays.
// */
//public class BlocksConfig {
//
//    private final Map<Material, BulletBlockSettings> blockSettings;
//
//    public BlocksConfig(FileConfiguration cfg) {
//        // EnumMap is much faster and memory-efficient than HashMap for Enums
//        this.blockSettings = new EnumMap<>(Material.class);
//
//        // Iterate only through keys present in the config, not all 1000+ materials
//        for (String key : cfg.getKeys(false)) {
//            Material mat = Material.getMaterial(key.toUpperCase());
//
//            if (mat != null) {
//                try {
//                    BulletBlockSettings settings = BulletBlockSettings.getBlockSettings(mat, cfg);
//                    if (settings != null) {
//                        blockSettings.put(mat, settings);
//                    }
//                } catch (Exception e) {
//                    // Log error but don't stop loading other blocks
//                    // InertiaLogger.error("Failed to load block settings for " + key, e);
//                }
//            }
//        }
//    }
//
//    /**
//     * Get physics settings for a material.
//     * @param material The Bukkit material
//     * @return Settings or null if not configured
//     */
//    public BulletBlockSettings getSettings(Material material) {
//        return blockSettings.get(material);
//    }
//
//    public boolean hasSettings(Material material) {
//        return blockSettings.containsKey(material);
//    }
//
//    public Map<Material, BulletBlockSettings> getAllSettings() {
//        return blockSettings;
//    }
//}