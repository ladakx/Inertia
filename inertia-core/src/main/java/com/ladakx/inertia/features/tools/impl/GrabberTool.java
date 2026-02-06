package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.api.service.PhysicsManipulationService;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.features.tools.data.ToolDataManager;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.body.impl.DisplayedPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.features.tools.Tool;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GrabberTool extends Tool {

    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final PhysicsManipulationService manipulationService;

    // Session state per player
    private static class GrabSession {
        UUID taskId;
        AbstractPhysicsBody body;
        double distance;
    }

    private final Map<UUID, GrabSession> sessions = new HashMap<>();

    public GrabberTool(ConfigurationService configurationService,
                       PhysicsWorldRegistry physicsWorldRegistry,
                       PhysicsManipulationService manipulationService,
                       ToolDataManager toolDataManager) {
        super("grabber", configurationService, toolDataManager);
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.manipulationService = manipulationService;
    }

    @Override
    public boolean onHotbarChange(PlayerItemHeldEvent event, int diff) {
        GrabSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return false;

        session.distance += diff * 1.5;
        if (session.distance < 1) session.distance = 1;

        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 0.5f, 2.0f);
        return true;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return;

        GrabSession session = sessions.get(player.getUniqueId());

        // Release if holding
        if (session != null) {
            release(player, session, space);
            return;
        }

        // Try grab
        var eye = player.getEyeLocation();
        List<PhysicsWorld.RaycastResult> results = space.raycastEntity(eye, eye.getDirection(), 16);
        if (results.isEmpty()) return;

        AbstractPhysicsBody hitBody = space.getObjectByVa(results.get(0).va());
        if (hitBody == null) return;

        GrabSession newSession = new GrabSession();
        newSession.body = hitBody;
        newSession.distance = player.getLocation().distance(hitBody.getLocation());

        newSession.taskId = manipulationService.startGrabbing(
                space,
                hitBody,
                player,
                10.0,
                () -> sessions.get(player.getUniqueId()) != null ? sessions.get(player.getUniqueId()).distance : 5.0
        );

        sessions.put(player.getUniqueId(), newSession);

        if (hitBody instanceof DisplayedPhysicsBody displayed) {
            if (displayed.getDisplay() != null) displayed.getDisplay().setGlowing(true);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, SoundCategory.MASTER, 0.5f, 1.8f);
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GrabSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return;

        // Create static joint
        manipulationService.createStaticJoint(space, session.body, session.body.getLocation());

        // Visual effects
        player.spawnParticle(Particle.REVERSE_PORTAL, session.body.getLocation(), 20);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, SoundCategory.MASTER, 0.5F, 1.8F);
        player.playSound(player.getLocation(), Sound.ENTITY_BEE_STING, SoundCategory.MASTER, 0.5F, 2.0F);

        release(player, session, space);
    }

    private void release(Player player, GrabSession session, PhysicsWorld space) {
        manipulationService.stopGrabbing(space, session.taskId);
        if (session.body instanceof DisplayedPhysicsBody displayed) {
            if (displayed.getDisplay() != null) displayed.getDisplay().setGlowing(false);
        }
        sessions.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_HIT, SoundCategory.MASTER, 0.5f, 1.8f);
    }

    public void release(Player player) {
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        GrabSession session = sessions.get(player.getUniqueId());
        if (session != null && space != null) {
            release(player, session, space);
        } else {
            sessions.remove(player.getUniqueId());
        }
    }

    @Override
    public void onSwapHands(Player player) {}

    @Override
    protected ItemStack getBaseItem() {
        ItemStack stack = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            MessageManager msg = configurationService.getMessageManager();
            meta.displayName(msg.getSingle(MessageKey.TOOL_GRABBER_NAME));
            meta.lore(msg.get(MessageKey.TOOL_GRABBER_LORE));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
