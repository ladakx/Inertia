package com.ladakx.inertia.features.commands.parsers;

import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class BodyIdByTypeParser implements ArgumentParser<CommandSender, String>, BlockingSuggestionProvider.Strings<CommandSender> {

    private final ConfigurationService configService;
    private final PhysicsBodyType type;

    public BodyIdByTypeParser(ConfigurationService configService, PhysicsBodyType type) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static @NonNull ParserDescriptor<CommandSender, String> bodyIdByTypeParser(ConfigurationService configService, PhysicsBodyType type) {
        return ParserDescriptor.of(new BodyIdByTypeParser(configService, type), String.class);
    }

    @Override
    public @NonNull ArgumentParseResult<String> parse(@NonNull CommandContext<CommandSender> commandContext, @NonNull CommandInput commandInput) {
        String input = commandInput.readString();

        return configService.getPhysicsBodyRegistry().find(input)
                .filter(model -> model.bodyDefinition().type() == type)
                .map(model -> ArgumentParseResult.success(model.bodyDefinition().id()))
                .orElseGet(() -> ArgumentParseResult.failure(new IllegalArgumentException("Body not found for type " + type + ": " + input)));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<CommandSender> commandContext, @NonNull CommandInput commandInput) {
        List<String> ids = configService.getPhysicsBodyRegistry().all().stream()
                .map(PhysicsBodyRegistry.BodyModel::bodyDefinition)
                .filter(def -> def.type() == type)
                .map(BodyDefinition::id)
                .sorted(Comparator.naturalOrder())
                .toList();
        return ids;
    }
}

