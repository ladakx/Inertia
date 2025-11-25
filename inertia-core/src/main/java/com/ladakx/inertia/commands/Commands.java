package com.ladakx.inertia.commands;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.files.config.message.MessageKey;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.Default; // Якщо потрібно для аргументів

public class Commands { // Більше не extends BaseCommand

    private final InertiaPlugin plugin;

    public Commands(InertiaPlugin plugin) {
        this.plugin = plugin;
    }

    // Команда: /inertia reload
    @Command("inertia reload")
    @Permission("inertia.commands.reload")
    public void onReloadCommand(CommandSender sender) {
        // Тут більше не треба if (checkPermission...), Cloud це вже перевірив
        plugin.reload();
        send(sender, MessageKey.RELOAD_PLUGIN);
    }

    // Команда: /inertia help
    // Також спрацює на просто /inertia (якщо ми це налаштуємо як fallback)
    @Command("inertia help")
    @Permission("inertia.commands.help")
    public void onHelp(CommandSender sender) {
        // Логіка для адміна
        if (sender.hasPermission("inertia.commands.help.admin")) {
            send(sender, MessageKey.HELP_COMMAND_ADMIN);
        } else {
            send(sender, MessageKey.HELP_COMMAND);
        }
    }

    // Команда: /inertia (коренева)
    @Command("inertia")
    @Permission("inertia.commands.help")
    public void onRoot(CommandSender sender) {
        onHelp(sender); // Просто перенаправляємо на help
    }

    // --- Helper Method ---
    private void send(CommandSender sender, MessageKey key, String... replacements) {
        plugin.getConfigManager().getMessageManager().send(sender, key, replacements);
    }
}