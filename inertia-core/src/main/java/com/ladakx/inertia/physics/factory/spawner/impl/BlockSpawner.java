package com.ladakx.inertia.physics.factory.spawner.impl;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.events.PhysicsBodySpawnEvent;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.body.config.BlockBodyDefinition;
import com.ladakx.inertia.physics.body.config.BodyDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.factory.ValidationUtils;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.factory.spawner.BodySpawnContext;
import com.ladakx.inertia.physics.factory.spawner.BodySpawner;
import org.bukkit.Bukkit;
import org.joml.Quaternionf;

public class BlockSpawner implements BodySpawner {

    private final ConfigurationService configService;
    private final JShapeFactory shapeFactory;

    public BlockSpawner(ConfigurationService configService, JShapeFactory shapeFactory) {
        this.configService = configService;
        this.shapeFactory = shapeFactory;
    }

    @Override
    public @org.jetbrains.annotations.NotNull PhysicsBodyType getType() {
        return PhysicsBodyType.BLOCK;
    }

    @Override
    public boolean spawn(@org.jetbrains.annotations.NotNull BodySpawnContext context) {
        PhysicsBodyRegistry.BodyModel model = configService.getPhysicsBodyRegistry().require(context.bodyId());
        BodyDefinition def = model.bodyDefinition();

        if (def instanceof BlockBodyDefinition blockDef) {
            ShapeRefC shapeRef = shapeFactory.createShape(blockDef.shapeLines());
            try {
                RVec3 pos = context.world().toJolt(context.location());
                float yawRad = (float) Math.toRadians(-context.location().getYaw());
                float pitchRad = (float) Math.toRadians(context.location().getPitch());
                Quaternionf jomlQuat = new Quaternionf().rotationYXZ(yawRad, pitchRad, 0f);
                Quat rot = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

                ValidationUtils.ValidationResult result = ValidationUtils.canSpawnAt(context.world(), shapeRef, pos, rot);

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

        // TODO: In Step 3, InertiaAPI should delegate back to a factory/spawner instead of implementing logic itself.
        // For now, we rely on the existing API implementation to create the object after validation.
        InertiaPhysicsBody obj = InertiaAPI.get().createBody(context.location(), context.bodyId());
        if (obj != null) {
            Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
                Bukkit.getPluginManager().callEvent(new PhysicsBodySpawnEvent(obj));
            });
            return true;
        }
        return false;
    }
}