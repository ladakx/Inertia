package com.ladakx.inertia.physics.factory;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstShape;
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
import com.ladakx.inertia.physics.body.config.BlockBodyDefinition;
import com.ladakx.inertia.physics.body.config.BodyDefinition;
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
import java.util.concurrent.ThreadLocalRandom;

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

    public enum ValidationResult {
        SUCCESS,
        OUT_OF_BOUNDS,
        OBSTRUCTED
    }

    /**
     * Pre-checks if a shape can be spawned at the given location/rotation.
     */
    public ValidationResult canSpawnAt(PhysicsWorld space, ShapeRefC shapeRef, RVec3 pos, Quat rot) {
        ConstShape shape = shapeRef.getPtr();

        // 1. Check World Boundaries (AABB)
        AaBox aabb = shape.getWorldSpaceBounds(RMat44.sRotationTranslation(rot, pos), Vec3.sReplicate(1.0f));
        if (!space.getBoundaryManager().isAABBInside(aabb)) {
            return ValidationResult.OUT_OF_BOUNDS;
        }

        // 2. Check Static Overlaps
        if (space.checkOverlap(shape, pos, rot)) {
            return ValidationResult.OBSTRUCTED;
        }

        return ValidationResult.SUCCESS;
    }

    public boolean spawnBody(Location location, String bodyId) {
        return spawnBody(location, bodyId, null);
    }

    public boolean spawnBody(Location location, String bodyId, @Nullable Player player) {
        if (location.getWorld() == null) return false;
        PhysicsWorld space = physicsWorldRegistry.getSpace(location.getWorld());
        if (space == null) return false;

        // Basic point check
        if (!space.isInsideWorld(location)) {
            if (player != null) configurationService.getMessageManager().send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return false;
        }

        if (!space.canSpawnBodies(1)) {
            if (player != null) configurationService.getMessageManager().send(player, MessageKey.SPAWN_LIMIT_REACHED, "{limit}", String.valueOf(space.getSettings().performance().maxBodies()));
            return false;
        }

        PhysicsBodyRegistry.BodyModel model = configurationService.getPhysicsBodyRegistry().require(bodyId);
        BodyDefinition def = model.bodyDefinition();

        if (def instanceof BlockBodyDefinition blockDef) {
            ShapeRefC shapeRef = shapeFactory.createShape(blockDef.shapeLines());
            try {
                RVec3 pos = space.toJolt(location);

                float yawRad = (float) Math.toRadians(-location.getYaw());
                float pitchRad = (float) Math.toRadians(location.getPitch());
                Quaternionf jomlQuat = new Quaternionf().rotationYXZ(yawRad, pitchRad, 0f);
                Quat rot = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

                ValidationResult result = canSpawnAt(space, shapeRef, pos, rot);
                if (result != ValidationResult.SUCCESS) {
                    if (player != null) {
                        MessageKey key = (result == ValidationResult.OUT_OF_BOUNDS)
                                ? MessageKey.SPAWN_FAIL_OUT_OF_BOUNDS
                                : MessageKey.SPAWN_FAIL_OBSTRUCTED;
                        configurationService.getMessageManager().send(player, key);
                    }
                    return false;
                }
            } finally {
                shapeRef.close();
            }
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
        RVec3 localStartPos = space.toJolt(startLoc);
        Quaternionf jomlQuat = new Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), new org.joml.Vector3f(0, -1, 0));
        Quat linkRotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        // --- VALIDATION PHASE ---
        ShapeRefC shapeRef = shapeFactory.createShape(def.shapeLines());
        try {
            for (int i = 0; i < size; i++) {
                double offsetY = i * spacing * direction.getY();
                RVec3 pos = new RVec3(localStartPos.xx(), localStartPos.yy() + offsetY, localStartPos.zz());

                ValidationResult result = canSpawnAt(space, shapeRef, pos, linkRotation);
                if (result != ValidationResult.SUCCESS) {
                    MessageKey key = (result == ValidationResult.OUT_OF_BOUNDS)
                            ? MessageKey.SPAWN_FAIL_OUT_OF_BOUNDS
                            : MessageKey.SPAWN_FAIL_OBSTRUCTED;
                    configurationService.getMessageManager().send(player, key);
                    return; // Abort entire chain spawn
                }
            }
        } finally {
            shapeRef.close();
        }
        // ------------------------

        Body parentBody = null;
        GroupFilterTable groupFilter = new GroupFilterTable(size);

        for (int i = 0; i < size; i++) {
            if (i > 0) {
                groupFilter.disableCollision(i, i - 1);
            }
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

    public void spawnRagdoll(Player player, String bodyId, boolean applyImpulse) {
        PhysicsBodyRegistry registry = configurationService.getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(bodyId);

        if (modelOpt.isEmpty() || !(modelOpt.get().bodyDefinition() instanceof RagdollDefinition def)) {
            throw new IllegalArgumentException("Ragdoll body not found or invalid type: " + bodyId);
        }

        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return;

        Location spawnLoc = getSpawnLocation(player, 3.0);
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

        String rootPart = def.parts().entrySet().stream()
                .filter(e -> e.getValue().parentName() == null)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (rootPart == null) {
            configurationService.getMessageManager().send(player, MessageKey.INVALID_RAGDOLL_BODY, "{id}", bodyId);
            return;
        }

        // --- SIMULATION & VALIDATION PHASE ---
        RVec3 rootPos = space.toJolt(spawnLoc);
        Map<String, RagdollPrecalc> precalculatedParts = new HashMap<>();

        // Calculate all transforms first
        collectRagdollTransforms(rootPart, rootPos, rotation, def, yawRad, precalculatedParts);

        // Validate all parts
        for (RagdollPrecalc part : precalculatedParts.values()) {
            RagdollDefinition.RagdollPartDefinition partDef = part.def;
            List<String> shapeLines = partDef.shapeString() != null ? List.of(partDef.shapeString()) : null;

            // If no shape string, we assume Box from size (legacy support in createBodySettings)
            // But validation requires a shape.
            ShapeRefC shapeRef;
            if (shapeLines != null) {
                shapeRef = shapeFactory.createShape(shapeLines);
            } else {
                Vector size = partDef.size();
                shapeRef = new BoxShape(new Vec3((float) size.getX() / 2f, (float) size.getY() / 2f, (float) size.getZ() / 2f)).toRefC();
            }

            try {
                ValidationResult result = canSpawnAt(space, shapeRef, part.pos, part.rot);
                if (result != ValidationResult.SUCCESS) {
                    MessageKey key = (result == ValidationResult.OUT_OF_BOUNDS)
                            ? MessageKey.SPAWN_FAIL_OUT_OF_BOUNDS
                            : MessageKey.SPAWN_FAIL_OBSTRUCTED;
                    configurationService.getMessageManager().send(player, key);
                    return; // Abort entire ragdoll spawn
                }
            } finally {
                shapeRef.close();
            }
        }
        // -------------------------------------

        // --- EXECUTION PHASE ---
        Map<String, Body> spawnedParts = new HashMap<>();
        GroupFilterTable groupFilter = new GroupFilterTable(partsCount);
        Map<String, Integer> partIndices = new HashMap<>();
        int indexCounter = 0;
        for (String key : def.parts().keySet()) {
            partIndices.put(key, indexCounter++);
        }

        // Spawn Root First
        RagdollPrecalc rootCalc = precalculatedParts.get(rootPart);
        createRagdollPart(space, bodyId, rootPart, rootCalc.pos, rootCalc.rot, spawnedParts, groupFilter, partIndices.get(rootPart));

        // Spawn Children recursively (logic reused from pre-calc structure or recursion)
        spawnRagdollChildren(space, bodyId, rootPart, def, spawnedParts, groupFilter, partIndices, precalculatedParts);

        // Apply Impulse if requested
        if (applyImpulse && spawnedParts.containsKey(rootPart)) {
            Body rootBody = spawnedParts.get(rootPart);
            Vector dir = player.getLocation().getDirection().multiply(1500.0);
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            RVec3 impulsePos = new RVec3(
                    rootCalc.pos.xx() + rand.nextDouble(-0.3, 0.3),
                    rootCalc.pos.yy() + rand.nextDouble(-0.3, 0.3),
                    rootCalc.pos.zz() + rand.nextDouble(-0.3, 0.3)
            );
            rootBody.addImpulse(new Vec3((float)dir.getX(), (float)dir.getY(), (float)dir.getZ()), impulsePos);
        }

        configurationService.getMessageManager().send(player, MessageKey.RAGDOLL_SPAWN_SUCCESS, "{id}", bodyId);
    }

    // Legacy support for command not using boolean flag
    public void spawnRagdoll(Player player, String bodyId) {
        spawnRagdoll(player, bodyId, false);
    }

    public void spawnTNT(Location location, String bodyId, float explosionForce, @Nullable Vector velocity) {
        if (location.getWorld() == null) return;
        PhysicsWorld space = physicsWorldRegistry.getSpace(location.getWorld());
        if (space == null) return;

        PhysicsBodyRegistry.BodyModel model = getRegistry().require(bodyId);
        if (model.bodyDefinition() instanceof BlockBodyDefinition blockDef) {
            ShapeRefC shapeRef = shapeFactory.createShape(blockDef.shapeLines());
            try {
                RVec3 pos = space.toJolt(location);
                Quat rot = new Quat(0, 0, 0, 1);

                ValidationResult result = canSpawnAt(space, shapeRef, pos, rot);
                if (result != ValidationResult.SUCCESS) {
                    if (result == ValidationResult.OUT_OF_BOUNDS) {
                        throw new IllegalArgumentException("Cannot spawn TNT outside world bounds");
                    } else if (result == ValidationResult.OBSTRUCTED) {
                        throw new IllegalStateException("Spawn location obstructed");
                    }
                }
            } finally {
                shapeRef.close();
            }
        } else {
            if (!space.isInsideWorld(location)) {
                throw new IllegalArgumentException("Cannot spawn TNT outside world bounds");
            }
        }

        if (!space.canSpawnBodies(1)) {
            throw new IllegalStateException("World body limit reached");
        }

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
            if (spawnBody(loc, bodyId, player)) {
                count++;
            }
        }

        return count;
    }

    // --- Helper Methods & Classes for Ragdoll ---

    private record RagdollPrecalc(RVec3 pos, Quat rot, RagdollDefinition.RagdollPartDefinition def) {}

    private void collectRagdollTransforms(String currentPartName, RVec3 currentPos, Quat currentRot,
                                          RagdollDefinition def, double yawRad,
                                          Map<String, RagdollPrecalc> accumulator) {

        RagdollDefinition.RagdollPartDefinition currentDef = def.parts().get(currentPartName);
        accumulator.put(currentPartName, new RagdollPrecalc(currentPos, currentRot, currentDef));

        // Find children
        def.parts().forEach((childName, childDef) -> {
            if (currentPartName.equals(childDef.parentName())) {
                var joint = childDef.joint();
                if (joint != null) {
                    Vector pOffset = joint.pivotOnParent();
                    Vector cOffset = joint.pivotOnChild();

                    Vector3d parentPivotWorld = new Vector3d(pOffset.getX(), pOffset.getY(), pOffset.getZ()).rotateY(yawRad);
                    Vector3d childPivotWorld = new Vector3d(cOffset.getX(), cOffset.getY(), cOffset.getZ()).rotateY(yawRad);

                    double x = currentPos.xx() + parentPivotWorld.x - childPivotWorld.x;
                    double y = currentPos.yy() + parentPivotWorld.y - childPivotWorld.y;
                    double z = currentPos.zz() + parentPivotWorld.z - childPivotWorld.z;

                    collectRagdollTransforms(childName, new RVec3(x, y, z), currentRot, def, yawRad, accumulator);
                }
            }
        });
    }

    private void spawnRagdollChildren(PhysicsWorld space, String bodyId, String parentName,
                                      RagdollDefinition def, Map<String, Body> spawnedBodies,
                                      GroupFilterTable groupFilter, Map<String, Integer> partIndices,
                                      Map<String, RagdollPrecalc> precalc) {

        def.parts().forEach((partName, partDef) -> {
            if (parentName.equals(partDef.parentName())) {
                RagdollPrecalc calc = precalc.get(partName);
                if (calc != null) {
                    createRagdollPart(space, bodyId, partName, calc.pos, calc.rot, spawnedBodies,
                            groupFilter, partIndices.get(partName));

                    // Recurse
                    spawnRagdollChildren(space, bodyId, partName, def, spawnedBodies, groupFilter, partIndices, precalc);
                }
            }
        });
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

    private Location getSpawnLocation(Player player, double distance) {
        return player.getEyeLocation().add(player.getLocation().getDirection().multiply(distance));
    }

    private PhysicsBodyRegistry getRegistry() {
        return configurationService.getPhysicsBodyRegistry();
    }
}