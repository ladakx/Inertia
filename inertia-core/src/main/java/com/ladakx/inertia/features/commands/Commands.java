package com.ladakx.inertia.features.commands;

import co.aikar.commands.annotation.*;
import com.ladakx.inertia.common.PhysicsGraphUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.pdc.InertiaPDCKeys;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.body.impl.DisplayedPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeGenerator;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeManager;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.features.tools.Tool;
import com.ladakx.inertia.features.tools.ToolRegistry;
import com.ladakx.inertia.features.tools.impl.ChainTool;
import com.ladakx.inertia.features.tools.impl.RagdollTool;
import com.ladakx.inertia.features.tools.impl.ShapeTool;
import com.ladakx.inertia.features.tools.impl.TNTSpawnTool;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@CommandAlias("inertia")
@Description("Main inertia plugin command.")
public class Commands extends BaseCommand {

    private final BodyFactory spawnService;
    private final DebugShapeManager debugShapeManager;
    private final ConfigurationService configurationService;
    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final ToolRegistry toolRegistry;

    public Commands(InertiaPlugin plugin,
                    ConfigurationService configurationService,
                    PhysicsWorldRegistry physicsWorldRegistry,
                    ToolRegistry toolRegistry,
                    BodyFactory bodyFactory) {
        super(configurationService);

        this.configurationService = configurationService;
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.toolRegistry = toolRegistry;
        this.spawnService = bodyFactory; // Use injected service
        this.debugShapeManager = new DebugShapeManager();
    }

    // --- System Commands ---

