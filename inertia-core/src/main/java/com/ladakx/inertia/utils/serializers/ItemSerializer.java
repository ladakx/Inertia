package com.ladakx.inertia.utils.serializers;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.utils.MinecraftVersions; // Ваш пакет
import com.ladakx.inertia.utils.StringUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;

public class ItemSerializer {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static ItemStack deserialize(ConfigurationSection section) {
        if (section == null) return new ItemStack(Material.AIR);

        String type = section.getString("type");
        Material material = Material.matchMaterial(type);
        if (material == null) {
            InertiaLogger.warn("Invalid material type: " + type);
            return new ItemStack(Material.STONE);
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        // 1. Name
        if (section.contains("name")) {
            meta.displayName(StringUtils.parseString(section.getString("name")));
        }

        // 2. Lore
        if (section.contains("lore")) {
            List<Component> lore = StringUtils.parseStringList(section.getStringList("lore"));
            meta.lore(lore);
        }

        // 3. Unbreakable
        if (section.contains("unbreakable")) {
            meta.setUnbreakable(section.getBoolean("unbreakable"));
        }

        // 4. Custom Model Data
        if (section.contains("custom-model-data")) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }

        // 5. Item Model (1.21.2 / 1.21.3+)
        if (section.contains("item-model")) {
            // 1.21.2 is technically part of TRICKY_TRIALS update cycle but handled carefully
            // TRICKY_TRIALS is 1.21. Version 2 is 1.21.2.
            if (MinecraftVersions.TRICKY_TRIALS.isAtLeast() &&
                    MinecraftVersions.CURRENT.compareTo(MinecraftVersions.TRICKY_TRIALS.get(2)) >= 0) {
                try {
                    String modelKey = section.getString("item-model");
                    if (modelKey != null && modelKey.contains(":")) {
                        // NamespacedKey support
                        String[] parts = modelKey.split(":");
                        meta.setItemModel(new NamespacedKey(parts[0], parts[1]));
                    }
                } catch (NoSuchMethodError ignored) {
                    // Fallback if method doesn't exist despite version check (safe guard)
                }
            }
        }

        // 6. Hide Flags
        if (section.getBoolean("hide-flags")) {
            meta.addItemFlags(ItemFlag.values());
        }

        // 7. Enchantment Glint Override (1.20.5+)
        if (section.contains("enchantment-glint-override")) {
            // 1.20.5 is index 5 in TRAILS_AND_TAILS
            if (MinecraftVersions.TRAILS_AND_TAILS.isAtLeast() &&
                    MinecraftVersions.CURRENT.compareTo(MinecraftVersions.TRAILS_AND_TAILS.get(5)) >= 0) {
                try {
                    meta.setEnchantmentGlintOverride(section.getBoolean("enchantment-glint-override"));
                } catch (NoSuchMethodError ignored) {}
            }
        }

        // 8. Enchantments
        if (section.contains("enchantments")) {
            for (String enchantEntry : section.getStringList("enchantments")) {
                String[] parts = enchantEntry.split(" ");
                if (parts.length >= 2) {
                    Enchantment enchantment = getEnchantment(parts[0]);
                    int level = Integer.parseInt(parts[1]);
                    if (enchantment != null) {
                        meta.addEnchant(enchantment, level, true);
                    }
                }
            }
        }

        // 9. Durability (Damage & Max Damage)
        if (section.contains("durability.damage")) {
            if (meta instanceof Damageable damageable) {
                damageable.setDamage(section.getInt("durability.damage"));
            }
        }
        // Max Damage (1.20.5+)
        if (section.contains("durability.max-damage")) {
            if (MinecraftVersions.TRAILS_AND_TAILS.isAtLeast() &&
                    MinecraftVersions.CURRENT.compareTo(MinecraftVersions.TRAILS_AND_TAILS.get(5)) >= 0) {
                try {
                    if (meta instanceof Damageable damageable) {
                        damageable.setMaxDamage(section.getInt("durability.max-damage"));
                    }
                } catch (NoSuchMethodError ignored) {}
            }
        }

        // 10. Skull Owner
        if (meta instanceof SkullMeta skullMeta && section.contains("skull-owning-player")) {
            String skullData = section.getString("skull-owning-player");
            assert skullData != null;
            applySkullTexture(skullMeta, skullData);
        }

        // 11. Colors (Potion & Leather)
        if (section.contains("potion-color") && meta instanceof PotionMeta potionMeta) {
            potionMeta.setColor(parseColorFromConfig(section.getString("potion-color")));
        }
        if (section.contains("leather-color") && meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(parseColorFromConfig(section.getString("leather-color")));
        }

