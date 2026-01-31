package com.ladakx.inertia.features.tools.impl;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import com.ladakx.inertia.common.PhysicsGraphUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.features.tools.Tool;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.body.impl.DisplayedPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class StaticTool extends Tool {

    private final PhysicsWorldRegistry physicsWorldRegistry;

    public StaticTool(ConfigurationService configurationService, PhysicsWorldRegistry physicsWorldRegistry) {
        super("static_tool", configurationService);
        this.physicsWorldRegistry = physicsWorldRegistry;
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

        List<PhysicsWorld.RaycastResult> results = space.raycastEntity(player.getEyeLocation(), player.getLocation().getDirection(), 10);
        if (results.isEmpty()) return;

        PhysicsWorld.RaycastResult result = results.get(0);
        Long va = result.va();
        if (va == null) return;

        AbstractPhysicsBody hitBody = space.getObjectByVa(va);
        if (hitBody == null) return;

        // REFACTOR: Использование PhysicsGraphUtils вместо дублирования кода
        Set<AbstractPhysicsBody> cluster = PhysicsGraphUtils.collectConnectedBodies(space, hitBody);
        java.util.UUID clusterId = java.util.UUID.randomUUID();
        int frozenCount = 0;

        for (AbstractPhysicsBody body : cluster) {
            if (body instanceof DisplayedPhysicsBody displayedBody) {
                try {
                    displayedBody.freeze(clusterId);
                    frozenCount++;
                } catch (Exception e) {
                    InertiaLogger.error("Failed to freeze body part via StaticTool", e);
                }
            } else {
                body.destroy();
            }
        }

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