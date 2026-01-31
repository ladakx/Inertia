package com.ladakx.inertia.features.commands;

import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Abstract base class for all Inertia command modules.
 * Provides common services and validation logic.
 */
public abstract class BaseCommand extends co.aikar.commands.BaseCommand {

    protected final ConfigurationService configurationService;

    protected BaseCommand(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    /**
     * Sends a localized message to the sender.
     */
    protected void send(CommandSender sender, MessageKey key, String... replacements) {
        configurationService.getMessageManager().send(sender, key, replacements);
    }

    /**
     * Checks permission and optionally sends a no-permission message.
     */
    protected boolean checkPermission(CommandSender sender, String permission, boolean showMSG) {
        if (sender.hasPermission(permission)) return true;
        if (showMSG) {
            send(sender, MessageKey.NO_PERMISSIONS);
        }
        return false;
    }

    /**
     * Validates that the player is in a physics-enabled world.
     */
    protected boolean validateWorld(Player player) {
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return false;
        }
        return true;
    }

    /**
     * Validates that a body ID exists in the registry.
     */
    protected boolean validateBodyExists(Player player, String bodyId) {
        PhysicsBodyRegistry registry = configurationService.getPhysicsBodyRegistry();
        if (registry.find(bodyId).isEmpty()) {
            send(player, MessageKey.SPAWN_FAIL_INVALID_ID, "{id}", bodyId);
            return false;
        }
        return true;
    }

    /**
     * Standard exception handler for commands.
     */
    protected void handleException(Player player, Exception e) {
        InertiaLogger.error("Error executing command for " + player.getName(), e);
        String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
        send(player, MessageKey.ERROR_OCCURRED, "{error}", msg);
    }
}