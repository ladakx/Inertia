/* New file: inertia-plugin/src/main/java/com/ladakx/inertia/command/TestBoxCommand.java */

package com.ladakx.inertia.command;

import com.ladakx.inertia.InertiaSpigotPlugin;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command(name = "spawnbox")
public class TestBoxCommand {

    private final InertiaSpigotPlugin plugin;

    public TestBoxCommand(InertiaSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @Execute
    public void execute(@Context CommandSender commandSender) {
        if (commandSender instanceof Player player) {
            plugin.getInertiaCore().getInertiaObjectManager().createDynamicBox(player.getLocation().add(0, 2, 0));
            player.sendMessage("Spawned a physics box!");
        }
    }
}