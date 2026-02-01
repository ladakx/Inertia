package com.ladakx.inertia.features.commands;

import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;

public abstract class CloudModule {

    protected final CommandManager<CommandSender> manager;
    protected final ConfigurationService config;

    public CloudModule(CommandManager<CommandSender> manager, ConfigurationService config) {
        this.manager = manager;
        this.config = config;
    }

    public abstract void register();

    /**
     * Создает корневой билдер для команды /inertia
     */
    protected Command.Builder<CommandSender> rootBuilder() {
        return manager.commandBuilder("inertia", "phys");
    }

    protected boolean validatePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            config.getMessageManager().send(sender, MessageKey.NOT_FOR_CONSOLE);
            return false;
        }
        return true;
    }

    protected boolean validateWorld(Player player) {
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) {
            config.getMessageManager().send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return false;
        }
        return true;
    }

    protected void send(CommandSender sender, MessageKey key, String... replacements) {
        config.getMessageManager().send(sender, key, replacements);
    }
}