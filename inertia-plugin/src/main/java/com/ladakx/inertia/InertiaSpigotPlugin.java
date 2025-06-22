/* Original project path: inertia-plugin/src/main/java/com/ladakx/inertia/InertiaSpigotPlugin.java */

package com.ladakx.inertia;

import com.ladakx.inertia.api.InertiaProvider;
import com.ladakx.inertia.command.TestBoxCommand;
import com.ladakx.inertia.core.InertiaCore;
import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class InertiaSpigotPlugin extends JavaPlugin {

    private InertiaCore inertiaCore;
    private LiteCommands<CommandSender> liteCommands;

    @Override
    public void onEnable() {
        this.inertiaCore = new InertiaCore(this);
        InertiaProvider.register(this.inertiaCore.getApi());
        this.inertiaCore.load();

        // Correct initialization based on your example
        this.liteCommands = LiteBukkitFactory.builder("inertia", this)
                .commands(
                        new TestBoxCommand(this)
                        // We can add more command instances here in the future
                )
                .build(); // Using .build() as in the example

        getLogger().info("Inertia has been enabled!");
    }

    @Override
    public void onDisable() {
        if (this.liteCommands != null) {
            this.liteCommands.unregister();
        }
        if (this.inertiaCore != null) {
            this.inertiaCore.unload();
            InertiaProvider.unregister();
        }
        getLogger().info("Inertia has been disabled!");
    }

    public InertiaCore getInertiaCore() {
        return inertiaCore;
    }
}