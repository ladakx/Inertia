package com.ladakx.inertia.commands;

import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.files.config.message.MessageKey;
import org.bukkit.command.CommandSender;

public class Commands {

    // --- Helper Method ---
    private void send(CommandSender sender, MessageKey key, String... replacements) {
        ConfigManager.getInstance().getMessageManager().send(sender, key, replacements);
    }
}