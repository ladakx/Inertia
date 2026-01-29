package com.ladakx.inertia.items;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.files.ItemsFile;
import com.ladakx.inertia.config.ConfigManager;
import com.ladakx.inertia.utils.serializers.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ItemManager {

    private static ItemManager instance;

    // Data
    private final ItemsFile itemsFile;
    private final Map<String, ItemStack> items = new HashMap<>();

    private ItemManager() {
        this.itemsFile = ConfigManager.getInstance().getItemsFile();
        reload(); // Завантажуємо одразу при створенні
    }

    public static void init() {
        if (instance == null) {
            instance = new ItemManager();
        }
    }

    public void reload() {
        // 1. Перезавантажуємо сам файл з диска
        itemsFile.reload();

        // 2. Очищаємо кеш
        items.clear();

        // 3. Парсимо дані з файлу
        var config = itemsFile.getConfig();
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
                    ItemStack item = ItemSerializer.deserialize(section);
                    items.put(key, item);
                    count++;
                } catch (Exception e) {
                    InertiaLogger.error("Error loading item " + key + ": " + e.getMessage());
                }
            }
        }
        InertiaLogger.info("Loaded " + count + " items.");
    }

    // --- API ---

    public static ItemManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ItemManager not initialized! Call init() first.");
        }
        return instance;
    }

    @Nullable
    public ItemStack getItem(@NotNull String id) {
        if (id.startsWith("item.")) {
            id = id.substring(5);
        }

        else if (id.startsWith("items.")) {
            id = id.substring(6);
        }

        ItemStack item = items.get(id).clone();
        if (!items.containsKey(id)) {
            if (Material.matchMaterial(id) != null) {
                item = new ItemStack(Material.matchMaterial(id));
            } else {
                item = new ItemStack(Material.BARRIER);
            }
        }

        return item;
    }

    @NotNull
    public ItemStack getItemOrDefault(@NotNull String id, @NotNull ItemStack fallback) {
        ItemStack item = getItem(id);
        return item != null ? item : fallback;
    }

    public boolean hasItem(@NotNull String id) {
        if (id.startsWith("item.")) {
            id = id.substring(5);
        }

        else if (id.startsWith("items.")) {
            id = id.substring(6);
        }

        return items.containsKey(id);
    }

    public Set<String> getItemIds() {
        return Collections.unmodifiableSet(items.keySet());
    }
}