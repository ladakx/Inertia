package com.ladakx.inertia.physics.factory.spawner.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.api.events.PhysicsBodySpawnEvent;
import com.ladakx.inertia.common.utils.MiscUtils;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.body.config.RagdollDefinition;
import com.ladakx.inertia.physics.body.impl.RagdollPhysicsBody;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.factory.ValidationUtils;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.factory.spawner.BodySpawnContext;
import com.ladakx.inertia.physics.factory.spawner.BodySpawner;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.RenderFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RagdollSpawner implements BodySpawner {

    private final ConfigurationService configService;
    private final JShapeFactory shapeFactory;
    private final RenderFactory renderFactory;

    public RagdollSpawner(ConfigurationService configService, JShapeFactory shapeFactory, RenderFactory renderFactory) {
        this.configService = configService;
        this.shapeFactory = shapeFactory;
        this.renderFactory = renderFactory;
    }

    @Override
    public @org.jetbrains.annotations.NotNull PhysicsBodyType getType() {
        return PhysicsBodyType.RAGDOLL;
    }

    @Override
    public boolean spawn(@org.jetbrains.annotations.NotNull BodySpawnContext context) {
        PhysicsBodyRegistry registry = configService.getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(context.bodyId());

        if (modelOpt.isEmpty() || !(modelOpt.get().bodyDefinition() instanceof RagdollDefinition def)) {
            throw new IllegalArgumentException("Ragdoll body not found or invalid type: " + context.bodyId());
        }

        PhysicsWorld space = context.world();
        Location spawnLoc = context.location();

        int partsCount = def.parts().size();
        if (!space.canSpawnBodies(partsCount)) {
            if (context.player() != null) configService.getMessageManager().send(context.player(), MessageKey.SPAWN_LIMIT_REACHED,
                    "{limit}", String.valueOf(space.getSettings().performance().maxBodies()));
            return false;
        }

        float yaw = -spawnLoc.getYaw() + 180;
        double yawRad = Math.toRadians(yaw);
        Quaternionf jomlQuat = new Quaternionf(new AxisAngle4f((float) yawRad, 0, 1, 0));
        Quat rotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        String rootPart = def.parts().entrySet().stream()
                .filter(e -> e.getValue().parentName() == null)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (rootPart == null) {
            if (context.player() != null) configService.getMessageManager().send(context.player(), MessageKey.INVALID_RAGDOLL_BODY, "{id}", context.bodyId());
            return false;
        }

        RVec3 rootPos = space.toJolt(spawnLoc);
        Map<String, RagdollPrecalc> precalculatedParts = new HashMap<>();
        collectRagdollTransforms(rootPart, rootPos, rotation, def, yawRad, precalculatedParts);

        for (RagdollPrecalc part : precalculatedParts.values()) {
            RagdollDefinition.RagdollPartDefinition partDef = part.def;
            List<String> shapeLines = partDef.shapeString() != null ? List.of(partDef.shapeString()) : null;
            ShapeRefC shapeRef;
            if (shapeLines != null) {
                shapeRef = shapeFactory.createShape(shapeLines);
            } else {
                Vector size = partDef.size();
                shapeRef = new BoxShape(new Vec3((float) size.getX() / 2f, (float) size.getY() / 2f, (float) size.getZ() / 2f)).toRefC();
            }

            try {
                ValidationUtils.ValidationResult result = ValidationUtils.canSpawnAt(space, shapeRef, part.pos, part.rot);
                if (result != ValidationUtils.ValidationResult.SUCCESS) {
                    if (context.player() != null) {
                        MessageKey key = (result == ValidationUtils.ValidationResult.OUT_OF_BOUNDS)
                                ? MessageKey.SPAWN_FAIL_OUT_OF_BOUNDS
                                : MessageKey.SPAWN_FAIL_OBSTRUCTED;
                        configService.getMessageManager().send(context.player(), key);
                    }
                    return false;
                }
            } finally {
                shapeRef.close();
            }
        }

        Map<String, Body> spawnedParts = new HashMap<>();
        GroupFilterTable groupFilter = new GroupFilterTable(partsCount);
        Map<String, Integer> partIndices = new HashMap<>();
        int indexCounter = 0;
        for (String key : def.parts().keySet()) {
            partIndices.put(key, indexCounter++);
        }

        RagdollPrecalc rootCalc = precalculatedParts.get(rootPart);
        createRagdollPart(space, context.bodyId(), rootPart, rootCalc.pos, rootCalc.rot, spawnedParts, groupFilter, partIndices.get(rootPart));
        spawnRagdollChildren(space, context.bodyId(), rootPart, def, spawnedParts, groupFilter, partIndices, precalculatedParts);

        boolean applyImpulse = context.getParam("impulse", Boolean.class, false);
        if (applyImpulse && spawnedParts.containsKey(rootPart)) {
            Body rootBody = spawnedParts.get(rootPart);
            Vector dir = spawnLoc.getDirection().multiply(1500.0);
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            RVec3 impulsePos = new RVec3(
                    rootCalc.pos.xx() + rand.nextDouble(-0.3, 0.3),
                    rootCalc.pos.yy() + rand.nextDouble(-0.3, 0.3),
                    rootCalc.pos.zz() + rand.nextDouble(-0.3, 0.3)
            );
            rootBody.addImpulse(new Vec3((float)dir.getX(), (float)dir.getY(), (float)dir.getZ()), impulsePos);
        }
        return true;
    }

    private record RagdollPrecalc(RVec3 pos, Quat rot, RagdollDefinition.RagdollPartDefinition def) {}

    private void collectRagdollTransforms(String currentPartName, RVec3 currentPos, Quat currentRot,
                                          RagdollDefinition def, double yawRad,
                                          Map<String, RagdollPrecalc> accumulator) {
        RagdollDefinition.RagdollPartDefinition currentDef = def.parts().get(currentPartName);
        accumulator.put(currentPartName, new RagdollPrecalc(currentPos, currentRot, currentDef));

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
                    spawnRagdollChildren(space, bodyId, partName, def, spawnedBodies, groupFilter, partIndices, precalc);
                }
            }
        });
    }

    private void createRagdollPart(PhysicsWorld space, String bodyId, String partName, RVec3 pos, Quat rot,
                                   Map<String, Body> spawnedBodies, GroupFilterTable groupFilter, int partIndex) {
        RagdollPhysicsBody obj = new RagdollPhysicsBody(
                space, bodyId, partName, configService.getPhysicsBodyRegistry(),
                renderFactory,
                shapeFactory,
                pos, rot, spawnedBodies,
                groupFilter, partIndex
        );
        spawnedBodies.put(partName, obj.getBody());
        Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
            Bukkit.getPluginManager().callEvent(new PhysicsBodySpawnEvent(obj));
        });
    }
}