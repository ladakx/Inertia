package com.ladakx.inertia.commands;

import com.ladakx.inertia.config.ConfigManager;
import com.ladakx.inertia.config.message.MessageKey;
import org.bukkit.command.CommandSender;

public class BaseCommand extends co.aikar.commands.BaseCommand {

    public boolean checkPermission(CommandSender sender, String permission, boolean showMSG) {
        if (sender.hasPermission(permission)) return true;
        if (showMSG) ConfigManager.getInstance().getMessageManager().send(sender, MessageKey.NO_PERMISSIONS);

        return false;
    }
}