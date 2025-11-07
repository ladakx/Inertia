package com.ladakx.inertia.commands;

import co.aikar.commands.BaseCommand;
import com.ladakx.inertia.files.config.MessagesCFG;
import org.bukkit.command.CommandSender;

public class RoseCommand extends BaseCommand {

    public boolean checkPermission(CommandSender sender, String permission, boolean showMSG) {
        if (sender.hasPermission(permission)) return true;
        if (showMSG) MessagesCFG.NO_PERMISSIONS.sendMessage(sender);

        return false;
    }
}
