package com.ladakx.inertia.features.commands.impl;

import co.aikar.commands.annotation.*;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.commands.BaseCommand;
import org.bukkit.command.CommandSender;

@CommandAlias("inertia")
public class SystemCommands extends BaseCommand {

    public SystemCommands(ConfigurationService configurationService) {
        super(configurationService);
    }

    @Subcommand("reload")
    @CommandPermission("inertia.commands.reload")
    @Description("Reloads the plugin configuration.")
    public void onReloadCommand(CommandSender sender) {
        if (checkPermission(sender, "inertia.commands.reload", true)) {
            InertiaPlugin.getInstance().reload();
            send(sender, MessageKey.RELOAD_PLUGIN);
        }
    }

    @Default
    @CatchUnknown
    @Subcommand("help")
    @Description("Shows help information.")
    public void onHelp(CommandSender sender) {
        if (checkPermission(sender, "inertia.commands.help.admin", false)) {
            send(sender, MessageKey.HELP_COMMAND_ADMIN);
        } else {
            send(sender, MessageKey.HELP_COMMAND);
        }
    }
}