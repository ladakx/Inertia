package com.ladakx.inertia.tools;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.tools.impl.*;
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

public class ToolManager implements Listener {

    private static ToolManager instance;
    private final Map<String, Tool> tools = new HashMap<>();

    private ToolManager(InertiaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        register(new ChainTool());
        register(new DeleteTool());
        register(new WeldTool());
        register(new GrabberTool());
        register(new RagdollTool());
        register(new ShapeTool());
        register(new TNTSpawnTool());
    }

    public static void init(InertiaPlugin plugin) {
        if (instance == null) instance = new ToolManager(plugin);
    }

    public static ToolManager getInstance() {
        return instance;
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