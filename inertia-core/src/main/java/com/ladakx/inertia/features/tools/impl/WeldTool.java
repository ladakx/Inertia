package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.api.service.PhysicsManipulationService;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.features.tools.data.ToolDataManager;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.features.tools.Tool;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WeldTool extends Tool {

    private @Nullable AbstractPhysicsBody firstObject = null;
    private boolean keepDistance = false;
    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final PhysicsManipulationService manipulationService;

    public WeldTool(ConfigurationService configurationService,
                    PhysicsWorldRegistry physicsWorldRegistry,
                    PhysicsManipulationService manipulationService,
                    ToolDataManager toolDataManager) {
        super("welder", configurationService, toolDataManager);
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.manipulationService = manipulationService;
    }

    @Override
    public void onSwapHands(Player player) {
        keepDistance = !keepDistance;
        send(player, MessageKey.WELD_MODE_CHANGE, "{mode}", (keepDistance ? "Keep Distance" : "Snap Center"));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (firstObject != null) {
            firstObject = null;
            send(player, MessageKey.WELD_DESELECTED);
            player.playSound(event.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.5f);
        }
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return;

        List<PhysicsWorld.RaycastResult> results = space.raycastEntity(player.getEyeLocation(), player.getLocation().getDirection(), 16);
        if (results.isEmpty()) return;

        AbstractPhysicsBody hitBody = space.getObjectByVa(results.get(0).va());
        if (hitBody == null) return;

        if (firstObject != null) {
            if (firstObject == hitBody) return; // Cannot weld to self

            manipulationService.weldBodies(space, firstObject, hitBody, keepDistance);

            firstObject = null;
            send(player, MessageKey.WELD_CONNECTED);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.5f, 2.0f);
            return;
        }

        firstObject = hitBody;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.5f, 1.5f);
        send(player, MessageKey.WELD_FIRST_SELECTED);
    }

    @Override
    protected ItemStack getBaseItem() {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        MessageManager msg = configurationService.getMessageManager();
        meta.displayName(msg.getSingle(MessageKey.TOOL_WELDER_NAME));
        meta.lore(msg.get(MessageKey.TOOL_WELDER_LORE));
        item.setItemMeta(meta);
        return item;
    }
}