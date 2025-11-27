package com.ladakx.inertia.commands;

import co.aikar.commands.annotation.*;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.body.InertiaPhysicsObject;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.files.config.message.MessageKey;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("inertia")
@Description("Main inertia plugin command.")
public class Commands extends BaseCommand {

    @Subcommand("reload")
    @CommandPermission("inertia.commands.reload")
    @Description("Reload the entire plugin.")
    public void onReloadCommand(CommandSender sender) {
        if (checkPermission(sender, "inertia.commands.reload", true)) {
            InertiaPlugin.getInstance().reload();
            send(sender, MessageKey.RELOAD_PLUGIN);
        }
    }

    @Default
    @CatchUnknown
    @Subcommand("help")
    @Description("Help plugin command")
    public void onHelp(CommandSender sender) {
        if (checkPermission(sender, "inertia.commands.help.admin", false)) {
            send(sender, MessageKey.HELP_COMMAND_ADMIN);
        } else if (checkPermission(sender, "inertia.commands.help", false)) {
            send(sender, MessageKey.HELP_COMMAND);
        } else {
            send(sender, MessageKey.NO_PERMISSIONS);
        }
    }

    @Subcommand("spawn")
    @CommandPermission("inertia.commands.spawn")
    @CommandCompletion("@bodies") // Використовує зареєстрований комплішн з Main класу
    @Description("Spawn a physics body at your location")
    public void onSpawnCommand(Player player, String bodyId) {
        if (!checkPermission(player, "inertia.commands.spawn", true)) return;

        // Перевіряємо, чи світ є фізичним
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return;
        }

        // Створюємо об'єкт через API
        InertiaPhysicsObject obj = InertiaAPI.get().createBody(player.getLocation(), bodyId);

        if (obj != null) {
            send(player, MessageKey.SPAWN_SUCCESS, "{id}", bodyId);
        } else {
            // Якщо об'єкт null, але світ симулюється, значить ID неправильний
            send(player, MessageKey.SPAWN_FAIL_INVALID_ID, "{id}", bodyId);
        }
    }

    @Subcommand("clear")
    @CommandPermission("inertia.commands.clear")
    @Description("Clear all physics bodies in the current world")
    public void onClearCommand(Player player) {
        if (!checkPermission(player, "inertia.commands.clear", true)) return;

        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());

        if (space == null) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return;
        }

        int countBefore = space.getObjects().size();
        space.removeAllObjects();

        send(player, MessageKey.CLEAR_SUCCESS, "{count}", String.valueOf(countBefore));
    }

    @Subcommand("debug bar")
    @Description("Debug command")
    public void onDebugCommand(CommandSender sender) {
        if (!checkPermission(sender, "inertia.commands.debug.bar", true)) return;
        if (!(sender instanceof Player player)) return;

        // logic here (TODO: Implement debug bar toggle logic later)
    }

    // --- Helper Method ---
    private void send(CommandSender sender, MessageKey key, String... replacements) {
        ConfigManager.getInstance().getMessageManager().send(sender, key, replacements);
    }
}