package com.ladakx.inertia.tools;

import com.ladakx.inertia.InertiaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public abstract class Tool {

    public static final NamespacedKey TOOL_KEY = new NamespacedKey(InertiaPlugin.getInstance(), "inertia_tool_id");
    private final String toolId;

    public Tool(String toolId) {
        this.toolId = toolId;
    }

    public abstract void onRightClick(PlayerInteractEvent event);
    public abstract void onLeftClick(PlayerInteractEvent event);
    public abstract void onSwapHands(Player player);

    protected abstract ItemStack getBaseItem();

    /**
     * Базовий метод отримання інструмента (без специфічних даних).
     */
    public ItemStack buildItem() {
        return markItemAsTool(getBaseItem());
    }

    /**
     * Додає NBT тег інструмента до ItemStack.
     */
    protected ItemStack markItemAsTool(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(TOOL_KEY, PersistentDataType.STRING, toolId);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public String getId() {
        return toolId;
    }

    public boolean isTool(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        String id = stack.getItemMeta().getPersistentDataContainer().get(TOOL_KEY, PersistentDataType.STRING);
        return toolId.equals(id);
    }
}