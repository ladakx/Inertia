package com.ladakx.inertia.tools.impl;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.tools.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Інструмент для спавну Ragdoll-структур.
 */
public class RagdollTool extends Tool {

    public static final NamespacedKey BODY_ID_KEY = new NamespacedKey(InertiaPlugin.getInstance(), "ragdoll_body_id");

    public RagdollTool() {
        super("ragdoll_tool");
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
    }

    @Override
    public void onSwapHands(Player player) {
    }

    public ItemStack getToolItem(String bodyId) {
        ItemStack item = getBaseItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(BODY_ID_KEY, PersistentDataType.STRING, bodyId);
            meta.displayName(Component.text("Ragdoll Tool: ", NamedTextColor.GOLD)
                    .append(Component.text(bodyId, NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                    Component.text("R-Click: Spawn Ragdoll", NamedTextColor.GRAY)
            ));
            item.setItemMeta(meta);
        }
        return super.markItemAsTool(item);
    }

    private String getBodyIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(BODY_ID_KEY, PersistentDataType.STRING);
    }

    @Override
    protected ItemStack getBaseItem() {
        return new ItemStack(Material.ARMOR_STAND);
    }
}