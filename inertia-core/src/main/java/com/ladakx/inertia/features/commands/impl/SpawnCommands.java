package com.ladakx.inertia.features.commands.impl;

import co.aikar.commands.annotation.*;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.commands.BaseCommand;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeGenerator;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@CommandAlias("inertia")
@Subcommand("spawn") // Все команды здесь будут начинаться с /inertia spawn
public class SpawnCommands extends BaseCommand {

    private final BodyFactory bodyFactory;
    private final DebugShapeManager debugShapeManager;

    public SpawnCommands(ConfigurationService configurationService, BodyFactory bodyFactory) {
        super(configurationService);
        this.bodyFactory = bodyFactory;
        this.debugShapeManager = new DebugShapeManager();
    }

    @Subcommand("body")
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

            if (bodyFactory.spawnBody(player.getLocation(), bodyId)) {
                send(player, MessageKey.SPAWN_SUCCESS, "{id}", bodyId);
            } else {
                com.ladakx.inertia.physics.world.PhysicsWorld space =
                        configurationService.getWorldsConfig().getWorldSettings(player.getWorld().getName()) != null
                                ? com.ladakx.inertia.core.InertiaPlugin.getInstance().getSpaceManager().getSpace(player.getWorld()) : null;

                if (space != null && !space.canSpawnBodies(1)) {
                    send(player, MessageKey.SPAWN_LIMIT_REACHED, "{limit}", String.valueOf(space.getSettings().maxBodies()));
                } else {
                    send(player, MessageKey.ERROR_OCCURRED, "{error}", "Failed to spawn body (unknown error)");
                }
            }
        } catch (Exception e) {
            handleException(player, e);
        }
    }

    @Subcommand("chain")
    @CommandPermission("inertia.commands.spawn")
    @CommandCompletion("@bodies 10|20|30")
    @Description("Spawn a physics chain.")
    public void onSpawnChain(Player player, String bodyId, @Default("10") int size) {
        if (!validateWorld(player)) return;
        try {
            if (!validateBodyExists(player, bodyId)) return;

            bodyFactory.spawnChain(player, bodyId, size);
            send(player, MessageKey.CHAIN_SPAWN_SUCCESS, "{size}", String.valueOf(size));
        } catch (Exception e) {
            handleException(player, e);
        }
    }

    @Subcommand("ragdoll")
    @CommandPermission("inertia.commands.spawn")
    @CommandCompletion("@bodies")
    @Description("Spawn a ragdoll.")
    public void onSpawnRagdoll(Player player, String bodyId) {
        if (!validateWorld(player)) return;
        try {
            if (!validateBodyExists(player, bodyId)) return;

            bodyFactory.spawnRagdoll(player, bodyId);
            send(player, MessageKey.RAGDOLL_SPAWN_SUCCESS, "{id}", bodyId);
        } catch (Exception e) {
            handleException(player, e);
        }
    }

    @Subcommand("tnt")
    @CommandPermission("inertia.commands.spawn")
    @CommandCompletion("@bodies 10|20|50")
    @Description("Spawn a physics-based TNT.")
    public void onSpawnTNT(Player player, String bodyId, @Default("20") float force) {
        if (!validateWorld(player)) return;

        try {
            if (!validateBodyExists(player, bodyId)) return;

            Location loc = player.getLocation().add(0, 0.5, 0);
            bodyFactory.spawnTNT(loc, bodyId, force, null);

            send(player, MessageKey.TNT_SPAWNED, "{force}", String.valueOf(force));
        } catch (Exception e) {
            handleException(player, e);
        }
    }

    // Примечание: spawn-shape был в корневом spawn, но логичнее его перенести сюда.
    // ACF позволяет регистрировать alias, если нужно сохранить старый синтаксис.
    @Subcommand("shape")
    @CommandAlias("inertia spawn-shape") 
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

        try {
            int count = bodyFactory.spawnShape(player, generator, bodyId, params);
            send(player, MessageKey.SHAPE_SPAWN_SUCCESS, "{count}", String.valueOf(count), "{shape}", shapeType);
        } catch (Exception e) {
            handleException(player, e);
        }
    }
}