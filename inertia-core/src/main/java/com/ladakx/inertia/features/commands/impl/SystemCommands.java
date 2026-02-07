package com.ladakx.inertia.features.commands.impl;

import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.features.commands.CloudModule;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.parser.standard.StringParser;

import java.util.Locale;

public class SystemCommands extends CloudModule {

    public SystemCommands(CommandManager<CommandSender> manager,
                          ConfigurationService config) {
        super(manager, config);
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

        // Command: /inertia help [page|topic] [page]
        manager.command(rootBuilder()
                .literal("help")
                .permission("inertia.commands.help")
                .optional("query", StringParser.greedyStringParser())
                .handler(ctx -> {
                    String query = ctx.getOrDefault("query", "").trim();
                    sendHelp(ctx.sender(), query);
                })
        );
    }

    private void sendHelp(CommandSender sender, String query) {
        if (sender == null) {
            return;
        }

        String[] args = query.isEmpty() ? new String[0] : query.split("\\s+");

        if (args.length == 0) {
            sendHelpIndex(sender, 1);
            return;
        }

        if (isPositiveInt(args[0])) {
            sendHelpIndex(sender, Integer.parseInt(args[0]));
            return;
        }

        String topic = args[0].toLowerCase(Locale.ROOT);
        int page = (args.length >= 2 && isPositiveInt(args[1])) ? Integer.parseInt(args[1]) : 1;

        switch (topic) {
            case "topics", "topic", "list" -> sendHelpIndex(sender, 1);
            case "spawn" -> sendHelpTopic(sender, "spawn", page);
            case "clear" -> sendHelpTopic(sender, "clear", page);
            case "debug" -> sendHelpTopic(sender, "debug", page);
            case "tool", "tools" -> sendHelpTopic(sender, "tool", page);
            case "admin" -> sendHelpTopic(sender, "admin", page);
            case "entity" -> sendHelpTopic(sender, "entity", page);
            default -> {
                sendHelpIndex(sender, 1);
                send(sender, MessageKey.HELP_TIP_TOPICS);
            }
        }
    }

    private void sendHelpIndex(CommandSender sender, int page) {
        int max = 2;
        int safePage = Math.max(1, Math.min(page, max));
        MessageKey key = safePage == 1 ? MessageKey.HELP_INDEX_1 : MessageKey.HELP_INDEX_2;
        send(sender, key, "{page}", String.valueOf(safePage), "{max}", String.valueOf(max));
    }

    private void sendHelpTopic(CommandSender sender, String topic, int page) {
        if ("spawn".equals(topic)) {
            int max = 2;
            int safePage = Math.max(1, Math.min(page, max));
            MessageKey key = safePage == 1 ? MessageKey.HELP_SPAWN_1 : MessageKey.HELP_SPAWN_2;
            send(sender, key, "{page}", String.valueOf(safePage), "{max}", String.valueOf(max));
            return;
        }

        MessageKey key = switch (topic) {
            case "clear" -> MessageKey.HELP_CLEAR_1;
            case "entity" -> MessageKey.HELP_ENTITY_1;
            case "debug" -> MessageKey.HELP_DEBUG_1;
            case "tool" -> MessageKey.HELP_TOOL_1;
            case "admin" -> MessageKey.HELP_ADMIN_1;
            default -> MessageKey.HELP_INDEX_1;
        };
        send(sender, key, "{page}", "1", "{max}", "1");
    }

    private boolean isPositiveInt(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        try {
            return Integer.parseInt(s) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
