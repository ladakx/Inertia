package com.ladakx.inertia.features.tools.impl;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.features.tools.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tool that removes physics objects from the world.
 * <p>
 * When used on a body that belongs to a compound object (chain, ragdoll),
 * it will remove the entire connected cluster of physics objects linked by
 * Jolt constraints.
 */
public class DeleteTool extends Tool {

    private final PhysicsWorldRegistry physicsWorldRegistry;

    public DeleteTool(ConfigurationService configurationService, PhysicsWorldRegistry physicsWorldRegistry) {
        super("remover", configurationService);
        this.physicsWorldRegistry = physicsWorldRegistry;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) {
            return;
        }

        List<PhysicsWorld.RaycastResult> results =
                space.raycastEntity(player.getEyeLocation(), player.getLocation().getDirection(), 16);
        if (results.isEmpty()) {
            return;
        }

        PhysicsWorld.RaycastResult result = results.get(0);
        Long va = result.va();
        if (va == null) {
            return;
        }

        AbstractPhysicsBody root = space.getObjectByVa(va);
        if (root == null) {
            return;
        }

        destroyConnectedObjects(space, root);

        player.playSound(
                player.getLocation(),
                Sound.BLOCK_STONE_BREAK,
                SoundCategory.MASTER,
                0.5F,
                0.6F
        );
        send(player, MessageKey.REMOVER_USED);
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
    }

    @Override
    public void onSwapHands(Player player) {
    }

    @Override
    protected ItemStack getBaseItem() {
        ItemStack item = new ItemStack(Material.TNT_MINECART);
        ItemMeta meta = item.getItemMeta();

        MessageManager msg = configurationService.getMessageManager();
        meta.displayName(msg.getSingle(MessageKey.TOOL_REMOVER_NAME));
        meta.lore(msg.get(MessageKey.TOOL_REMOVER_LORE));

        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Destroy the entire cluster of physics objects that are connected to
     * the specified root object via Jolt two-body constraints.
     *
     * @param space owning physics space
     * @param root  starting physics object
     */
    private void destroyConnectedObjects(PhysicsWorld space, AbstractPhysicsBody root) {
        Set<AbstractPhysicsBody> visited = new HashSet<>();
        Deque<AbstractPhysicsBody> queue = new ArrayDeque<>();

        visited.add(root);
        queue.add(root);

        while (!queue.isEmpty()) {
            AbstractPhysicsBody current = queue.poll();
            if (current == null) {
                continue;
            }

            List<TwoBodyConstraintRef> refs = current.getConstraintSnapshot();
            for (TwoBodyConstraintRef ref : refs) {
                if (ref == null) {
                    continue;
                }

                TwoBodyConstraint constraint;
                try {
                    constraint = ref.getPtr();
                } catch (Exception e) {
                    InertiaLogger.warn("Failed to access Jolt constraint from reference: " + e);
                    continue;
                }
                if (constraint == null) {
                    continue;
                }

                enqueueOwner(space, constraint.getBody1(), visited, queue);
                enqueueOwner(space, constraint.getBody2(), visited, queue);
            }
        }

        for (AbstractPhysicsBody object : visited) {
            try {
                object.destroy();
            } catch (Exception e) {
                InertiaLogger.error(
                        "Failed to destroy physics object via DeleteTool: " + e
                );
            }
        }
    }

    /**
     * Helper that resolves the owner of a Jolt body and enqueues it for
     * breadth-first search if it has not been visited yet.
     *
     * @param space   owning physics space
     * @param body    Jolt body to resolve
     * @param visited set of already visited physics objects
     * @param queue   BFS queue
     */
    private void enqueueOwner(PhysicsWorld space,
                              Body body,
                              Set<AbstractPhysicsBody> visited,
                              Deque<AbstractPhysicsBody> queue) {
        if (body == null) {
            return;
        }
        AbstractPhysicsBody owner = space.getObjectByVa(body.va());
        if (owner != null && !visited.contains(owner)) {
            visited.add(owner);
            queue.add(owner);
        }
    }
}