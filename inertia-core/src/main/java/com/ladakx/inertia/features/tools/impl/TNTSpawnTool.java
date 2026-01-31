package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.common.utils.StringUtils;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.features.tools.Tool;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

import static com.ladakx.inertia.common.pdc.InertiaPDCUtils.getString;
import static com.ladakx.inertia.common.pdc.InertiaPDCUtils.setString;

public class TNTSpawnTool extends Tool {

    private static final String KEY_BODY = "tnt_body";
    private static final String KEY_FORCE = "tnt_force";

    private final BodyFactory bodyFactory;
    private final PhysicsWorldRegistry physicsWorldRegistry;

    public TNTSpawnTool(ConfigurationService configurationService,
                        PhysicsWorldRegistry physicsWorldRegistry,
                        BodyFactory bodyFactory) {
        super("tnt_spawner", configurationService);
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.bodyFactory = bodyFactory;
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        spawnTNT(event.getPlayer(), event.getItem(), true);
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        spawnTNT(event.getPlayer(), event.getItem(), false);
    }

    @Override
    public void onSwapHands(Player player) {}

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
            bodyFactory.spawnTNT(spawnLoc, bodyId, force, velocity);
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
        MessageManager msg = configurationService.getMessageManager();

        // Localized Name
        meta.displayName(msg.getSingle(MessageKey.TOOL_TNT_NAME));

        // Localized Lore
        List<Component> lore = new ArrayList<>();
        for (Component line : msg.get(MessageKey.TOOL_TNT_LORE)) {
            lore.add(StringUtils.replace(line,
                    "{body}", bodyId,
                    "{force}", String.format("%.1f", force)
            ));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    @Override
    protected ItemStack getBaseItem() {
        return new ItemStack(Material.TNT);
    }
}