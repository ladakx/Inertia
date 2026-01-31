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

    /**
     * Applies standard Inertia physics tags to a visual entity.
     */
    public static void applyInertiaTags(com.ladakx.inertia.rendering.VisualEntity visual,
                                        String bodyId,
                                        java.util.UUID bodyUuid,
                                        String renderModelId,
                                        String renderEntityKey) {
        if (visual == null || !visual.isValid()) return;

        var pdc = visual.getPersistentDataContainer();

        // Body ID
        pdc.set(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ID,
                PersistentDataType.STRING, bodyId);

        // Body UUID
        pdc.set(InertiaPDCKeys.INERTIA_PHYSICS_BODY_UUID,
                PersistentDataType.STRING, bodyUuid.toString());

        // Active State (Default true on spawn)
        pdc.set(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ACTIVE,
                PersistentDataType.STRING, "true");

        // Render Model ID (e.g. "chains.heavy_iron_chain")
        if (renderModelId != null) {
            pdc.set(InertiaPDCKeys.INERTIA_RENDER_MODEL_ID,
                    PersistentDataType.STRING, renderModelId);
        }

        // Entity Key inside the model (e.g. "display_1")
        if (renderEntityKey != null) {
            pdc.set(InertiaPDCKeys.INERTIA_RENDER_MODEL_ENTITY_ID,
                    PersistentDataType.STRING, renderEntityKey);
        }
    }
}