    @Subcommand("reload")
    @CommandPermission("inertia.commands.reload")
    public void onReloadCommand(CommandSender sender) {
        if (checkPermission(sender, "inertia.commands.reload", true)) {
            InertiaPlugin.getInstance().reload(); // Plugin main reload method
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
    @CommandCompletion("10|20|50|100 @clear_filter")
    @Syntax("[radius] [type|id]")
    @Description("Clear physics bodies with optional radius and type filters.")
    public void onClearCommand(Player player, @Optional Integer radius, @Optional String filter) {
        if (!checkPermission(player, "inertia.commands.clear", true)) return;

        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return;
        }

        PhysicsBodyType targetType = null;
        String targetId = null;

        if (filter != null) {
            try {
                targetType = PhysicsBodyType.valueOf(filter.toUpperCase());
            } catch (IllegalArgumentException e) {
                targetId = filter;
            }
        }

        int countRemoved = 0;
        Location playerLoc = player.getLocation();
        double radiusSq = (radius != null && radius > 0) ? radius * radius : -1;

        List<com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody> toRemove = new java.util.ArrayList<>();

        for (var obj : space.getObjects()) {
            if (radiusSq > 0) {
                if (obj.getLocation().distanceSquared(playerLoc) > radiusSq) continue;
            }

            if (targetType != null && obj.getType() != targetType) continue;
            if (targetId != null && !obj.getBodyId().equalsIgnoreCase(targetId)) continue;

            toRemove.add(obj);
        }

        for (var obj : toRemove) {
            try {
                obj.destroy();
                countRemoved++;
            } catch (Exception e) {
                InertiaLogger.error("Failed to clear object during command execution", e);
            }
        }

        // Выбираем правильное сообщение
        if (countRemoved == 0) {
            send(player, MessageKey.CLEAR_NO_MATCH);
        } else if (radius != null && filter != null) {
            send(player, MessageKey.CLEAR_SUCCESS_COMBO,
                    "{count}", String.valueOf(countRemoved),
                    "{radius}", String.valueOf(radius),
                    "{filter}", filter);
        } else if (radius != null) {
            send(player, MessageKey.CLEAR_SUCCESS_RADIUS,
                    "{count}", String.valueOf(countRemoved),
                    "{radius}", String.valueOf(radius));
        } else if (filter != null) {
            send(player, MessageKey.CLEAR_SUCCESS_FILTER,
                    "{count}", String.valueOf(countRemoved),
                    "{filter}", filter);
        } else {
            send(player, MessageKey.CLEAR_SUCCESS, "{count}", String.valueOf(countRemoved));
        }
    }

    @Subcommand("entity clear")
    @CommandPermission("inertia.commands.clear")
    @CommandCompletion("10|20|50|100 true|false true|false @clear_filter")
    @Syntax("<radius> <active> <static> [type|id]")
    @Description("Clear entities linked to Inertia. active=false removes only orphaned visuals. static=true removes frozen entities.")
    public void onEntityClear(Player player, int radius, boolean active, boolean removeStatic, @Optional String filter) {
        if (!checkPermission(player, "inertia.commands.clear", true)) return;

        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return;
        }

        PhysicsBodyType targetType = null;
        String targetId = null;

        if (filter != null) {
            try {
                targetType = PhysicsBodyType.valueOf(filter.toUpperCase());
            } catch (IllegalArgumentException e) {
                targetId = filter;
            }
        }

        int removedCount = 0;

        List<org.bukkit.entity.Entity> entities = player.getNearbyEntities(radius, radius, radius);

        java.util.Set<com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody> bodiesToDestroy = new java.util.HashSet<>();
        List<org.bukkit.entity.Entity> entitiesToRemove = new java.util.ArrayList<>();

        for (org.bukkit.entity.Entity entity : entities) {
            var pdc = entity.getPersistentDataContainer();

            if (!pdc.has(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ID, org.bukkit.persistence.PersistentDataType.STRING)) {
                continue;
            }

            boolean isStatic = false;
            if (pdc.has(InertiaPDCKeys.INERTIA_ENTITY_STATIC, org.bukkit.persistence.PersistentDataType.STRING)) {
                String staticVal = pdc.get(InertiaPDCKeys.INERTIA_ENTITY_STATIC, org.bukkit.persistence.PersistentDataType.STRING);
                isStatic = "true".equalsIgnoreCase(staticVal);
            }

            if (isStatic && !removeStatic) {
                continue;
            }

            String bodyId = pdc.get(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ID, org.bukkit.persistence.PersistentDataType.STRING);
            if (targetId != null) {
                assert bodyId != null;
                if (!bodyId.equalsIgnoreCase(targetId)) continue;
            }

            if (targetType != null) {
                var modelOpt = configurationService.getPhysicsBodyRegistry().find(bodyId);
                if (modelOpt.isPresent() && modelOpt.get().bodyDefinition().type() != targetType) {
                    continue;
                }
            }

            if (isStatic) {
                entitiesToRemove.add(entity);
                continue;
            }

            com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody body = null;
            if (pdc.has(InertiaPDCKeys.INERTIA_PHYSICS_BODY_UUID, org.bukkit.persistence.PersistentDataType.STRING)) {
                try {
                    String uuidStr = pdc.get(InertiaPDCKeys.INERTIA_PHYSICS_BODY_UUID, org.bukkit.persistence.PersistentDataType.STRING);
                    assert uuidStr != null;
                    java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                    body = space.getObjectByUuid(uuid);
                } catch (Exception ignored) {}
            }

            if (active) {
                if (body != null) {
                    bodiesToDestroy.add(body);
                } else {
                    entitiesToRemove.add(entity);
                }
            } else {
                if (body == null) {
                    entitiesToRemove.add(entity);
                }
            }
        }

        for (var body : bodiesToDestroy) {
            body.destroy();
            removedCount++;
        }

        for (var entity : entitiesToRemove) {
            entity.remove();
            removedCount++;
        }

        send(player, MessageKey.CLEAR_SUCCESS, "{count}", String.valueOf(removedCount));
    }

    @Subcommand("entity static")
    @CommandPermission("inertia.commands.static")
    @CommandCompletion("10|20|50 @clear_filter")
    @Syntax("<radius> [type|id]")
    @Description("Freeze valid physics entities in radius, making them static decorations.")
    public void onEntityStatic(Player player, int radius, @Optional String filter) {
        if (!checkPermission(player, "inertia.commands.static", true)) return;

        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return;
        }

        PhysicsBodyType targetType = null;
        String targetId = null;

        if (filter != null) {
            try {
                targetType = PhysicsBodyType.valueOf(filter.toUpperCase());
            } catch (IllegalArgumentException e) {
                targetId = filter;
            }
        }

        int frozenCount = 0;
        List<org.bukkit.entity.Entity> entities = player.getNearbyEntities(radius, radius, radius);

        // 1. Собираем "корневые" тела, которые попали в радиус
        java.util.Set<AbstractPhysicsBody> hitBodies = new java.util.HashSet<>();

