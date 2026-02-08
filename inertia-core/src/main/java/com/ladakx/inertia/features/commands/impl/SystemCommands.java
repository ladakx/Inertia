package com.ladakx.inertia.features.commands.impl;

import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.features.commands.CloudModule;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SystemCommands extends CloudModule {

    private static final List<String> HELP_TOPICS = List.of(
            "spawn",
            "clear",
            "debug",
            "tool",
            "admin",
            "entity",
            "topics"
    );

    private static final Map<String, Integer> HELP_TOPIC_PAGE_COUNT = Map.ofEntries(
            Map.entry("spawn", 2),
            Map.entry("clear", 1),
            Map.entry("debug", 1),
            Map.entry("tool", 1),
            Map.entry("admin", 1),
            Map.entry("entity", 1),
            Map.entry("topics", 2)
    );

    private static final int HELP_INDEX_MAX_PAGE = 2;

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

        // Command: /inertia help
        manager.command(rootBuilder()
                .literal("help")
                .permission("inertia.commands.help")
                .optional("query", StringParser.stringParser(),
                        SuggestionProvider.blockingStrings((ctx, input) -> suggestHelpQueries()))
                .optional("page", IntegerParser.integerParser(1),
                        SuggestionProvider.blockingStrings((ctx, input) -> suggestHelpPageNumbers(ctx, input)))
                .handler(ctx -> {
                    String query = ctx.getOrDefault("query", null);
                    Integer page = ctx.getOrDefault("page", null);
                    handleHelp(ctx.sender(), query, page);
                })
        );
    }

    private void handleHelp(CommandSender sender, String queryRaw, Integer explicitPage) {
        if (sender == null) return;
        if (queryRaw == null || queryRaw.isBlank()) {
            sendHelpIndex(sender, explicitPage == null ? 1 : explicitPage);
            return;
        }

        String normalized = queryRaw.toLowerCase(Locale.ROOT);
        if (isNumeric(normalized)) {
            sendHelpIndex(sender, Integer.parseInt(normalized));
            return;
        }

        int page = explicitPage == null ? 1 : explicitPage;
        switch (normalized) {
            case "topics", "topic", "list" -> sendHelpIndex(sender, page);
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
        int max = HELP_INDEX_MAX_PAGE;
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

    private static List<String> suggestHelpQueries() {
        List<String> pageHints = IntStream.rangeClosed(1, HELP_INDEX_MAX_PAGE)
                .mapToObj(Integer::toString)
                .toList();
        return Stream.concat(HELP_TOPICS.stream(), pageHints.stream())
                .toList();
    }

    private static List<String> suggestHelpPageNumbers(CommandContext<CommandSender> ctx, CommandInput input) {
        String query = ctx.getOrDefault("query", "topics");
        if (query == null) {
            query = "topics";
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        int max = switch (normalized) {
            case "topics", "topic", "list" -> HELP_INDEX_MAX_PAGE;
            default -> HELP_TOPIC_PAGE_COUNT.getOrDefault(normalized, 1);
        };
        if (max < 1) {
            max = 1;
        }
        return IntStream.rangeClosed(1, max)
                .mapToObj(Integer::toString)
                .toList();
    }

    private static boolean isNumeric(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

}
