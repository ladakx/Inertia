package com.ladakx.inertia.common.pdc;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class InertiaPDCUtils {

    private InertiaPDCUtils() {}

    public static void setString(Plugin plugin, ItemStack item, String key, String value) {
        if (item == null) return;

        // Отримуємо ItemMeta. Якщо її немає, Bukkit створить нову порожню.
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return; // Це може статися тільки для Material.AIR

        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    public static String getString(Plugin plugin, ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, key), PersistentDataType.STRING);
    }

    public static boolean hasKey(Plugin plugin, ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, key), PersistentDataType.STRING);
    }

    // Packet-based рендеринг не використовує Bukkit PersistentDataContainer для entity.
}
