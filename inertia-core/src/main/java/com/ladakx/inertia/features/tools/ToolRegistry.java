package com.ladakx.inertia.features.tools;

import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.features.tools.impl.*;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ToolRegistry implements Listener {

    private final Map<String, Tool> tools = new HashMap<>();
    private final ConfigurationService configurationService;
    private final PhysicsWorldRegistry physicsWorldRegistry;

    public ToolRegistry(InertiaPlugin plugin,
                        ConfigurationService configurationService,
                        PhysicsWorldRegistry physicsWorldRegistry,
                        JShapeFactory shapeFactory,
                        BodyFactory bodyFactory) {
        this.configurationService = configurationService;
        this.physicsWorldRegistry = physicsWorldRegistry;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        register(new DeleteTool(configurationService, physicsWorldRegistry));
        register(new WeldTool(configurationService, physicsWorldRegistry));
        register(new GrabberTool(configurationService, physicsWorldRegistry));
        register(new StaticTool(configurationService, physicsWorldRegistry));

        // Pass bodyFactory to ChainTool
        register(new ChainTool(configurationService, physicsWorldRegistry, shapeFactory, bodyFactory));

        register(new RagdollTool(configurationService, physicsWorldRegistry, shapeFactory));
        register(new ShapeTool(configurationService, physicsWorldRegistry, bodyFactory));
        register(new TNTSpawnTool(configurationService, physicsWorldRegistry, bodyFactory));
    }

    public void register(Tool tool) {
        tools.put(tool.getId(), tool);
    }

    public Tool getTool(String id) {
        return tools.get(id);
    }

    private Tool getToolFromItem(ItemStack stack) {
        if (stack == null) return null;
        for (Tool tool : tools.values()) {
            if (tool.isTool(stack)) return tool;
        }
        return null;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Tool tool = getToolFromItem(event.getItem());
        if (tool == null) return;

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            tool.onRightClick(event);
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            tool.onLeftClick(event);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Tool tool = getToolFromItem(event.getOffHandItem());
        if (tool != null) {
            event.setCancelled(true);
            tool.onSwapHands(event.getPlayer());
        }
    }

    @EventHandler
    public void onHotbarChange(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getPreviousSlot());
        Tool tool = getToolFromItem(item);
        if (tool != null) {
            int newSlot = event.getNewSlot();
            int oldSlot = event.getPreviousSlot();
            int diff = newSlot - oldSlot;
            if (diff == -8) diff = 1;
            if (diff == 8) diff = -1;

            if (tool.onHotbarChange(event, diff)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Tool chainTool = getTool("chain_tool");
        if (chainTool instanceof ChainTool ct) {
            ct.onSwapHands(event.getPlayer());
        }
    }
}