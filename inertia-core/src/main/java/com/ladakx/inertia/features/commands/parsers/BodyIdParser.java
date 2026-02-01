package com.ladakx.inertia.features.commands.parsers;

import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.physics.body.config.BodyDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

import java.util.stream.Collectors;

public class BodyIdParser implements ArgumentParser<CommandSender, String>, BlockingSuggestionProvider.Strings<CommandSender> {

    private final ConfigurationService configService;

    public BodyIdParser(ConfigurationService configService) {
        this.configService = configService;
    }

    public static @NonNull ParserDescriptor<CommandSender, String> bodyIdParser(ConfigurationService configService) {
        return ParserDescriptor.of(new BodyIdParser(configService), String.class);
    }

    @Override
    public @NonNull ArgumentParseResult<String> parse(@NonNull CommandContext<CommandSender> commandContext, @NonNull CommandInput commandInput) {
        String input = commandInput.readString();

        if (configService.getPhysicsBodyRegistry().find(input).isPresent()) {
            return ArgumentParseResult.success(input);
        }

        return ArgumentParseResult.failure(new IllegalArgumentException("Body not found: " + input));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<CommandSender> commandContext, @NonNull CommandInput commandInput) {
        return configService.getPhysicsBodyRegistry().all().stream()
                .map(PhysicsBodyRegistry.BodyModel::bodyDefinition)
                .map(BodyDefinition::id)
                .collect(Collectors.toList());
    }
}