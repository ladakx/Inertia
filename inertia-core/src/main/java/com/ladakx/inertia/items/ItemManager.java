package com.ladakx.inertia.items;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.config.ConfigManager;
import com.ladakx.inertia.utils.serializers.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ItemManager {

    // Прибираємо static instance

    private final Map<String, ItemStack> items = new HashMap<>();
    private final ConfigManager configManager;

    // DI Constructor
    public ItemManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Перезавантажує предмети.
     * ВАЖЛИВО: Цей метод повинен викликатися в ГОЛОВНОМУ потоці (Main Thread),
     * оскільки створення ItemStack та ItemMeta не є потокобезпечним в Bukkit API.
     * Саме читання файлу вже виконано асинхронно в ConfigManager.reloadAsync().
     */
    public void reload() {
        items.clear();

        // Отримуємо конфіг через DI (він уже завантажений в пам'ять)
        FileConfiguration config = configManager.getItemsFile().getConfig();
        if (config == null) return;

        Set<String> keys = config.getKeys(false);
        if (keys.isEmpty()) {
            InertiaLogger.warn("items.yml is empty!");
            return;
        }

        int count = 0;
        for (String key : keys) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section != null) {
                try {
                    // ItemSerializer використовує Bukkit API, тому це має бути Sync
                    ItemStack item = ItemSerializer.deserialize(section);
                    items.put(key, item);
                    count++;
                } catch (Exception e) {
                    InertiaLogger.error("Error loading item " + key, e);
                }
            }
        }
        InertiaLogger.info("Loaded " + count + " items.");
    }

    // --- API ---

    @Nullable
    public ItemStack getItem(@NotNull String id) {
        if (id.startsWith("item.")) id = id.substring(5);
        else if (id.startsWith("items.")) id = id.substring(6);

        if (!items.containsKey(id)) {
            // Fallback
            Material mat = Material.matchMaterial(id);
            if (mat != null) {
                return new ItemStack(mat);
            }
            return new ItemStack(Material.BARRIER);
        }

        return items.get(id).clone();
    }

    @NotNull
    public ItemStack getItemOrDefault(@NotNull String id, @NotNull ItemStack fallback) {
        ItemStack item = getItem(id);
        // getItem повертає BARRIER якщо не знайдено, тому перевірка трохи складніша,
        // але для спрощення залишимо так або змінимо логіку getItem
        return (item != null && item.getType() != Material.BARRIER) ? item : fallback;
    }

    public boolean hasItem(@NotNull String id) {
        if (id.startsWith("item.")) id = id.substring(5);
        else if (id.startsWith("items.")) id = id.substring(6);
        return items.containsKey(id);
    }

    public Set<String> getItemIds() {
        return Collections.unmodifiableSet(items.keySet());
    }
}