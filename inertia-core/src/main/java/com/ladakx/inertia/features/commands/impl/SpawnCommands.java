package com.ladakx.inertia.features.commands.impl;

import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.commands.CloudModule;
import com.ladakx.inertia.features.commands.parsers.BodyIdParser;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeGenerator;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeManager;
import com.ladakx.inertia.physics.factory.BodyFactory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.util.Arrays;

public class SpawnCommands extends CloudModule {

    private final BodyFactory bodyFactory;
    private final DebugShapeManager debugShapeManager;

    public SpawnCommands(CommandManager<CommandSender> manager, ConfigurationService config, BodyFactory bodyFactory) {
        super(manager, config);
        this.bodyFactory = bodyFactory;
        this.debugShapeManager = new DebugShapeManager();
    }

    @Override
    public void register() {
        // Создаем базовый билдер для /inertia spawn
        var spawnRoot = rootBuilder().literal("spawn").permission("inertia.commands.spawn");

        // /inertia spawn body <id>
        manager.command(spawnRoot
                .literal("body")
                .required("id", BodyIdParser.bodyIdParser(config))
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    if (!validateWorld(player)) return;

                    String id = ctx.get("id");
                    if (bodyFactory.spawnBody(player.getLocation(), id)) {
                        send(player, MessageKey.SPAWN_SUCCESS, "{id}", id);
                    } else {
                        send(player, MessageKey.ERROR_OCCURRED, "{error}", "Failed to spawn (Limit reached?)");
                    }
                })
        );

        // /inertia spawn chain <id> [size]
        manager.command(spawnRoot
                .literal("chain")
                .required("id", BodyIdParser.bodyIdParser(config))
                .optional("size", IntegerParser.integerParser(1, 100),
                        Description.of("Links count (1-100)"))
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    if (!validateWorld(player)) return;

                    String id = ctx.get("id");
                    int size = ctx.getOrDefault("size", 10);

                    try {
                        bodyFactory.spawnChain(player, id, size);
                        send(player, MessageKey.CHAIN_SPAWN_SUCCESS, "{size}", String.valueOf(size));
                    } catch (Exception e) {
                        send(player, MessageKey.ERROR_OCCURRED, "{error}", e.getMessage());
                    }
                })
        );

        // /inertia spawn ragdoll <id>
        manager.command(spawnRoot
                .literal("ragdoll")
                .required("id", BodyIdParser.bodyIdParser(config))
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    if (!validateWorld(player)) return;

                    String id = ctx.get("id");
                    try {
                        bodyFactory.spawnRagdoll(player, id);
                        send(player, MessageKey.RAGDOLL_SPAWN_SUCCESS, "{id}", id);
                    } catch (Exception e) {
                        send(player, MessageKey.ERROR_OCCURRED, "{error}", e.getMessage());
                    }
                })
        );

        // /inertia spawn tnt <id> [force]
        manager.command(spawnRoot
                .literal("tnt")
                .required("id", BodyIdParser.bodyIdParser(config))
                .optional("force", DoubleParser.doubleParser(0.1, 100.0),
                        Description.of("Explosion force"))
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    if (!validateWorld(player)) return;

                    String id = ctx.get("id");
                    float force = ctx.<Double>getOrDefault("force", 20.0).floatValue();

                    try {
                        bodyFactory.spawnTNT(player.getLocation().add(0, 0.5, 0), id, force, null);
                        send(player, MessageKey.TNT_SPAWNED, "{force}", String.valueOf(force));
                    } catch (Exception e) {
                        send(player, MessageKey.ERROR_OCCURRED, "{error}", e.getMessage());
                    }
                })
        );

        // /inertia spawn-shape <type> <id> <params>
        manager.command(rootBuilder()
                .literal("spawn-shape")
                .permission("inertia.commands.spawn")
                .required("type", StringParser.stringParser(),
                        SuggestionProvider.blockingStrings((c, i) -> debugShapeManager.getAvailableShapes()))
                .required("id", BodyIdParser.bodyIdParser(config))
                .required("params", StringParser.greedyStringParser(),
                        // ИСПРАВЛЕНИЕ: Возвращаем CompletableFuture
                        (ctx, input) -> {
                            String type = ctx.getOrDefault("type", null);
                            if (type == null) return java.util.concurrent.CompletableFuture.completedFuture(java.util.Collections.emptyList());

                            var generator = debugShapeManager.getGenerator(type);
                            if (generator == null) return java.util.concurrent.CompletableFuture.completedFuture(java.util.Collections.emptyList());

                            // Оборачиваем результат
                            return java.util.concurrent.CompletableFuture.completedFuture(
                                    java.util.List.of(org.incendo.cloud.suggestion.Suggestion.suggestion(generator.getUsage()))
                            );
                        })
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    if (!validateWorld(player)) return;

                    String type = ctx.get("type");
                    String id = ctx.get("id");
                    String paramsRaw = ctx.get("params");

                    DebugShapeGenerator generator = debugShapeManager.getGenerator(type);
                    if (generator == null) {
                        send(player, MessageKey.SHAPE_NOT_FOUND, "{shape}", type);
                        return;
                    }

                    try {
                        double[] params = Arrays.stream(paramsRaw.split(" "))
                                .mapToDouble(Double::parseDouble)
                                .toArray();

                        if (params.length < generator.getParamCount()) {
                            send(player, MessageKey.SHAPE_USAGE, "{type}", type, "{params}", generator.getUsage());
                            return;
                        }

                        int count = bodyFactory.spawnShape(player, generator, id, params);
                        send(player, MessageKey.SHAPE_SPAWN_SUCCESS, "{count}", String.valueOf(count), "{shape}", type);

                    } catch (NumberFormatException e) {
                        send(player, MessageKey.SHAPE_INVALID_PARAMS);
                    } catch (Exception e) {
                        send(player, MessageKey.ERROR_OCCURRED, "{error}", e.getMessage());
                    }
                })
        );
    }
}