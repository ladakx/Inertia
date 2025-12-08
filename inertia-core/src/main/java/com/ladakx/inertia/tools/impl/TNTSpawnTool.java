package com.ladakx.inertia.tools.impl;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.files.config.message.MessageKey;
import com.ladakx.inertia.physics.service.PhysicsSpawnService;
import com.ladakx.inertia.tools.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.List;

import static com.ladakx.inertia.utils.PDCUtils.getString;
import static com.ladakx.inertia.utils.PDCUtils.setString;

public class TNTSpawnTool extends Tool {

    private static final String KEY_BODY = "tnt_body";
    private static final String KEY_FORCE = "tnt_force";

    private final PhysicsSpawnService spawnService;

    public TNTSpawnTool() {
        super("tnt_spawner");
        this.spawnService = new PhysicsSpawnService(InertiaPlugin.getInstance());
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        // L-Click: Throw TNT
        spawnTNT(event.getPlayer(), event.getItem(), true);
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        // R-Click: Place TNT at feet
        spawnTNT(event.getPlayer(), event.getItem(), false);
    }

    @Override
    public void onSwapHands(Player player) {
        // No action
    }

    private void spawnTNT(Player player, ItemStack item, boolean isThrow) {
        if (!validateWorld(player)) return;

        String bodyId = getString(InertiaPlugin.getInstance(), item, KEY_BODY);
        String forceStr = getString(InertiaPlugin.getInstance(), item, KEY_FORCE);

        if (bodyId == null || forceStr == null) {
            send(player, MessageKey.TOOL_BROKEN_NBT);
            return;
        }

        float force;
        try {
            force = Float.parseFloat(forceStr);
        } catch (NumberFormatException e) {
            send(player, MessageKey.TOOL_INVALID_PARAMS);
            return;
        }

        Location spawnLoc;
        Vector velocity = null;

        if (isThrow) {
            spawnLoc = player.getEyeLocation();
            // Calculate throw vector
            velocity = player.getLocation().getDirection().multiply(15.0); // Adjust speed as needed
        } else {
            // Spawn at feet, slight offset up
            spawnLoc = player.getLocation().add(0, 0.5, 0);
        }

        try {
            spawnService.spawnTNT(spawnLoc, bodyId, force, velocity);
            player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
            
            if (isThrow) {
                player.swingMainHand();
            }
        } catch (Exception e) {
            // Handled via InertiaLogger in service usually, but good to catch strictly here too
            // InertiaLogger.error("Failed to spawn TNT", e); // Provided guidelines say don't printTrace manually if Service handles it, but Service methods throw exceptions in current code.
            // Using existing pattern:
            send(player, MessageKey.ERROR_OCCURRED, "{error}", e.getMessage());
        }
    }

    public ItemStack getToolItem(String bodyId, float force) {
        ItemStack item = getBaseItem();
        item = markItemAsTool(item);

        setString(InertiaPlugin.getInstance(), item, KEY_BODY, bodyId);
        setString(InertiaPlugin.getInstance(), item, KEY_FORCE, String.valueOf(force));

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("TNT Spawner", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        
        meta.lore(List.of(
                Component.text("Body: ", NamedTextColor.GRAY).append(Component.text(bodyId, NamedTextColor.WHITE)),
                Component.text("Force: ", NamedTextColor.GRAY).append(Component.text(String.format("%.1f", force), NamedTextColor.YELLOW)),
                Component.empty(),
                Component.text("L-Click: Throw TNT", NamedTextColor.GOLD),
                Component.text("R-Click: Place at feet", NamedTextColor.GOLD)
        ));
        
        item.setItemMeta(meta);
        return item;
    }

    @Override
    protected ItemStack getBaseItem() {
        return new ItemStack(Material.TNT);
    }
}