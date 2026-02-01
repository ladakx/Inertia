package com.ladakx.inertia.features.commands.impl;

import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.features.commands.CloudModule;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.StringParser;

public class SystemCommands extends CloudModule {

    private final MinecraftHelp<CommandSender> help;

    public SystemCommands(CommandManager<CommandSender> manager,
                          ConfigurationService config,
                          MinecraftHelp<CommandSender> help) {
        super(manager, config);
        this.help = help;
    }

    @Override
    public void register() {
        // Command: /inertia reload
        manager.command(rootBuilder()
                .literal("reload")
                .permission("inertia.commands.reload")
                .handler(ctx -> {
                    InertiaPlugin.getInstance().reload();
                    send(ctx.sender(), MessageKey.RELOAD_PLUGIN);
                })
        );

        // Command: /inertia help [query]
        manager.command(rootBuilder()
                .literal("help")
                .permission("inertia.commands.help")
                .optional("query", StringParser.greedyStringParser())
                .handler(ctx -> {
                    String query = ctx.getOrDefault("query", "");
                    help.queryCommands(query, ctx.sender());
                })
        );
    }
}