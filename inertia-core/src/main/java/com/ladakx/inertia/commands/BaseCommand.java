package com.ladakx.inertia.commands;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.files.config.message.MessageKey;
import org.bukkit.command.CommandSender;

public class BaseCommand extends co.aikar.commands.BaseCommand {

    public boolean checkPermission(CommandSender sender, String permission, boolean showMSG) {
        if (sender.hasPermission(permission)) return true;
        if (showMSG) InertiaPlugin.getInstance().getConfigManager().getMessageManager().send(sender, MessageKey.NO_PERMISSIONS);

        return false;
    }
}
