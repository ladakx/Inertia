package com.ladakx.inertia.features.commands.impl;

import co.aikar.commands.annotation.*;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.commands.BaseCommand;
import com.ladakx.inertia.features.tools.Tool;
import com.ladakx.inertia.features.tools.ToolRegistry;
import com.ladakx.inertia.features.tools.impl.ChainTool;
import com.ladakx.inertia.features.tools.impl.RagdollTool;
import com.ladakx.inertia.features.tools.impl.ShapeTool;
import com.ladakx.inertia.features.tools.impl.TNTSpawnTool;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeGenerator;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeManager;
import org.bukkit.entity.Player;

import java.util.Arrays;

@CommandAlias("inertia")
@Subcommand("tool")
public class ToolCommands extends BaseCommand {

    private final ToolRegistry toolRegistry;
    private final DebugShapeManager debugShapeManager;

    public ToolCommands(ConfigurationService configurationService, ToolRegistry toolRegistry) {
        super(configurationService);
        this.toolRegistry = toolRegistry;
        this.debugShapeManager = new DebugShapeManager();
    }

    @Subcommand("chain")
    @CommandPermission("inertia.commands.tool")
    @CommandCompletion("@bodies")
    public void onToolChain(Player player, String bodyId) {
        giveTool(player, "chain_tool", bodyId, ChainTool.class);
    }

    @Subcommand("ragdoll")
    @CommandPermission("inertia.commands.tool")
    @CommandCompletion("@bodies")
    public void onToolRagdoll(Player player, String bodyId) {
        giveTool(player, "ragdoll_tool", bodyId, RagdollTool.class);
    }

    @Subcommand("shape")
    @CommandPermission("inertia.commands.tool")
    @CommandCompletion("@shapes @blocks")
    public void onToolShape(Player player, String shapeType, String[] args) {
        if (!checkPermission(player, "inertia.commands.tool", true)) return;

        DebugShapeGenerator generator = debugShapeManager.getGenerator(shapeType);
        if (generator == null) {
            send(player, MessageKey.SHAPE_NOT_FOUND, "{shape}", shapeType);
            return;
        }

        int requiredArgs = generator.getParamCount() + 1;
        if (args.length < requiredArgs) {
            send(player, MessageKey.SHAPE_USAGE, "{type}", shapeType, "{params}", generator.getUsage());
            return;
        }

        String bodyId = args[args.length - 1];
        if (!validateBodyExists(player, bodyId)) return;

        double[] params = new double[args.length - 1];
        try {
            for (int i = 0; i < args.length - 1; i++) {
                params[i] = Double.parseDouble(args[i]);
            }
        } catch (NumberFormatException e) {
            send(player, MessageKey.SHAPE_INVALID_PARAMS);
            return;
        }

        Tool tool = toolRegistry.getTool("shape_tool");
        if (tool instanceof ShapeTool st) {
            player.getInventory().addItem(st.getToolItem(shapeType, bodyId, params));
            send(player, MessageKey.TOOL_RECEIVED, "{tool}", "Shape Tool (" + shapeType + ")");
        }
    }

    @Subcommand("tntspawner")
    @CommandPermission("inertia.commands.tool")
    @CommandCompletion("@bodies 10|20|50")
    public void onToolTNT(Player player, String bodyId, @Default("20") float force) {
        if (!checkPermission(player, "inertia.commands.tool", true)) return;
        if (!validateBodyExists(player, bodyId)) return;

        Tool tool = toolRegistry.getTool("tnt_spawner");
        if (tool instanceof TNTSpawnTool tntTool) {
            player.getInventory().addItem(tntTool.getToolItem(bodyId, force));
            send(player, MessageKey.TNT_TOOL_RECEIVED, "{body}", bodyId, "{force}", String.valueOf(force));
        } else {
            send(player, MessageKey.TOOL_NOT_FOUND);
        }
    }

    @Subcommand("grabber")
    @CommandPermission("inertia.commands.tool")
    public void onToolGrabber(Player player) {
        giveTool(player, "grabber", null, null);
    }

    @Subcommand("welder")
    @CommandPermission("inertia.commands.tool")
    public void onToolWelder(Player player) {
        giveTool(player, "welder", null, null);
    }

    @Subcommand("remover")
    @CommandPermission("inertia.commands.tool")
    public void onToolRemover(Player player) {
        giveTool(player, "remover", null, null);
    }

    @Subcommand("static")
    @CommandPermission("inertia.commands.tool")
    public void onToolStatic(Player player) {
        giveTool(player, "static_tool", null, null);
    }

    private void giveTool(Player player, String toolId, String bodyId, Class<? extends Tool> toolClass) {
        if (!checkPermission(player, "inertia.commands.tool", true)) return;

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