package com.ladakx.inertia.commands;

import co.aikar.commands.annotation.*;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.files.config.message.MessageKey;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.debug.shapes.ShapeGenerator;
import com.ladakx.inertia.physics.debug.shapes.ShapeManager;
import com.ladakx.inertia.physics.service.PhysicsSpawnService;
import com.ladakx.inertia.tools.Tool;
import com.ladakx.inertia.tools.ToolManager;
import com.ladakx.inertia.tools.impl.ChainTool;
import com.ladakx.inertia.tools.impl.RagdollTool;
import com.ladakx.inertia.tools.impl.ShapeTool;
import com.ladakx.inertia.tools.impl.TNTSpawnTool;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

@CommandAlias("inertia")
@Description("Main inertia plugin command.")
public class Commands extends BaseCommand {

    private final PhysicsSpawnService spawnService;
    private final ShapeManager shapeManager;

    public Commands(InertiaPlugin plugin) {
        this.spawnService = new PhysicsSpawnService(plugin);
        this.shapeManager = new ShapeManager();
    }

    // --- System Commands ---

    @Subcommand("reload")
    @CommandPermission("inertia.commands.reload")
    @Description("Reload the configuration.")
    public void onReloadCommand(CommandSender sender) {
        if (checkPermission(sender, "inertia.commands.reload", true)) {
            InertiaPlugin.getInstance().reload();
            send(sender, MessageKey.RELOAD_PLUGIN);
        }
    }

    @Default
    @CatchUnknown
    @Subcommand("help")
    @Description("Help command.")
    public void onHelp(CommandSender sender) {
        if (checkPermission(sender, "inertia.commands.help.admin", false)) {
            send(sender, MessageKey.HELP_COMMAND_ADMIN);
        } else {
            send(sender, MessageKey.HELP_COMMAND);
        }
    }

