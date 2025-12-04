package com.ladakx.inertia.commands;

import co.aikar.commands.annotation.*;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.body.InertiaPhysicsObject;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.files.config.message.MessageKey;
import com.ladakx.inertia.items.ItemManager;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.physics.config.BodyDefinition;
import com.ladakx.inertia.physics.config.RagdollDefinition;
import com.ladakx.inertia.physics.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.tools.Tool;
import com.ladakx.inertia.tools.ToolManager;
import com.ladakx.inertia.tools.impl.ChainTool;
import com.ladakx.inertia.tools.impl.RagdollTool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.Optional;

@CommandAlias("inertia")
@Description("Main inertia plugin command.")
public class Commands extends BaseCommand {

    @Subcommand("reload")
    @CommandPermission("inertia.commands.reload")
    @Description("Reload the entire plugin.")
    public void onReloadCommand(CommandSender sender) {
        if (checkPermission(sender, "inertia.commands.reload", true)) {
            InertiaPlugin.getInstance().reload();
            send(sender, MessageKey.RELOAD_PLUGIN);
        }
    }

    @Default
    @CatchUnknown
    @Subcommand("help")
    @Description("Help plugin command")
    public void onHelp(CommandSender sender) {
        if (checkPermission(sender, "inertia.commands.help.admin", false)) {
            send(sender, MessageKey.HELP_COMMAND_ADMIN);
        } else if (checkPermission(sender, "inertia.commands.help", false)) {
            send(sender, MessageKey.HELP_COMMAND);
        } else {
            send(sender, MessageKey.NO_PERMISSIONS);
        }
    }

    // --- Spawn Commands ---

    @Subcommand("spawn body")
    @CommandPermission("inertia.commands.spawn")
    @CommandCompletion("@bodies")
    @Description("Spawn a generic physics body")
    public void onSpawnCommand(Player player, String bodyId) {
        if (!checkPermission(player, "inertia.commands.spawn", true)) return;
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return;
        }

        InertiaPhysicsObject obj = InertiaAPI.get().createBody(player.getLocation(), bodyId);
        if (obj != null) {
            send(player, MessageKey.SPAWN_SUCCESS, "{id}", bodyId);
        } else {
            send(player, MessageKey.SPAWN_FAIL_INVALID_ID, "{id}", bodyId);
        }
    }

    @Subcommand("spawn chain")
    @CommandPermission("inertia.commands.spawn")
    @CommandCompletion("@bodies")
    @Description("Spawn a physics chain structure directly")
    public void onSpawnChain(Player player, String bodyId) {
        if (!checkPermission(player, "inertia.commands.spawn", true)) return;
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return;
        }

        Location startLoc = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceBlocks(startLoc, startLoc.getDirection(), 10);

        Location targetLoc;
        if (result != null && result.getHitPosition() != null) {
            targetLoc = result.getHitPosition().toLocation(player.getWorld()).subtract(startLoc.getDirection().multiply(0.2));
        } else {
            targetLoc = startLoc.add(startLoc.getDirection().multiply(3));
        }

        ChainTool.buildChainBetweenPoints(player, targetLoc.clone().add(0, 5, 0), targetLoc, bodyId);
    }

    @Subcommand("clear")
    @CommandPermission("inertia.commands.clear")
    @Description("Clear all physics bodies in the current world")
    public void onClearCommand(Player player) {
        if (!checkPermission(player, "inertia.commands.clear", true)) return;
        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());

        if (space == null) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return;
        }

        int countBefore = space.getObjects().size();
        space.removeAllObjects();

        send(player, MessageKey.CLEAR_SUCCESS, "{count}", String.valueOf(countBefore));
    }

    // --- Tools Commands ---

    @Subcommand("tool chain")
    @CommandPermission("inertia.commands.tool")
    @CommandCompletion("@bodies")
    @Description("Get a tool to spawn chains")
    public void onToolChain(Player player, String bodyId) {
        if (!checkPermission(player, "inertia.commands.tool", true)) return;
        ToolManager tm = ToolManager.getInstance();
        Tool tool = tm.getTool("chain_tool");

        if (tool instanceof ChainTool chainTool) {
            player.getInventory().addItem(chainTool.getToolItem(bodyId));
            player.sendMessage(Component.text("Received Chain Tool for: " + bodyId, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Chain tool not registered!", NamedTextColor.RED));
        }
    }

    @Subcommand("tool ragdoll")
    @CommandPermission("inertia.commands.tool")
    @CommandCompletion("@bodies")
    @Description("Get a tool to spawn ragdolls")
    public void onToolRagdoll(Player player, String bodyId) {
        if (!checkPermission(player, "inertia.commands.tool", true)) return;

        // Validate Body ID
        PhysicsBodyRegistry registry = ConfigManager.getInstance().getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(bodyId);

        if (modelOpt.isEmpty() || !(modelOpt.get().bodyDefinition() instanceof RagdollDefinition)) {
            player.sendMessage(Component.text("Invalid or unknown ragdoll body: " + bodyId, NamedTextColor.RED));
            return;
        }

        ToolManager tm = ToolManager.getInstance();
        Tool tool = tm.getTool("ragdoll_tool");

        if (tool instanceof RagdollTool ragdollTool) {
            player.getInventory().addItem(ragdollTool.getToolItem(bodyId));
            player.sendMessage(Component.text("Received Ragdoll Tool for: " + bodyId, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Ragdoll tool not registered!", NamedTextColor.RED));
        }
    }

    @Subcommand("tool grabber")
    @CommandPermission("inertia.commands.tool")
    @Description("Get the Gravity Grabber tool")
    public void onToolGrabber(Player player) {
        giveSimpleTool(player, "grabber", "Gravity Grabber");
    }

    @Subcommand("tool welder")
    @CommandPermission("inertia.commands.tool")
    @Description("Get the Welder tool")
    public void onToolWelder(Player player) {
        giveSimpleTool(player, "welder", "Welder");
    }

    @Subcommand("tool remover")
    @CommandPermission("inertia.commands.tool")
    @Description("Get the Remover tool")
    public void onToolRemover(Player player) {
        giveSimpleTool(player, "remover", "Remover");
    }

    // --- Item Commands ---

    @Subcommand("give")
    @CommandPermission("inertia.commands.give")
    @CommandCompletion("@items")
    @Description("Get a custom item from items.yml")
    public void onGiveItem(Player player, String itemId) {
        if (!checkPermission(player, "inertia.commands.give", true)) return;
        ItemStack item = ItemManager.getInstance().getItem(itemId);

        if (item != null) {
            player.getInventory().addItem(item);
            player.sendMessage(Component.text("Received item: " + itemId, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Item '" + itemId + "' not found.", NamedTextColor.RED));
        }
    }

    // --- Helper Methods ---

    private void giveSimpleTool(Player player, String toolId, String toolName) {
        if (!checkPermission(player, "inertia.commands.tool", true)) return;
        ToolManager tm = ToolManager.getInstance();
        Tool tool = tm.getTool(toolId);

        if (tool != null) {
            player.getInventory().addItem(tool.buildItem());
            player.sendMessage(Component.text("Received tool: " + toolName, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Tool '" + toolId + "' is not registered.", NamedTextColor.RED));
        }
    }

    private void send(CommandSender sender, MessageKey key, String... replacements) {
        ConfigManager.getInstance().getMessageManager().send(sender, key, replacements);
    }
}