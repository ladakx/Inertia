package com.ladakx.inertia.features.listeners;

import com.ladakx.inertia.api.events.PhysicsBodyInteractAction;
import com.ladakx.inertia.api.events.PhysicsBodyInteractEvent;
import com.ladakx.inertia.api.events.PhysicsBodyInteractPayload;
import com.ladakx.inertia.api.interaction.RaycastHit;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class PhysicsBodyInteractListener implements Listener {

    private static final double INTERACTION_DISTANCE = 3.5D;

    private final PhysicsWorldRegistry physicsWorldRegistry;

    public PhysicsBodyInteractListener(@NotNull PhysicsWorldRegistry physicsWorldRegistry) {
        this.physicsWorldRegistry = Objects.requireNonNull(physicsWorldRegistry, "physicsWorldRegistry");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        PhysicsBodyInteractAction interactAction = toInteractAction(event.getAction());
        if (interactAction == null) {
            return;
        }

        Player player = event.getPlayer();
        PhysicsWorld physicsWorld = physicsWorldRegistry.getWorld(player.getWorld());
        if (physicsWorld == null) {
            return;
        }

        RaycastHit hit = physicsWorld.getInteraction().raycast(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                INTERACTION_DISTANCE
        );
        if (hit == null) {
            return;
        }

        PhysicsBodyInteractPayload payload = new PhysicsBodyInteractPayload(
                1,
                player.getUniqueId(),
                player.getName(),
                player.getWorld().getUID(),
                player.getWorld().getName(),
                interactAction,
                hit.body(),
                hit.point().clone(),
                hit.fraction()
        );

        PhysicsBodyInteractEvent interactEvent = new PhysicsBodyInteractEvent(payload);
        Bukkit.getPluginManager().callEvent(interactEvent);
        if (interactEvent.isCancelled()) {
            event.setCancelled(true);
        }
    }

    private static @Nullable PhysicsBodyInteractAction toInteractAction(@NotNull Action action) {
        return switch (action) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> PhysicsBodyInteractAction.LEFT_CLICK;
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> PhysicsBodyInteractAction.RIGHT_CLICK;
            default -> null;
        };
    }
}