        for (org.bukkit.entity.Entity entity : entities) {
            var pdc = entity.getPersistentDataContainer();

            if (!pdc.has(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ID, org.bukkit.persistence.PersistentDataType.STRING)) {
                continue;
            }

            // Пропускаем уже статичные
            if (pdc.has(InertiaPDCKeys.INERTIA_ENTITY_STATIC, org.bukkit.persistence.PersistentDataType.STRING)) {
                continue;
            }

            String bodyId = pdc.get(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ID, org.bukkit.persistence.PersistentDataType.STRING);

            // Фильтрация
            if (targetId != null && bodyId != null && !bodyId.equalsIgnoreCase(targetId)) {
                continue;
            }

            if (targetType != null) {
                var modelOpt = configurationService.getPhysicsBodyRegistry().find(bodyId);
                if (modelOpt.isPresent() && modelOpt.get().bodyDefinition().type() != targetType) {
                    continue;
                }
            }

            if (pdc.has(InertiaPDCKeys.INERTIA_PHYSICS_BODY_UUID, org.bukkit.persistence.PersistentDataType.STRING)) {
                try {
                    String uuidStr = pdc.get(InertiaPDCKeys.INERTIA_PHYSICS_BODY_UUID, org.bukkit.persistence.PersistentDataType.STRING);
                    assert uuidStr != null;
                    java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                    AbstractPhysicsBody body = space.getObjectByUuid(uuid);
                    if (body != null && body.isValid()) {
                        hitBodies.add(body);
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. Обрабатываем группы.
        // Используем Set для отслеживания уже обработанных тел, чтобы не замораживать одну цепь дважды,
        // если в радиус попало несколько её звеньев.
        java.util.Set<AbstractPhysicsBody> processedBodies = new java.util.HashSet<>();

        for (AbstractPhysicsBody hit : hitBodies) {
            if (processedBodies.contains(hit)) continue;

            // Находим всю семью (цепь/рэгдолл)
            java.util.Set<AbstractPhysicsBody> cluster = PhysicsGraphUtils.collectConnectedBodies(space, hit);

            // Генерируем УНИКАЛЬНЫЙ ID для этой конкретной группы
            java.util.UUID clusterId = java.util.UUID.randomUUID();

            for (AbstractPhysicsBody body : cluster) {
                // Если мы уже обработали это тело в рамках другого hit (маловероятно при корректной физике, но возможно), пропускаем
                if (!processedBodies.add(body)) continue;

                try {
                    if (body instanceof com.ladakx.inertia.physics.body.impl.DisplayedPhysicsBody displayedBody) {
                        displayedBody.freeze(clusterId);
                        frozenCount++;
                    } else {
                        // Невидимые технические тела (если есть) просто удаляем
                        body.destroy();
                    }
                } catch (Exception e) {
                    InertiaLogger.error("Error freezing body via command", e);
                }
            }
        }

        if (frozenCount > 0) {
            send(player, MessageKey.STATIC_SUCCESS, "{count}", String.valueOf(frozenCount));
        } else {
            send(player, MessageKey.STATIC_NO_MATCH);
        }
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

        DebugShapeGenerator generator = debugShapeManager.getGenerator(shapeType);
        if (generator == null) {
            send(player, MessageKey.SHAPE_NOT_FOUND, "{shape}", shapeType);
            send(player, MessageKey.SHAPE_LIST_AVAILABLE, "{shapes}", String.join(", ", debugShapeManager.getAvailableShapes()));
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

    @Subcommand("tool static")
    @CommandPermission("inertia.commands.tool")
    public void onToolStatic(Player player) {
        giveTool(player, "static_tool", null, null);
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

        DebugShapeGenerator generator = debugShapeManager.getGenerator(shapeType);
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

        Tool tool = toolRegistry.getTool("shape_tool");
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

        Tool tool = toolRegistry.getTool("tnt_spawner");

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
        PhysicsBodyRegistry registry = configurationService.getPhysicsBodyRegistry();
        if (registry.find(bodyId).isEmpty()) {
            send(player, MessageKey.SPAWN_FAIL_INVALID_ID, "{id}", bodyId);
            return false;
        }
        return true;
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

    private void handleException(Player player, Exception e) {
        InertiaLogger.error("Error executing command for " + player.getName(), e);
        String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
        send(player, MessageKey.ERROR_OCCURRED, "{error}", msg);
    }

    // Override BaseCommand helper to use injected config
    private void send(CommandSender sender, MessageKey key, String... replacements) {
        configurationService.getMessageManager().send(sender, key, replacements);
    }

    // Override checkPermission logic if needed, or simply use instance methods
    public boolean checkPermission(CommandSender sender, String permission, boolean showMSG) {
        if (sender.hasPermission(permission)) return true;
        if (showMSG) send(sender, MessageKey.NO_PERMISSIONS);
        return false;
    }
}