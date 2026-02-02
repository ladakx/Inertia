package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.api.service.PhysicsManipulationService;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.features.tools.Tool;
import com.ladakx.inertia.features.tools.data.ToolDataManager;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class StaticTool extends Tool {

    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final PhysicsManipulationService manipulationService;

    public StaticTool(ConfigurationService configurationService,
                      PhysicsWorldRegistry physicsWorldRegistry,
                      PhysicsManipulationService manipulationService,
                      ToolDataManager toolDataManager) {
        super("static_tool", configurationService, toolDataManager);
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.manipulationService = manipulationService;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        applyFreeze(event.getPlayer());
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        applyFreeze(event.getPlayer());
    }

    @Override
    public void onSwapHands(Player player) {
    }

    private void applyFreeze(Player player) {
        if (!validateWorld(player)) return;
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return;

        java.util.List<PhysicsWorld.RaycastResult> results = space.raycastEntity(player.getEyeLocation(), player.getLocation().getDirection(), 10);
        if (results.isEmpty()) return;

        AbstractPhysicsBody hitBody = space.getObjectByVa(results.get(0).va());
        if (hitBody == null) return;

        int frozenCount = manipulationService.freezeCluster(space, hitBody);

        if (frozenCount > 0) {
            send(player, MessageKey.BODY_FROZEN);
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, SoundCategory.MASTER, 1.0f, 2.0f);
        } else {
            send(player, MessageKey.CANNOT_FREEZE_BODY);
        }
    }

    @Override
    protected ItemStack getBaseItem() {
        ItemStack item = new ItemStack(Material.PACKED_ICE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            MessageManager msg = configurationService.getMessageManager();
            meta.displayName(msg.getSingle(MessageKey.TOOL_STATIC_NAME));
            meta.lore(msg.get(MessageKey.TOOL_STATIC_LORE));
            item.setItemMeta(meta);
        }
        return item;
    }
}