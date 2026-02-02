package com.ladakx.inertia.physics.factory;

import com.github.stephengold.joltjni.*;
import com.ladakx.inertia.api.events.PhysicsBodySpawnEvent;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.physics.body.impl.ChainPhysicsBody;
import com.ladakx.inertia.physics.body.impl.RagdollPhysicsBody;
import com.ladakx.inertia.physics.body.impl.TNTPhysicsBody;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.body.config.RagdollDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.*;

public class BodyFactory {

    private final InertiaPlugin plugin;
    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final JShapeFactory shapeFactory;
    private final ConfigurationService configurationService;

    public BodyFactory(InertiaPlugin plugin,
                       PhysicsWorldRegistry physicsWorldRegistry,
                       ConfigurationService configurationService,
                       JShapeFactory shapeFactory) {
        this.plugin = plugin;
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.configurationService = configurationService;
        this.shapeFactory = shapeFactory;
    }

    public boolean spawnBody(Location location, String bodyId) {
        if (location.getWorld() == null) return false;

        PhysicsWorld space = physicsWorldRegistry.getSpace(location.getWorld());
        if (space == null) return false;

        // Check bounds before API call to be explicit
        if (!space.isInsideWorld(location)) {
            return false;
        }

        if (!space.canSpawnBodies(1)) {
            return false;
        }

        InertiaPhysicsBody obj = InertiaAPI.get().createBody(location, bodyId);
        if (obj != null) {
            Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
                Bukkit.getPluginManager().callEvent(new PhysicsBodySpawnEvent(obj));
            });
            return true;
        }
        return false;
    }

    public void spawnChain(Player player, String bodyId, int size) {
        PhysicsBodyRegistry registry = configurationService.getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(bodyId);

        if (modelOpt.isEmpty()) {
            throw new IllegalArgumentException("Chain body not found: " + bodyId);
        }
        if (!(modelOpt.get().bodyDefinition() instanceof ChainBodyDefinition def)) {
            throw new IllegalArgumentException("Body ID '" + bodyId + "' is not of type CHAIN.");
        }

        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return;

        Location startLoc = getSpawnLocation(player, 3.0);
        if (!space.isInsideWorld(startLoc)) {
            configurationService.getMessageManager().send(player, MessageKey.ERROR_OCCURRED, "{error}", "Outside of world bounds!");
            return;
        }

        if (!space.canSpawnBodies(size)) {
            configurationService.getMessageManager().send(player, MessageKey.SPAWN_LIMIT_REACHED,
                    "{limit}", String.valueOf(space.getSettings().performance().maxBodies()));
            return;
        }

        Vector direction = new Vector(0, -1, 0);
        double spacing = def.creation().spacing();

        // Convert Start to Local Jolt
        RVec3 localStartPos = space.toJolt(startLoc);

        Quaternionf jomlQuat = new Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), new org.joml.Vector3f(0, -1, 0));
        Quat linkRotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        Body parentBody = null;
        GroupFilterTable groupFilter = new GroupFilterTable(size);

        for (int i = 0; i < size; i++) {
            if (i > 0) {
                groupFilter.disableCollision(i, i - 1);
            }

            // Calculate position in Jolt Space relative to start
            double offsetY = i * spacing * direction.getY();
            RVec3 pos = new RVec3(localStartPos.xx(), localStartPos.yy() + offsetY, localStartPos.zz());

            ChainPhysicsBody link = new ChainPhysicsBody(
                    space,
                    bodyId,
                    registry,
                    plugin.getRenderFactory(),
                    shapeFactory,
                    pos,
                    linkRotation,
                    parentBody,
                    groupFilter,
                    i,
                    size
            );

            parentBody = link.getBody();
            Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
                Bukkit.getPluginManager().callEvent(new PhysicsBodySpawnEvent(link));
            });
        }
    }

    public void spawnRagdoll(Player player, String bodyId) {
        PhysicsBodyRegistry registry = configurationService.getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(bodyId);

        if (modelOpt.isEmpty() || !(modelOpt.get().bodyDefinition() instanceof RagdollDefinition def)) {
            throw new IllegalArgumentException("Ragdoll body not found or invalid type: " + bodyId);
        }

        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return;

        Location spawnLoc = getSpawnLocation(player, 3.0);
        if (!space.isInsideWorld(spawnLoc)) {
            configurationService.getMessageManager().send(player, MessageKey.ERROR_OCCURRED, "{error}", "Outside of world bounds!");
            return;
        }

        int partsCount = def.parts().size();
        if (!space.canSpawnBodies(partsCount)) {
            configurationService.getMessageManager().send(player, MessageKey.SPAWN_LIMIT_REACHED,
                    "{limit}", String.valueOf(space.getSettings().performance().maxBodies()));
            return;
        }

        float yaw = -player.getLocation().getYaw() + 180;
        double yawRad = Math.toRadians(yaw);

        Quaternionf jomlQuat = new Quaternionf(new AxisAngle4f((float) yawRad, 0, 1, 0));
        Quat rotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        Map<String, Body> spawnedParts = new HashMap<>();
        int totalParts = def.parts().size();
        GroupFilterTable groupFilter = new GroupFilterTable(totalParts);

        Map<String, Integer> partIndices = new HashMap<>();
        int indexCounter = 0;
        for (String key : def.parts().keySet()) {
            partIndices.put(key, indexCounter++);
        }

        String rootPart = def.parts().entrySet().stream()
                .filter(e -> e.getValue().parentName() == null)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (rootPart == null) return;

        // Convert to Jolt Local
        RVec3 rootPos = space.toJolt(spawnLoc);

        createRagdollPart(space, bodyId, rootPart, rootPos, rotation, spawnedParts, groupFilter, partIndices.get(rootPart));
        spawnRagdollChildren(space, bodyId, rootPart, def, rotation, spawnedParts, yawRad, groupFilter, partIndices);
    }

    public int spawnShape(Player player, DebugShapeGenerator generator, String bodyId, double... params) {
        Location center = getSpawnLocation(player, 5.0);
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return 0;

        if (!space.isInsideWorld(center)) {
            configurationService.getMessageManager().send(player, MessageKey.ERROR_OCCURRED, "{error}", "Outside of world bounds!");
            return 0;
        }

        List<Vector> offsets = generator.generatePoints(center, params);
        if (!space.canSpawnBodies(offsets.size())) {
            configurationService.getMessageManager().send(player, MessageKey.SPAWN_LIMIT_REACHED,
                    "{limit}", String.valueOf(space.getSettings().performance().maxBodies()));
            return 0;
        }

        int count = 0;
        for (Vector offset : offsets) {
            Location loc = center.clone().add(offset);

            // Check individual body bounds
            if (!space.isInsideWorld(loc)) continue;

            InertiaPhysicsBody body = InertiaAPI.get().createBody(loc, bodyId);
            if (body != null) {
                Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
                    Bukkit.getPluginManager().callEvent(new PhysicsBodySpawnEvent(body));
                });
                count++;
            }
        }
        return count;
    }

    private void createRagdollPart(PhysicsWorld space, String bodyId, String partName, RVec3 pos, Quat rot,
                                   Map<String, Body> spawnedBodies, GroupFilterTable groupFilter, int partIndex) {
        RagdollPhysicsBody obj = new RagdollPhysicsBody(
                space, bodyId, partName, configurationService.getPhysicsBodyRegistry(),
                plugin.getRenderFactory(),
                shapeFactory,
                pos, rot, spawnedBodies,
                groupFilter, partIndex
        );
        spawnedBodies.put(partName, obj.getBody());
        Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
            Bukkit.getPluginManager().callEvent(new PhysicsBodySpawnEvent(obj));
        });
    }

    private void spawnRagdollChildren(PhysicsWorld space, String bodyId, String parentName,
                                      RagdollDefinition def, Quat rotation,
                                      Map<String, Body> spawnedBodies, double yawRad,
                                      GroupFilterTable groupFilter, Map<String, Integer> partIndices) {
        Body parentBody = spawnedBodies.get(parentName);
        if (parentBody == null) return;

        RVec3 parentPos = parentBody.getPosition();

        def.parts().forEach((partName, partDef) -> {
            if (parentName.equals(partDef.parentName())) {
                var joint = partDef.joint();
                if (joint != null) {
                    Vector pOffset = joint.pivotOnParent();
                    Vector cOffset = joint.pivotOnChild();

                    Vector3d parentPivotWorld = new Vector3d(pOffset.getX(), pOffset.getY(), pOffset.getZ()).rotateY(yawRad);
                    Vector3d childPivotWorld = new Vector3d(cOffset.getX(), cOffset.getY(), cOffset.getZ()).rotateY(yawRad);

                    double x = parentPos.xx() + parentPivotWorld.x - childPivotWorld.x;
                    double y = parentPos.yy() + parentPivotWorld.y - childPivotWorld.y;
                    double z = parentPos.zz() + parentPivotWorld.z - childPivotWorld.z;

                    createRagdollPart(space, bodyId, partName, new RVec3(x, y, z), rotation, spawnedBodies,
                            groupFilter, partIndices.get(partName));

                    spawnRagdollChildren(space, bodyId, partName, def, rotation, spawnedBodies, yawRad, groupFilter, partIndices);
                }
            }
        });
    }

    public void spawnTNT(Location location, String bodyId, float explosionForce, @Nullable Vector velocity) {
        if (location.getWorld() == null) return;
        PhysicsWorld space = physicsWorldRegistry.getSpace(location.getWorld());
        if (space == null) return;

        if (!space.isInsideWorld(location)) {
            throw new IllegalArgumentException("Cannot spawn TNT outside world bounds");
        }

        if (!space.canSpawnBodies(1)) {
            throw new IllegalStateException("World body limit reached");
        }

        if (getRegistry().find(bodyId).isEmpty()) {
            throw new IllegalArgumentException("Body ID not found: " + bodyId);
        }

        // Convert to Jolt
        RVec3 pos = space.toJolt(location);

        Quat rot = new Quat(0, 0, 0, 1);
        TNTPhysicsBody tnt = new TNTPhysicsBody(
                space,
                bodyId,
                getRegistry(),
                plugin.getRenderFactory(),
                shapeFactory,
                pos,
                rot,
                explosionForce,
                80
        );

        if (velocity != null) {
            com.github.stephengold.joltjni.Vec3 linearVel = new com.github.stephengold.joltjni.Vec3(
                    (float) velocity.getX(),
                    (float) velocity.getY(),
                    (float) velocity.getZ()
            );
            tnt.getBody().setLinearVelocity(linearVel);
        }

        Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
            Bukkit.getPluginManager().callEvent(new PhysicsBodySpawnEvent(tnt));
        });
    }

    private Location getSpawnLocation(Player player, double distance) {
        return player.getEyeLocation().add(player.getLocation().getDirection().multiply(distance));
    }

    private PhysicsBodyRegistry getRegistry() {
        return configurationService.getPhysicsBodyRegistry();
    }
}