package com.ladakx.inertia.features.commands.impl;

import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.commands.CloudModule;
import com.ladakx.inertia.features.commands.parsers.BodyIdParser;
import com.ladakx.inertia.features.tools.Tool;
import com.ladakx.inertia.features.tools.ToolRegistry;
import com.ladakx.inertia.features.tools.impl.ChainTool;
import com.ladakx.inertia.features.tools.impl.RagdollTool;
import com.ladakx.inertia.features.tools.impl.ShapeTool;
import com.ladakx.inertia.features.tools.impl.TNTSpawnTool;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeGenerator;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.util.Arrays;

public class ToolCommands extends CloudModule {

    private final ToolRegistry toolRegistry;
    private final DebugShapeManager debugShapeManager;

    public ToolCommands(CommandManager<CommandSender> manager, ConfigurationService config, ToolRegistry toolRegistry) {
        super(manager, config);
        this.toolRegistry = toolRegistry;
        this.debugShapeManager = new DebugShapeManager();
    }

    @Override
    public void register() {
        // Используем правильный тип: Command.Builder
        Command.Builder<CommandSender> toolRoot = rootBuilder()
                .literal("tool")
                .permission("inertia.commands.tool");

        // /inertia tool chain <id>
        manager.command(toolRoot
                .literal("chain")
                .required("id", BodyIdParser.bodyIdParser(config))
                .handler(ctx -> giveTool((Player) ctx.sender(), "chain_tool", ctx.get("id"), ChainTool.class))
        );

        // /inertia tool ragdoll <id>
        manager.command(toolRoot
                .literal("ragdoll")
                .required("id", BodyIdParser.bodyIdParser(config))
                .handler(ctx -> giveTool((Player) ctx.sender(), "ragdoll_tool", ctx.get("id"), RagdollTool.class))
        );

        // /inertia tool tntspawner <id> [force]
        manager.command(toolRoot
                .literal("tntspawner")
                .required("id", BodyIdParser.bodyIdParser(config))
                .optional("force", DoubleParser.doubleParser(0.1))
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    if (!validateWorld(player)) return;

                    String id = ctx.get("id");
                    float force = ctx.<Double>getOrDefault("force", 20.0).floatValue();

                    Tool tool = toolRegistry.getTool("tnt_spawner");
                    if (tool instanceof TNTSpawnTool tntTool) {
                        player.getInventory().addItem(tntTool.getToolItem(id, force));
                        send(player, MessageKey.TNT_TOOL_RECEIVED, "{body}", id, "{force}", String.valueOf(force));
                    } else {
                        send(player, MessageKey.TOOL_NOT_FOUND);
                    }
                })
        );

        // /inertia tool shape <type> <id> <params>
        manager.command(toolRoot
                .literal("shape")
                .required("type", StringParser.stringParser(),
                        SuggestionProvider.blockingStrings((c, i) -> debugShapeManager.getAvailableShapes()))
                .required("id", BodyIdParser.bodyIdParser(config))
                .required("params", StringParser.greedyStringParser(),
                        // ИСПРАВЛЕНИЕ: CompletableFuture
                        (ctx, input) -> {
                            String type = ctx.getOrDefault("type", null);
                            if (type == null) return java.util.concurrent.CompletableFuture.completedFuture(java.util.Collections.emptyList());
                            var generator = debugShapeManager.getGenerator(type);
                            if (generator == null) return java.util.concurrent.CompletableFuture.completedFuture(java.util.Collections.emptyList());

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
                        double[] params = Arrays.stream(paramsRaw.split(" ")).mapToDouble(Double::parseDouble).toArray();
                        if (params.length < generator.getParamCount()) {
                            send(player, MessageKey.SHAPE_USAGE, "{type}", type, "{params}", generator.getUsage());
                            return;
                        }

                        Tool tool = toolRegistry.getTool("shape_tool");
                        if (tool instanceof ShapeTool st) {
                            player.getInventory().addItem(st.getToolItem(type, id, params));
                            send(player, MessageKey.TOOL_RECEIVED, "{tool}", "Shape Tool (" + type + ")");
                        }
                    } catch (Exception e) {
                        send(player, MessageKey.SHAPE_INVALID_PARAMS);
                    }
                })
        );

        // Simple Tools
        registerSimpleTool(toolRoot, "grabber");
        registerSimpleTool(toolRoot, "welder");
        registerSimpleTool(toolRoot, "remover");
        registerSimpleTool(toolRoot, "static", "static_tool");
    }

    private void registerSimpleTool(Command.Builder<CommandSender> root, String commandName) {
        registerSimpleTool(root, commandName, commandName);
    }

    // Исправленная сигнатура метода: Command.Builder<CommandSender>
    private void registerSimpleTool(Command.Builder<CommandSender> root, String commandName, String toolId) {
        manager.command(root
                .literal(commandName)
                .handler(ctx -> {
                    if (validatePlayer(ctx.sender())) {
                        giveTool((Player) ctx.sender(), toolId, null, null);
                    }
                })
        );
    }

    private void giveTool(Player player, String toolId, String bodyId, Class<? extends Tool> toolClass) {
        if (!validateWorld(player)) return;

        Tool tool = toolRegistry.getTool(toolId);
        if (tool == null) {
            send(player, MessageKey.TOOL_NOT_FOUND);
            return;
        }

        boolean success = false;
        if (bodyId != null && toolClass != null && toolClass.isInstance(tool)) {
            if (tool instanceof ChainTool ct) {
                player.getInventory().addItem(ct.getToolItem(bodyId));
                success = true;
            } else if (tool instanceof RagdollTool rt) {
                player.getInventory().addItem(rt.getToolItem(bodyId));
                success = true;
            }
        } else {
            player.getInventory().addItem(tool.buildItem());
            success = true;
        }

        if (success) {
            send(player, MessageKey.TOOL_RECEIVED, "{tool}", toolId + (bodyId != null ? " (" + bodyId + ")" : ""));
        } else {
            send(player, MessageKey.TOOL_NOT_FOUND);
        }
    }
}