        // 12. Armor Trims (1.20+)
        if (MinecraftVersions.TRAILS_AND_TAILS.isAtLeast() && meta instanceof org.bukkit.inventory.meta.ArmorMeta armorMeta) {
            if (section.contains("trim-pattern") && section.contains("trim-material")) {
                try {
                    applyArmorTrim(armorMeta, section.getString("trim-pattern"), section.getString("trim-material"));
                } catch (Exception e) {
                    InertiaLogger.warn("Failed to apply armor trim: " + e.getMessage());
                }
            }
        }

        // 13. PDC Tags
        if (section.contains("tags")) {
            var container = meta.getPersistentDataContainer();
            for (String tagEntry : section.getStringList("tags")) {
                String[] parts = tagEntry.split(" ");
                if (parts.length >= 2) {
                    NamespacedKey key = new NamespacedKey("inertia", parts[0]); // Using 'inertia' namespace
                    try {
                        int value = Integer.parseInt(parts[1]);
                        container.set(key, PersistentDataType.INTEGER, value);
                    } catch (NumberFormatException e) {
                        container.set(key, PersistentDataType.STRING, parts[1]);
                    }
                }
            }
        }

        item.setItemMeta(meta);

        // 14. Light Level (BlockData handling for Light blocks)
        if (section.contains("light-level") && material == Material.LIGHT) {
            if (item.getItemMeta() instanceof BlockDataMeta blockDataMeta) {
                BlockData data = material.createBlockData();
                if (data instanceof Levelled levelled) {
                    levelled.setLevel(section.getInt("light-level"));
                    blockDataMeta.setBlockData(levelled);
                    item.setItemMeta(blockDataMeta);
                }
            }
        }

        return item;
    }

    // --- Helper Methods ---

    public static Color parseColorFromConfig(String colorStr) {
        if (colorStr == null) return Color.WHITE;
        if (colorStr.contains(",")) {
            String[] rgb = colorStr.split(",");
            return Color.fromRGB(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
        }
        try {
            return (Color) Color.class.getField(colorStr.toUpperCase()).get(null);
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    public static Enchantment getEnchantment(String key) {
        NamespacedKey namespacedKey = NamespacedKey.minecraft(key.toLowerCase());
        Enchantment enchantment = Enchantment.getByKey(namespacedKey);
        if (enchantment == null) {
            // Fallback for legacy names if needed, or just return null
            return Enchantment.getByName(key.toUpperCase());
        }
        return enchantment;
    }

    // --- Specific Feature Handlers ---

    /**
     * Handles Skull Textures (URL) and UUIDs safely across versions using Reflection/Paper API.
     */
    public static void applySkullTexture(SkullMeta meta, String data) {
        // If it's just a name or UUID
        if (data.length() <= 16 || (data.length() == 36 && data.contains("-"))) {
            try {
                UUID uuid = UUID.fromString(data);
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            } catch (IllegalArgumentException e) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(data));
            }
            return;
        }

        // If it's a URL (Texture)
        // We use GameProfile reflection to be compatible with 1.16.5 -> 1.21+ without strict NMS version imports
        try {
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), null);
            String base64;
            if (data.startsWith("http")) {
                String encodedData = Base64.getEncoder().encodeToString(String.format("{textures:{SKIN:{url:\"%s\"}}}", data).getBytes());
                base64 = encodedData;
            } else {
                base64 = data; // Assume already Base64
            }

            profile.getProperties().put("textures", new com.mojang.authlib.properties.Property("textures", base64));

            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            InertiaLogger.warn("Failed to set skull texture: " + e.getMessage());
        }
    }

    /**
     * Applies Armor Trims. Only called if version >= 1.20
     */
    public static void applyArmorTrim(org.bukkit.inventory.meta.ArmorMeta meta, String patternStr, String materialStr) {
        // Registry Access for 1.20+
        Registry<org.bukkit.inventory.meta.trim.TrimPattern> patternRegistry = Registry.TRIM_PATTERN;
        Registry<org.bukkit.inventory.meta.trim.TrimMaterial> materialRegistry = Registry.TRIM_MATERIAL;

        org.bukkit.inventory.meta.trim.TrimPattern pattern = patternRegistry.get(NamespacedKey.minecraft(patternStr.toLowerCase()));
        org.bukkit.inventory.meta.trim.TrimMaterial material = materialRegistry.get(NamespacedKey.minecraft(materialStr.toLowerCase()));

        if (pattern != null && material != null) {
            meta.setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(material, pattern));
        }
    }
}