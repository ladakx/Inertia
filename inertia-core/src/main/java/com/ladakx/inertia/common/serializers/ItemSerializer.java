package com.ladakx.inertia.common.serializers;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.MinecraftVersions;
import com.ladakx.inertia.common.utils.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class ItemSerializer {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

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
            Component nameComp = StringUtils.parseString(section.getString("name"));
            setDisplayNameSafe(meta, nameComp);
        }

        // 2. Lore
        if (section.contains("lore")) {
            List<Component> lore = StringUtils.parseStringList(section.getStringList("lore"));
            setLoreSafe(meta, lore);
        }

        // 3. Unbreakable
        if (section.contains("unbreakable")) {
            meta.setUnbreakable(section.getBoolean("unbreakable"));
        }

        // 4. Custom Model Data (Updated for Component Support)
        if (section.contains("custom-model-data")) {
            // Вариант 1: Классическое число (Integer)
            if (section.isInt("custom-model-data")) {
                int data = section.getInt("custom-model-data");
                if (meta.hasCustomModelData()) {
                    meta.setCustomModelData(data);
                } else {
                    try {
                        Method setCMD = meta.getClass().getMethod("setCustomModelData", Integer.class);
                        setCMD.setAccessible(true);
                        setCMD.invoke(meta, data);
                    } catch (Exception e) {
                        InertiaLogger.warn("Failed to set custom-model-data via reflection: " + e.getMessage());
                    }
                }
            }
            // Вариант 2: Компонент (Section) - 1.21.4+
            else if (section.isConfigurationSection("custom-model-data")) {
                if (MinecraftVersions.TRICKY_TRIALS.isAtLeast()) {
                    applyCustomModelDataComponent(meta, section.getConfigurationSection("custom-model-data"));
                }
            }
        }

        // 5. Item Model (1.21.2 / 1.21.3+)
        if (section.contains("item-model")) {
            if (MinecraftVersions.TRICKY_TRIALS.isAtLeast() &&
                    MinecraftVersions.CURRENT.compareTo(MinecraftVersions.TRICKY_TRIALS.get(2)) >= 0) {
                try {
                    String modelKey = section.getString("item-model");
                    if (modelKey != null && modelKey.contains(":")) {
                        String[] parts = modelKey.split(":");
                        NamespacedKey key = new NamespacedKey(parts[0], parts[1]);

                        Method setItemModel = meta.getClass().getMethod("setItemModel", NamespacedKey.class);
                        setItemModel.setAccessible(true);
                        setItemModel.invoke(meta, key);
                    }
                } catch (Exception e) {
                    InertiaLogger.warn("Failed to set item model: " + e.getMessage());
                }
            }
        }

        // 6. Hide Flags
        if (section.getBoolean("hide-flags")) {
            meta.addItemFlags(ItemFlag.values());
        }

        // 7. Enchantment Glint Override (1.20.5+)
        if (section.contains("enchantment-glint-override")) {
            if (MinecraftVersions.TRAILS_AND_TAILS.isAtLeast() &&
                    MinecraftVersions.CURRENT.compareTo(MinecraftVersions.TRAILS_AND_TAILS.get(5)) >= 0) {
                try {
                    Method setGlint = meta.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class);
                    setGlint.setAccessible(true);
                    setGlint.invoke(meta, section.getBoolean("enchantment-glint-override"));
                } catch (Exception e) {
                    try {
                        Method setGlint = meta.getClass().getMethod("setEnchantmentGlintOverride", boolean.class);
                        setGlint.setAccessible(true);
                        setGlint.invoke(meta, section.getBoolean("enchantment-glint-override"));
                    } catch (Exception ex) {
                        InertiaLogger.warn("Failed to set enchantment-glint-override: " + ex.getMessage());
                    }
                }
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
            if (meta instanceof Damageable) {
                ((Damageable) meta).setDamage(section.getInt("durability.damage"));
            }
        }

        // Max Damage (1.20.5+)
        if (section.contains("durability.max-damage")) {
            if (MinecraftVersions.TRAILS_AND_TAILS.isAtLeast() &&
                    MinecraftVersions.CURRENT.compareTo(MinecraftVersions.TRAILS_AND_TAILS.get(5)) >= 0) {
                try {
                    Method setMaxDamage = meta.getClass().getMethod("setMaxDamage", Integer.class);
                    setMaxDamage.setAccessible(true);
                    setMaxDamage.invoke(meta, section.getInt("durability.max-damage"));
                } catch (Exception e) {
                    InertiaLogger.warn("Failed to set max-damage via reflection: " + e.getMessage());
                }
            }
        }

        // 10. Skull Owner
        if (meta instanceof SkullMeta && section.contains("skull-owning-player")) {
            String skullData = section.getString("skull-owning-player");
            if (skullData != null) {
                applySkullTexture((SkullMeta) meta, skullData);
            }
        }

        // 11. Colors
        if (section.contains("potion-color") && meta instanceof PotionMeta) {
            ((PotionMeta) meta).setColor(parseColorFromConfig(section.getString("potion-color")));
        }
        if (section.contains("leather-color") && meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(parseColorFromConfig(section.getString("leather-color")));
        }

        // 12. Armor Trims (1.20+)
        // FIX: Removed 'meta instanceof ArmorMeta' because ArmorMeta does not exist in 1.16.5
        if (MinecraftVersions.TRAILS_AND_TAILS.isAtLeast()) {
            if (section.contains("trim-pattern") && section.contains("trim-material")) {
                try {
                    // We pass generic ItemMeta, the check happens inside via reflection
                    applyArmorTrim(meta, section.getString("trim-pattern"), section.getString("trim-material"));
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
                    NamespacedKey key = new NamespacedKey("com/ladakx", parts[0]);
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

        // 14. Light Level
        if (section.contains("light-level") && material.name().equals("LIGHT")) {
            if (item.getItemMeta() instanceof BlockDataMeta) {
                BlockDataMeta blockDataMeta = (BlockDataMeta) item.getItemMeta();
                BlockData data = material.createBlockData();
                if (data instanceof Levelled) {
                    ((Levelled) data).setLevel(section.getInt("light-level"));
                    blockDataMeta.setBlockData(data);
                    item.setItemMeta(blockDataMeta);
                }
            }
        }

        return item;
    }

    // --- Helper Methods ---

    /**
     * Applies CustomModelDataComponent via Reflection (Paper 1.21.4+).
     */
    private static void applyCustomModelDataComponent(ItemMeta meta, ConfigurationSection section) {
        try {
            // 1. Get the component from the meta using the Interface method (ItemMeta)
            Method getComponentMethod = ItemMeta.class.getMethod("getCustomModelDataComponent");
            Object component = getComponentMethod.invoke(meta);

            if (component == null) return;

            // 2. Get the CustomModelDataComponent interface class to call methods on the component object
            Class<?> componentInterface = Class.forName("org.bukkit.inventory.meta.components.CustomModelDataComponent");

            // Set Floats
            if (section.contains("floats")) {
                List<Float> floats = section.getFloatList("floats");
                Method setFloats = componentInterface.getMethod("setFloats", List.class);
                setFloats.invoke(component, floats);
            }

            // Set Strings
            if (section.contains("strings")) {
                List<String> strings = section.getStringList("strings");
                Method setStrings = componentInterface.getMethod("setStrings", List.class);
                setStrings.invoke(component, strings);
            }

            // Set Flags
            if (section.contains("flags")) {
                List<Boolean> flags = section.getBooleanList("flags");
                Method setFlags = componentInterface.getMethod("setFlags", List.class);
                setFlags.invoke(component, flags);
            }

            // 3. Set the component back
            try {
                Method setComponentMethod = ItemMeta.class.getMethod("setCustomModelDataComponent", componentInterface);
                setComponentMethod.invoke(meta, component);
            } catch (NoSuchMethodException e) {
                InertiaLogger.debug("setCustomModelDataComponent not found, relying on mutable view (expected behavior on some versions).");
            }

        } catch (Exception e) {
            InertiaLogger.error("Failed to apply CustomModelDataComponent to item meta", e);
        }
    }

    private static void setDisplayNameSafe(ItemMeta meta, Component component) {
        try {
            Method displayNameMethod = meta.getClass().getMethod("displayName", Component.class);
            displayNameMethod.setAccessible(true);
            displayNameMethod.invoke(meta, component);
        } catch (Exception e) {
            String legacyName = LegacyComponentSerializer.legacySection().serialize(component);
            meta.setDisplayName(legacyName);
        }
    }

    private static void setLoreSafe(ItemMeta meta, List<Component> lore) {
        try {
            Method loreMethod = meta.getClass().getMethod("lore", List.class);
            loreMethod.setAccessible(true);
            loreMethod.invoke(meta, lore);
        } catch (Exception e) {
            List<String> legacyLore = new ArrayList<>();
            for (Component comp : lore) {
                legacyLore.add(LegacyComponentSerializer.legacySection().serialize(comp));
            }
            meta.setLore(legacyLore);
        }
    }

    public static Color parseColorFromConfig(String colorStr) {
        if (colorStr == null) return Color.WHITE;
        if (colorStr.contains(",")) {
            String[] rgb = colorStr.split(",");
            try {
                return Color.fromRGB(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
            } catch (Exception e) {
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
            return Enchantment.getByName(key.toUpperCase());
        }
        return enchantment;
    }

    public static void applySkullTexture(SkullMeta meta, String data) {
        if (data.length() <= 16 || (data.length() == 36 && data.contains("-"))) {
            try {
                UUID uuid = UUID.fromString(data);
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            } catch (IllegalArgumentException e) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(data));
            }
            return;
        }

        try {
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), null);
            String base64;
            if (data.startsWith("http")) {
                base64 = Base64.getEncoder().encodeToString(String.format("{textures:{SKIN:{url:\"%s\"}}}", data).getBytes());
            } else {
                base64 = data;
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
     * Applies Armor Trims via Reflection for 1.20+ compatibility while compiling on 1.16.5.
     * Changed parameter from ArmorMeta to ItemMeta to avoid ClassNotFoundException during compilation.
     */
    public static void applyArmorTrim(ItemMeta meta, String patternStr, String materialStr) throws Exception {
        // 1. Check if meta is actually an instance of ArmorMeta (which exists only at runtime on 1.20+)
        Class<?> armorMetaClass;
        try {
            armorMetaClass = Class.forName("org.bukkit.inventory.meta.ArmorMeta");
        } catch (ClassNotFoundException e) {
            return; // Not on 1.20+, cannot apply trims
        }

        if (!armorMetaClass.isInstance(meta)) {
            return; // Item is not armor
        }

        // Reflection magic
        Class<?> registryClass = Class.forName("org.bukkit.Registry");

        Field trimPatternRegistryField = registryClass.getField("TRIM_PATTERN");
        Field trimMaterialRegistryField = registryClass.getField("TRIM_MATERIAL");

        Object trimPatternRegistry = trimPatternRegistryField.get(null);
        Object trimMaterialRegistry = trimMaterialRegistryField.get(null);

        Method getMethod = registryClass.getMethod("get", NamespacedKey.class);
        getMethod.setAccessible(true);

        Object pattern = getMethod.invoke(trimPatternRegistry, NamespacedKey.minecraft(patternStr.toLowerCase()));
        Object material = getMethod.invoke(trimMaterialRegistry, NamespacedKey.minecraft(materialStr.toLowerCase()));

        if (pattern != null && material != null) {
            Class<?> armorTrimClass = Class.forName("org.bukkit.inventory.meta.trim.ArmorTrim");
            Class<?> trimMaterialClass = Class.forName("org.bukkit.inventory.meta.trim.TrimMaterial");
            Class<?> trimPatternClass = Class.forName("org.bukkit.inventory.meta.trim.TrimPattern");

            Constructor<?> armorTrimConstructor = armorTrimClass.getConstructor(trimMaterialClass, trimPatternClass);
            armorTrimConstructor.setAccessible(true);
            Object armorTrim = armorTrimConstructor.newInstance(material, pattern);

            Method setTrimMethod = meta.getClass().getMethod("setTrim", armorTrimClass);
            setTrimMethod.setAccessible(true);
            setTrimMethod.invoke(meta, armorTrim);
        }
    }
}