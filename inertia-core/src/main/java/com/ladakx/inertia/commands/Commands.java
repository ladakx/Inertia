package com.ladakx.inertia.commands;

import co.aikar.commands.annotation.*;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.files.config.message.MessageKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("inertia")
@Description("Main inertia plugin command.")
public class Commands extends BaseCommand {

    @Subcommand("reload")
    @CommandPermission("inertia.commands.reload")
    @Description("Reload the entire plugin.")
    public void onReloadCommand(CommandSender sender, @Default String arg) {
        if (checkPermission(sender, "inertia.commands.reload", true)) {
            InertiaPlugin.getInstance().reload();
            send(sender, MessageKey.RELOAD_PLUGIN);
        }
    }

    @Default
    @CatchUnknown
    @Subcommand("help")
    @Description("Help plugin command")
    public void onHelp(CommandSender sender, @Default String arg) {
        if (checkPermission(sender, "inertia.commands.help.admin", false)) {
            send(sender, MessageKey.HELP_COMMAND_ADMIN);
        } else if (checkPermission(sender, "inertia.commands.help", false)) {
            send(sender, MessageKey.HELP_COMMAND);
        } else {
            send(sender, MessageKey.NO_PERMISSIONS);
        }
    }

    @Subcommand("debug bar")
    @Description("Debug command")
    public void onDebugCommand(CommandSender sender, @Default String arg) {
        if (!checkPermission(sender, "inertia.commands.debug.bar", true)) return;
        if (!(sender instanceof Player player)) return;

        // logic here
    }

    // --- Helper Method ---
    private void send(CommandSender sender, MessageKey key, String... replacements) {
        ConfigManager.getInstance().getMessageManager().send(sender, key, replacements);
    }
}