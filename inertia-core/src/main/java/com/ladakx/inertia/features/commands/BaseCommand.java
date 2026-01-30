package com.ladakx.inertia.features.commands;

import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import org.bukkit.command.CommandSender;

public abstract class BaseCommand extends co.aikar.commands.BaseCommand {

    protected final ConfigurationService configurationService;

    public BaseCommand(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public boolean checkPermission(CommandSender sender, String permission, boolean showMSG) {
        if (sender.hasPermission(permission)) return true;

        if (showMSG) {
            // DI usage instead of static call
            configurationService.getMessageManager().send(sender, MessageKey.NO_PERMISSIONS);
        }

        return false;
    }
}