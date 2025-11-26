package com.ladakx.inertia.utils.serializers;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.utils.MinecraftVersions; // Ваш пакет
import com.ladakx.inertia.utils.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;

public class ItemSerializer {

    public static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ItemSerializer() {
        // utility class
    }

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
            Component name = StringUtils.parseString(section.getString("name"));
            setDisplayName(meta, name);
        }

        // 2. Lore
        if (section.contains("lore")) {
            List<Component> lore = StringUtils.parseStringList(section.getStringList("lore"));
            setLore(meta, lore);
        }

        // 3. Unbreakable
        if (section.contains("unbreakable")) {
            meta.setUnbreakable(section.getBoolean("unbreakable"));
        }

        // 4. Custom Model Data
        if (section.contains("custom-model-data")) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }

        // 5. Item Model (1.21.2 / 1.21.3+) – через рефлексію
        if (section.contains("item-model")) {
            if (MinecraftVersions.TRICKY_TRIALS.isAtLeast() &&
                    MinecraftVersions.CURRENT.compareTo(MinecraftVersions.TRICKY_TRIALS.get(2)) >= 0) {
                String modelKey = section.getString("item-model");
                setItemModel(meta, modelKey); // рефлексія всередині
            }
        }

        // 6. Hide Flags
        if (section.getBoolean("hide-flags")) {
            meta.addItemFlags(ItemFlag.values());
        }

        // 7. Enchantment Glint Override (1.20.5+) – через рефлексію
        if (section.contains("enchantment-glint-override")) {
            if (MinecraftVersions.TRAILS_AND_TAILS.isAtLeast() &&
                    MinecraftVersions.CURRENT.compareTo(MinecraftVersions.TRAILS_AND_TAILS.get(5)) >= 0) {
                setEnchantmentGlintOverride(meta, section.getBoolean("enchantment-glint-override"));
            }
        }

        // 8. Enchantments
        if (section.contains("enchantments")) {
            for (String enchantEntry : section.getStringList("enchantments")) {
                String[] parts = enchantEntry.split(" ");
                if (parts.length >= 2) {
                    Enchantment enchantment = getEnchantment(parts[0]);
                    try {
                        int level = Integer.parseInt(parts[1]);
                        if (enchantment != null) {
                            meta.addEnchant(enchantment, level, true);
                        }
                    } catch (NumberFormatException ignore) {
                        InertiaLogger.warn("Invalid enchantment level: " + enchantEntry);
                    }
                }
            }
        }

        // 9. Durability (Damage & Max Damage)
        if (section.contains("durability.damage")) {
            if (meta instanceof Damageable) {
                Damageable damageable = (Damageable) meta;
                damageable.setDamage(section.getInt("durability.damage"));
            }
        }

        // Max Damage (1.20.5+) – через рефлексію
        if (section.contains("durability.max-damage")) {
            if (MinecraftVersions.TRAILS_AND_TAILS.isAtLeast() &&
                    MinecraftVersions.CURRENT.compareTo(MinecraftVersions.TRAILS_AND_TAILS.get(5)) >= 0) {
                if (meta instanceof Damageable) {
                    setMaxDamage((Damageable) meta, section.getInt("durability.max-damage"));
                }
            }
        }

        // 10. Skull Owner
        if (section.contains("skull-owning-player") && meta instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta) meta;
            String skullData = section.getString("skull-owning-player");
            if (skullData != null) {
                applySkullTexture(skullMeta, skullData);
            }
        }

        // 11. Colors (Potion & Leather)
        if (section.contains("potion-color") && meta instanceof PotionMeta) {
            PotionMeta potionMeta = (PotionMeta) meta;
            potionMeta.setColor(parseColorFromConfig(section.getString("potion-color")));
        }
        if (section.contains("leather-color") && meta instanceof LeatherArmorMeta) {
            LeatherArmorMeta leatherMeta = (LeatherArmorMeta) meta;
            leatherMeta.setColor(parseColorFromConfig(section.getString("leather-color")));
        }

        // 12. Armor Trims (1.20+) – через рефлексію, без прямого ArmorMeta
        if (MinecraftVersions.TRAILS_AND_TAILS.isAtLeast()
                && section.contains("trim-pattern") && section.contains("trim-material")) {
            applyArmorTrim(meta,
                    section.getString("trim-pattern"),
                    section.getString("trim-material"));
        }

        // 13. PDC Tags
        if (section.contains("tags")) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
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

        // 14. Light Level (BlockData handling for Light blocks; працює тільки там, де LIGHT існує)
        if (section.contains("light-level") && isLightMaterial(material)) {
            ItemMeta currentMeta = item.getItemMeta();
            if (currentMeta instanceof BlockDataMeta) {
                BlockDataMeta blockDataMeta = (BlockDataMeta) currentMeta;
                BlockData data = material.createBlockData();
                if (data instanceof Levelled) {
                    Levelled levelled = (Levelled) data;
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
            try {
                return Color.fromRGB(
                        Integer.parseInt(rgb[0].trim()),
                        Integer.parseInt(rgb[1].trim()),
                        Integer.parseInt(rgb[2].trim())
                );
            } catch (NumberFormatException e) {
                return Color.WHITE;
            }
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
            // Fallback for legacy names if needed
            enchantment = Enchantment.getByName(key.toUpperCase());
        }
        return enchantment;
    }

    // --- Adventure name / lore cross-version ---

    /**
     * Встановлює displayName, працює як з Adventure API (нові версії),
     * так і з legacy String (1.16.5).
     */
    private static void setDisplayName(ItemMeta meta, Component component) {
        if (meta == null || component == null) return;

        // Спробувати новий API: ItemMeta#displayName(Component)
        try {
            Method method = meta.getClass().getMethod("displayName", Component.class);
            method.invoke(meta, component);
            return;
        } catch (NoSuchMethodException ignored) {
            // старі версії – fallback нижче
        } catch (Exception e) {
            InertiaLogger.warn("Failed to set display name via Adventure API: " + e.getMessage());
        }

        // Legacy API – setDisplayName(String)
        try {
            String legacy = LegacyComponentSerializer.legacySection().serialize(component);
            Method legacyMethod = meta.getClass().getMethod("setDisplayName", String.class);
            legacyMethod.invoke(meta, legacy);
        } catch (Exception e) {
            InertiaLogger.warn("Failed to set legacy display name: " + e.getMessage());
        }
    }

    /**
     * Встановлює lore, працює і з Adventure API, і з legacy String API.
     */
    private static void setLore(ItemMeta meta, List<Component> lore) {
        if (meta == null || lore == null) return;

        // Спробувати новий API: ItemMeta#lore(List<Component>)
        try {
            Method method = meta.getClass().getMethod("lore", List.class);
            method.invoke(meta, lore);
            return;
        } catch (NoSuchMethodException ignored) {
            // старі версії – fallback нижче
        } catch (Exception e) {
            InertiaLogger.warn("Failed to set lore via Adventure API: " + e.getMessage());
        }

        // Legacy API – setLore(List<String>)
        try {
            List<String> legacyLore = new ArrayList<>();
            LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
            for (Component line : lore) {
                legacyLore.add(serializer.serialize(line));
            }
            Method legacyMethod = meta.getClass().getMethod("setLore", List.class);
            legacyMethod.invoke(meta, legacyLore);
        } catch (Exception e) {
            InertiaLogger.warn("Failed to set legacy lore: " + e.getMessage());
        }
    }

    // --- Версійні фічі через рефлексію ---

    /**
     * Item Model (1.21.2+) – використовує рефлексію, щоб не ламати компіляцію на 1.16.5.
     */
    private static void setItemModel(ItemMeta meta, String modelKey) {
        if (meta == null || modelKey == null || !modelKey.contains(":")) return;

        String[] parts = modelKey.split(":", 2);
        NamespacedKey key = new NamespacedKey(parts[0], parts[1]);

        try {
            Method method = meta.getClass().getMethod("setItemModel", NamespacedKey.class);
            method.invoke(meta, key);
        } catch (NoSuchMethodException ignored) {
            // Версія без ItemModel – просто ігноруємо
        } catch (Exception e) {
            InertiaLogger.warn("Failed to set item model: " + e.getMessage());
        }
    }

    /**
     * Enchantment glint override (1.20.5+) – через рефлексію.
     */
    private static void setEnchantmentGlintOverride(ItemMeta meta, boolean value) {
        if (meta == null) return;
        try {
            Method m = meta.getClass().getMethod("setEnchantmentGlintOverride", boolean.class);
            m.invoke(meta, value);
        } catch (NoSuchMethodException ignored) {
            // старі версії – немає такої функції
        } catch (Exception e) {
            InertiaLogger.warn("Failed to set enchantment glint override: " + e.getMessage());
        }
    }

    /**
     * Max damage (1.20.5+) – через рефлексію.
     */
    private static void setMaxDamage(Damageable damageable, int maxDamage) {
        if (damageable == null) return;
        try {
            Method m = damageable.getClass().getMethod("setMaxDamage", int.class);
            m.invoke(damageable, maxDamage);
        } catch (NoSuchMethodException ignored) {
            // старі версії
        } catch (Exception e) {
            InertiaLogger.warn("Failed to set max damage: " + e.getMessage());
        }
    }

    /**
     * Перевірка, що Material – LIGHT, але без прямого посилання на Material.LIGHT
     * (щоб компілювалось на 1.16.5, де LIGHT ще немає).
     */
    private static boolean isLightMaterial(Material material) {
        if (material == null) return false;
        try {
            return "LIGHT".equalsIgnoreCase(material.name());
        } catch (Throwable ignored) {
            return false;
        }
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
                String encodedData = Base64.getEncoder().encodeToString(
                        String.format("{textures:{SKIN:{url:\"%s\"}}}", data).getBytes()
                );
                base64 = encodedData;
            } else {
                base64 = data; // Assume already Base64
            }

            profile.getProperties().put("textures",
                    new com.mojang.authlib.properties.Property("textures", base64));

            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            InertiaLogger.warn("Failed to set skull texture: " + e.getMessage());
        }
    }

    /**
     * Applies Armor Trims (1.20+).
     * Реалізація через рефлексію, щоб клас компілювався на 1.16.5.
     *
     * УВАГА: сигнатура змінена на ItemMeta, без ArmorMeta –
     * якщо ти викликав цей метод десь ще, просто передавай туди ItemMeta.
     */
    public static void applyArmorTrim(ItemMeta meta, String patternStr, String materialStr) {
        if (meta == null || patternStr == null || materialStr == null) return;

        try {
            // Class<?> armorMetaClass = org.bukkit.inventory.meta.ArmorMeta
            Class<?> armorMetaClass = Class.forName("org.bukkit.inventory.meta.ArmorMeta");
            if (!armorMetaClass.isInstance(meta)) {
                return; // item не є бронею з тримами
            }

            // org.bukkit.Registry
            Class<?> registryClass = Class.forName("org.bukkit.Registry");

            // TRIM_PATTERN / TRIM_MATERIAL
            Field trimPatternField = registryClass.getField("TRIM_PATTERN");
            Field trimMaterialField = registryClass.getField("TRIM_MATERIAL");

            Object patternRegistry = trimPatternField.get(null);
            Object materialRegistry = trimMaterialField.get(null);

            Method getMethod = registryClass.getMethod("get", NamespacedKey.class);

            NamespacedKey patternKey = NamespacedKey.minecraft(patternStr.toLowerCase());
            NamespacedKey materialKey = NamespacedKey.minecraft(materialStr.toLowerCase());

            Object pattern = getMethod.invoke(patternRegistry, patternKey);
            Object material = getMethod.invoke(materialRegistry, materialKey);

            if (pattern == null || material == null) {
                return;
            }

            Class<?> trimMaterialClass = Class.forName("org.bukkit.inventory.meta.trim.TrimMaterial");
            Class<?> trimPatternClass = Class.forName("org.bukkit.inventory.meta.trim.TrimPattern");
            Class<?> armorTrimClass = Class.forName("org.bukkit.inventory.meta.trim.ArmorTrim");

            Constructor<?> armorTrimCtor = armorTrimClass.getConstructor(trimMaterialClass, trimPatternClass);
            Object armorTrim = armorTrimCtor.newInstance(material, pattern);

            Method setTrim = armorMetaClass.getMethod("setTrim", armorTrimClass);
            setTrim.invoke(meta, armorTrim);
        } catch (ClassNotFoundException e) {
            // Версія < 1.20, немає цих класів – просто ігноруємо
        } catch (NoSuchFieldException | NoSuchMethodException ignored) {
            // Щось змінилось в API – ігноруємо тихо
        } catch (Exception e) {
            InertiaLogger.warn("Failed to apply armor trim: " + e.getMessage());
        }
    }
}