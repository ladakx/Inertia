package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.common.utils.StringUtils;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.features.tools.data.ToolDataManager;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.physics.world.PhysicsWorld;
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

public class TNTSpawnTool extends Tool {
    private final BodyFactory bodyFactory;
    private final PhysicsWorldRegistry physicsWorldRegistry;

    public TNTSpawnTool(ConfigurationService configurationService,
                        PhysicsWorldRegistry physicsWorldRegistry,
                        BodyFactory bodyFactory,
                        ToolDataManager toolDataManager) {
        super("tnt_spawner", configurationService, toolDataManager);
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
        PhysicsWorld space = physicsWorldRegistry.getWorld(player.getWorld());
        if (space == null) return;

        if (!space.canSpawnBodies(1)) {
            send(player, MessageKey.SPAWN_LIMIT_REACHED, "{limit}", String.valueOf(space.getSettings().performance().maxBodies()));
            return;
        }

        String bodyId = toolDataManager.getBodyId(item);
        float force = toolDataManager.getTntForce(item, -1f);

        if (bodyId == null || force < 0) {
            send(player, MessageKey.TOOL_BROKEN_NBT);
            return;
        }

        Location spawnLoc;
        Vector velocity = null;
        if (isThrow) {
            spawnLoc = player.getEyeLocation();
            velocity = player.getLocation().getDirection().multiply(15.0);
        } else {
            spawnLoc = player.getLocation().add(0, 0.5, 0);
        }

        try {
            bodyFactory.spawnTNT(spawnLoc, bodyId, force, velocity);
            player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
            if (isThrow) {
                player.swingMainHand();
            }
        } catch (Exception e) {
            send(player, MessageKey.ERROR_OCCURRED, "{error}", e.getMessage());
        }
    }

    public ItemStack getToolItem(String bodyId, float force) {
        ItemStack item = getBaseItem();
        item = markItemAsTool(item);

        toolDataManager.setBodyId(item, bodyId);
        toolDataManager.setTntForce(item, force);

        ItemMeta meta = item.getItemMeta();
        MessageManager msg = configurationService.getMessageManager();
        meta.displayName(msg.getSingle(MessageKey.TOOL_TNT_NAME));
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