    @Subcommand("clear")
    @CommandPermission("inertia.commands.clear")
    @Description("Clear all physics bodies in the current world.")
    public void onClearCommand(Player player) {
        if (!checkPermission(player, "inertia.commands.clear", true)) return;

        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());
        if (space == null) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return;
        }

        int countBefore = space.getObjects().size();
        space.removeAllObjects();
        send(player, MessageKey.CLEAR_SUCCESS, "{count}", String.valueOf(countBefore));
    }

    // --- Spawn Commands ---

    @Subcommand("spawn body")
    @CommandPermission("inertia.commands.spawn")
    @CommandCompletion("@bodies")
    @Description("Spawn a single physics body.")
    public void onSpawnBody(Player player, String bodyId) {
        if (!validateWorld(player)) return;

        if (bodyId.startsWith("chains.")) {
            onSpawnChain(player, bodyId, 10);
            return;
        } else if (bodyId.startsWith("ragdolls.")) {
            onSpawnRagdoll(player, bodyId);
            return;
        }

        try {
            if (!validateBodyExists(player, bodyId)) return;

            if (spawnService.spawnBody(player.getLocation(), bodyId)) {
                send(player, MessageKey.SPAWN_SUCCESS, "{id}", bodyId);
            } else {
                send(player, MessageKey.ERROR_OCCURRED, "{error}", "Failed to spawn body (internal error)");
            }
        } catch (Exception e) {
            handleException(player, e);
        }
    }

    @Subcommand("spawn chain")
    @CommandPermission("inertia.commands.spawn")
    @CommandCompletion("@bodies 10|20|30")
    @Description("Spawn a physics chain.")
    public void onSpawnChain(Player player, String bodyId, @Default("10") int size) {
        if (!validateWorld(player)) return;
        try {
            if (!validateBodyExists(player, bodyId)) return;

            spawnService.spawnChain(player, bodyId, size);
            send(player, MessageKey.CHAIN_SPAWN_SUCCESS, "{size}", String.valueOf(size));
        } catch (Exception e) {
            handleException(player, e);
        }
    }

    @Subcommand("spawn ragdoll")
    @CommandPermission("inertia.commands.spawn")
    @CommandCompletion("@bodies")
    @Description("Spawn a ragdoll.")
    public void onSpawnRagdoll(Player player, String bodyId) {
        if (!validateWorld(player)) return;
        try {
            if (!validateBodyExists(player, bodyId)) return;

            spawnService.spawnRagdoll(player, bodyId);
            send(player, MessageKey.RAGDOLL_SPAWN_SUCCESS, "{id}", bodyId);
        } catch (Exception e) {
            handleException(player, e);
        }
    }

    @Subcommand("spawn-shape")
    @CommandPermission("inertia.commands.spawn")
    @CommandCompletion("@shapes @bodies")
    @Syntax("<type> <params...> <body>")
    @Description("Spawn multiple blocks in a shape.")
    public void onSpawnShape(Player player, String shapeType, String[] args) {
        if (!validateWorld(player)) return;

        ShapeGenerator generator = shapeManager.getGenerator(shapeType);
        if (generator == null) {
            send(player, MessageKey.SHAPE_NOT_FOUND, "{shape}", shapeType);
            send(player, MessageKey.SHAPE_LIST_AVAILABLE, "{shapes}", String.join(", ", shapeManager.getAvailableShapes()));
            return;
        }

        // Expected params + 1 for bodyId
        int requiredArgs = generator.getParamCount() + 1;

        if (args.length < requiredArgs) {
            send(player, MessageKey.SHAPE_USAGE, "{type}", shapeType, "{params}", generator.getUsage());
            return;
        }

        String bodyId = args[args.length - 1]; // Last argument is always bodyId
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

        try {
            int count = spawnService.spawnShape(player, generator, bodyId, params);
            send(player, MessageKey.SHAPE_SPAWN_SUCCESS, "{count}", String.valueOf(count), "{shape}", shapeType);
        } catch (Exception e) {
            handleException(player, e);
        }
    }

    // --- Tools Commands ---

    @Subcommand("tool chain")
    @CommandPermission("inertia.commands.tool")
    @CommandCompletion("@bodies")
    public void onToolChain(Player player, String bodyId) {
        giveTool(player, "chain_tool", bodyId, ChainTool.class);
    }

    @Subcommand("tool ragdoll")
    @CommandPermission("inertia.commands.tool")
    @CommandCompletion("@bodies")
    public void onToolRagdoll(Player player, String bodyId) {
        giveTool(player, "ragdoll_tool", bodyId, RagdollTool.class);
    }

    @Subcommand("tool shape")
    @CommandPermission("inertia.commands.tool")
    @CommandCompletion("@shapes @blocks")
    public void onToolShape(Player player, String shapeType, String[] args) {
        if (!checkPermission(player, "inertia.commands.tool", true)) return;

        ShapeGenerator generator = shapeManager.getGenerator(shapeType);
        if (generator == null) {
            send(player, MessageKey.SHAPE_NOT_FOUND, "{shape}", shapeType);
            return;
        }

        int requiredArgs = generator.getParamCount() + 1; // Params + BodyId
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

        ToolManager tm = ToolManager.getInstance();
        Tool tool = tm.getTool("shape_tool");
        if (tool instanceof ShapeTool st) {
            player.getInventory().addItem(st.getToolItem(shapeType, bodyId, params));
            send(player, MessageKey.TOOL_RECEIVED, "{tool}", "Shape Tool (" + shapeType + ")");
        }
    }

    @Subcommand("tool grabber")
    @CommandPermission("inertia.commands.tool")
    public void onToolGrabber(Player player) {
        giveTool(player, "grabber", null, null);
    }

    @Subcommand("tool welder")
    @CommandPermission("inertia.commands.tool")
    public void onToolWelder(Player player) {
        giveTool(player, "welder", null, null);
    }

    @Subcommand("tool remover")
    @CommandPermission("inertia.commands.tool")
    public void onToolRemover(Player player) {
        giveTool(player, "remover", null, null);
    }

    @Subcommand("spawn tnt")
    @CommandPermission("inertia.commands.spawn")
    @CommandCompletion("@bodies 10|20|50")
    @Description("Spawn a physics-based TNT.")
    public void onSpawnTNT(Player player, String bodyId, @Default("20") float force) {
        if (!validateWorld(player)) return;

        try {
            if (!validateBodyExists(player, bodyId)) return;

            // Spawn at feet
            Location loc = player.getLocation().add(0, 0.5, 0);
            spawnService.spawnTNT(loc, bodyId, force, null);

            send(player, MessageKey.TNT_SPAWNED, "{force}", String.valueOf(force));
        } catch (Exception e) {
            handleException(player, e);
        }
    }

    @Subcommand("tool tntspawner")
    @CommandPermission("inertia.commands.tool")
    @CommandCompletion("@bodies 10|20|50")
    @Description("Get a tool to throw or place physics TNT.")
    public void onToolTNT(Player player, String bodyId, @Default("20") float force) {
        if (!checkPermission(player, "inertia.commands.tool", true)) return;

        if (!validateBodyExists(player, bodyId)) return;

        ToolManager tm = ToolManager.getInstance();
        Tool tool = tm.getTool("tnt_spawner");

        if (tool instanceof TNTSpawnTool tntTool) {
            player.getInventory().addItem(tntTool.getToolItem(bodyId, force));
            send(player, MessageKey.TNT_TOOL_RECEIVED, "{body}", bodyId, "{force}", String.valueOf(force));
        } else {
            send(player, MessageKey.TOOL_NOT_FOUND);
        }
    }

    // --- Helpers ---

    private boolean validateWorld(Player player) {
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return false;
        }
        return true;
    }

    private boolean validateBodyExists(Player player, String bodyId) {
        PhysicsBodyRegistry registry = ConfigManager.getInstance().getPhysicsBodyRegistry();
        if (registry.find(bodyId).isEmpty()) {
            send(player, MessageKey.SPAWN_FAIL_INVALID_ID, "{id}", bodyId);
            return false;
        }
        return true;
    }

    private void giveTool(Player player, String toolId, String bodyId, Class<? extends Tool> toolClass) {
        if (!checkPermission(player, "inertia.commands.tool", true)) return;

        ToolManager tm = ToolManager.getInstance();
        Tool tool = tm.getTool(toolId);

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

    private void handleException(Player player, Exception e) {
        InertiaLogger.error("Error executing command for " + player.getName(), e);
        String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
        send(player, MessageKey.ERROR_OCCURRED, "{error}", msg);
    }

    private void send(CommandSender sender, MessageKey key, String... replacements) {
        ConfigManager.getInstance().getMessageManager().send(sender, key, replacements);
    }
}