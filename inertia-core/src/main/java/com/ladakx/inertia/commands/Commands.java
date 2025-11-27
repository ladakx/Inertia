package com.ladakx.inertia.commands;

import co.aikar.commands.annotation.*;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.body.InertiaPhysicsObject;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.files.config.message.MessageKey;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.tools.Tool;
import com.ladakx.inertia.tools.ToolManager;
import com.ladakx.inertia.tools.impl.ChainTool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

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

    @Subcommand("tool chain")
    @CommandPermission("inertia.commands.tool")
    @CommandCompletion("@bodies")
    @Description("Get a tool to spawn chains between two points")
    public void onToolChain(Player player, String bodyId) {
        if (!checkPermission(player, "inertia.commands.tool", true)) return;

        ToolManager tm = ToolManager.getInstance();
        Tool tool = tm.getTool("chain_tool"); // Переконайтесь що ID в конструкторі ChainTool співпадає

        if (tool instanceof ChainTool chainTool) {
            player.getInventory().addItem(chainTool.getToolItem(bodyId));
            player.sendMessage(Component.text("Received Chain Tool for: " + bodyId, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Chain tool not registered!", NamedTextColor.RED));
        }
    }

    // --- Helper Method ---
    private void send(CommandSender sender, MessageKey key, String... replacements) {
        ConfigManager.getInstance().getMessageManager().send(sender, key, replacements);
    }
}