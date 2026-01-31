package com.ladakx.inertia.features.commands.impl;

import co.aikar.commands.annotation.*;
import co.aikar.commands.annotation.Optional;
import com.ladakx.inertia.common.PhysicsGraphUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.pdc.InertiaPDCKeys;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.commands.BaseCommand;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.body.impl.DisplayedPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

@CommandAlias("inertia")
public class ManageCommands extends BaseCommand {

    private final PhysicsWorldRegistry physicsWorldRegistry;

    public ManageCommands(ConfigurationService configurationService, PhysicsWorldRegistry physicsWorldRegistry) {
        super(configurationService);
        this.physicsWorldRegistry = physicsWorldRegistry;
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

        List<AbstractPhysicsBody> toRemove = new ArrayList<>();

        for (AbstractPhysicsBody obj : space.getObjects()) {
            if (radiusSq > 0) {
                if (obj.getLocation().distanceSquared(playerLoc) > radiusSq) continue;
            }

            if (targetType != null && obj.getType() != targetType) continue;
            if (targetId != null && !obj.getBodyId().equalsIgnoreCase(targetId)) continue;

            toRemove.add(obj);
        }

        for (AbstractPhysicsBody obj : toRemove) {
            try {
                obj.destroy();
                countRemoved++;
            } catch (Exception e) {
                InertiaLogger.error("Failed to clear object during command execution", e);
            }
        }

        if (countRemoved == 0) {
            send(player, MessageKey.CLEAR_NO_MATCH);
        } else if (radius != null) {
            send(player, MessageKey.CLEAR_SUCCESS_RADIUS, "{count}", String.valueOf(countRemoved), "{radius}", String.valueOf(radius));
        } else {
            send(player, MessageKey.CLEAR_SUCCESS, "{count}", String.valueOf(countRemoved));
        }
    }

    @Subcommand("entity clear")
    @CommandPermission("inertia.commands.clear")
    @CommandCompletion("10|20|50|100 true|false true|false @clear_filter")
    @Syntax("<radius> <active> <static> [type|id]")
    @Description("Clear entities. active=false removes only orphaned visuals. static=true removes frozen entities.")
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
        List<Entity> entities = player.getNearbyEntities(radius, radius, radius);
        Set<AbstractPhysicsBody> bodiesToDestroy = new HashSet<>();
        List<Entity> entitiesToRemove = new ArrayList<>();

        for (Entity entity : entities) {
            var pdc = entity.getPersistentDataContainer();

            if (!pdc.has(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ID, PersistentDataType.STRING)) {
                continue;
            }

            boolean isStatic = false;
            if (pdc.has(InertiaPDCKeys.INERTIA_ENTITY_STATIC, PersistentDataType.STRING)) {
                String staticVal = pdc.get(InertiaPDCKeys.INERTIA_ENTITY_STATIC, PersistentDataType.STRING);
                isStatic = "true".equalsIgnoreCase(staticVal);
            }

            if (isStatic && !removeStatic) continue;

            String bodyId = pdc.get(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ID, PersistentDataType.STRING);
            if (targetId != null && bodyId != null && !bodyId.equalsIgnoreCase(targetId)) continue;

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

            AbstractPhysicsBody body = null;
            if (pdc.has(InertiaPDCKeys.INERTIA_PHYSICS_BODY_UUID, PersistentDataType.STRING)) {
                try {
                    String uuidStr = pdc.get(InertiaPDCKeys.INERTIA_PHYSICS_BODY_UUID, PersistentDataType.STRING);
                    assert uuidStr != null;
                    UUID uuid = UUID.fromString(uuidStr);
                    body = space.getObjectByUuid(uuid);
                } catch (Exception ignored) {}
            }

            if (active) {
                if (body != null) bodiesToDestroy.add(body);
                else entitiesToRemove.add(entity);
            } else {
                if (body == null) entitiesToRemove.add(entity);
            }
        }

        for (AbstractPhysicsBody body : bodiesToDestroy) {
            body.destroy();
            removedCount++;
        }

        for (Entity entity : entitiesToRemove) {
            entity.remove();
            removedCount++;
        }

        send(player, MessageKey.CLEAR_SUCCESS, "{count}", String.valueOf(removedCount));
    }

    @Subcommand("entity static")
    @CommandPermission("inertia.commands.static")
    @CommandCompletion("10|20|50 @clear_filter")
    @Syntax("<radius> [type|id]")
    @Description("Freeze valid physics entities in radius.")
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
        List<Entity> entities = player.getNearbyEntities(radius, radius, radius);
        Set<AbstractPhysicsBody> hitBodies = new HashSet<>();

        for (Entity entity : entities) {
            var pdc = entity.getPersistentDataContainer();

            if (!pdc.has(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ID, PersistentDataType.STRING)) continue;
            if (pdc.has(InertiaPDCKeys.INERTIA_ENTITY_STATIC, PersistentDataType.STRING)) continue;

            String bodyId = pdc.get(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ID, PersistentDataType.STRING);
            if (targetId != null && bodyId != null && !bodyId.equalsIgnoreCase(targetId)) continue;

            if (targetType != null) {
                var modelOpt = configurationService.getPhysicsBodyRegistry().find(bodyId);
                if (modelOpt.isPresent() && modelOpt.get().bodyDefinition().type() != targetType) continue;
            }

            if (pdc.has(InertiaPDCKeys.INERTIA_PHYSICS_BODY_UUID, PersistentDataType.STRING)) {
                try {
                    String uuidStr = pdc.get(InertiaPDCKeys.INERTIA_PHYSICS_BODY_UUID, PersistentDataType.STRING);
                    assert uuidStr != null;
                    UUID uuid = UUID.fromString(uuidStr);
                    AbstractPhysicsBody body = space.getObjectByUuid(uuid);
                    if (body != null && body.isValid()) hitBodies.add(body);
                } catch (Exception ignored) {}
            }
        }

        Set<AbstractPhysicsBody> processedBodies = new HashSet<>();

        for (AbstractPhysicsBody hit : hitBodies) {
            if (processedBodies.contains(hit)) continue;

            Set<AbstractPhysicsBody> cluster = PhysicsGraphUtils.collectConnectedBodies(space, hit);
            UUID clusterId = UUID.randomUUID();

            for (AbstractPhysicsBody body : cluster) {
                if (!processedBodies.add(body)) continue;

                try {
                    if (body instanceof DisplayedPhysicsBody displayedBody) {
                        displayedBody.freeze(clusterId);
                        frozenCount++;
                    } else {
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
}