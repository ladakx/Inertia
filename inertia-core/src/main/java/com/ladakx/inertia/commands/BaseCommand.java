package com.ladakx.inertia.commands;

import com.ladakx.inertia.config.ConfigManager;
import com.ladakx.inertia.config.message.MessageKey;
import org.bukkit.command.CommandSender;

public abstract class BaseCommand extends co.aikar.commands.BaseCommand {

    protected final ConfigManager configManager;

    public BaseCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public boolean checkPermission(CommandSender sender, String permission, boolean showMSG) {
        if (sender.hasPermission(permission)) return true;

        if (showMSG) {
            // DI usage instead of static call
            configManager.getMessageManager().send(sender, MessageKey.NO_PERMISSIONS);
        }

        return false;
    }
}