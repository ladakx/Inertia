package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.api.service.PhysicsManipulationService;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.features.tools.NetworkInteractTool;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WeldTool extends Tool implements NetworkInteractTool {

    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final PhysicsManipulationService manipulationService;

    private static final class WeldSession {
        @Nullable AbstractPhysicsBody firstObject;
        boolean keepDistance;
    }

    private final Map<UUID, WeldSession> sessions = new HashMap<>();

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
        WeldSession session = sessions.computeIfAbsent(player.getUniqueId(), k -> new WeldSession());
        session.keepDistance = !session.keepDistance;
        send(player, MessageKey.WELD_MODE_CHANGE, "{mode}", (session.keepDistance ? "Keep Distance" : "Snap Center"));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        WeldSession session = sessions.get(player.getUniqueId());
        if (session != null && session.firstObject != null) {
            session.firstObject = null;
            send(player, MessageKey.WELD_DESELECTED);
            player.playSound(event.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.5f);
        }
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PhysicsWorld space = physicsWorldRegistry.getWorld(player.getWorld());
        if (space == null) return;

        WeldSession session = sessions.computeIfAbsent(player.getUniqueId(), k -> new WeldSession());

        var eye = player.getEyeLocation();
        List<PhysicsWorld.RaycastResult> results = space.raycastEntity(eye, eye.getDirection(), 16);
        if (results.isEmpty()) return;

        AbstractPhysicsBody hitBody = space.getObjectByVa(results.get(0).va());
        if (hitBody == null) return;

        boolean unweld = player.isSneaking();

        if (session.firstObject != null) {
            if (session.firstObject == hitBody) return; // Cannot weld to self

            if (unweld) {
                int removed = manipulationService.unweldBodies(space, session.firstObject, hitBody);
                send(player, removed > 0 ? MessageKey.WELD_DISCONNECTED : MessageKey.WELD_NOT_CONNECTED);
            } else {
                manipulationService.weldBodies(space, session.firstObject, hitBody, session.keepDistance);
                send(player, MessageKey.WELD_CONNECTED);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.5f, 2.0f);
            }

            session.firstObject = null;
            return;
        }

        if (unweld) {
            int removed = manipulationService.unweldAll(space, hitBody);
            send(player, removed > 0 ? MessageKey.WELD_DISCONNECTED : MessageKey.WELD_NOT_CONNECTED);
            return;
        }

        session.firstObject = hitBody;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.5f, 1.5f);
        send(player, MessageKey.WELD_FIRST_SELECTED);
    }

    @Override
    public void onNetworkInteract(Player player, AbstractPhysicsBody body, boolean attack) {
        WeldSession session = sessions.computeIfAbsent(player.getUniqueId(), k -> new WeldSession());
        if (attack) {
            if (session.firstObject != null) {
                session.firstObject = null;
                send(player, MessageKey.WELD_DESELECTED);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.5f);
            }
            return;
        }

        PhysicsWorld space = physicsWorldRegistry.getWorld(player.getWorld());
        if (space == null) return;

        boolean unweld = player.isSneaking();

        if (session.firstObject != null) {
            if (session.firstObject == body) return;

            if (unweld) {
                int removed = manipulationService.unweldBodies(space, session.firstObject, body);
                send(player, removed > 0 ? MessageKey.WELD_DISCONNECTED : MessageKey.WELD_NOT_CONNECTED);
            } else {
                manipulationService.weldBodies(space, session.firstObject, body, session.keepDistance);
                send(player, MessageKey.WELD_CONNECTED);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.5f, 2.0f);
            }
            session.firstObject = null;
            return;
        }

        if (unweld) {
            int removed = manipulationService.unweldAll(space, body);
            send(player, removed > 0 ? MessageKey.WELD_DISCONNECTED : MessageKey.WELD_NOT_CONNECTED);
            return;
        }

        session.firstObject = body;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.5f, 1.5f);
        send(player, MessageKey.WELD_FIRST_SELECTED);
    }

    public void reset(Player player) {
        if (player == null) return;
        sessions.remove(player.getUniqueId());
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
