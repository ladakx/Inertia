package com.ladakx.inertia.physics.factory.spawner.impl;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.ladakx.inertia.api.events.PhysicsBodyPostSpawnEvent;
import com.ladakx.inertia.api.events.PhysicsBodyPreSpawnEvent;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.body.PhysicsBodyType;
import com.ladakx.inertia.physics.body.config.BlockBodyDefinition;
import com.ladakx.inertia.physics.body.config.BodyDefinition;
import com.ladakx.inertia.physics.body.impl.BlockPhysicsBody;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.factory.ValidationUtils;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.factory.spawner.BodySpawnContext;
import com.ladakx.inertia.physics.factory.spawner.BodySpawner;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.RenderFactory;
import org.bukkit.Bukkit;
import org.joml.Quaternionf;

import java.util.UUID;

public class BlockSpawner implements BodySpawner {
    private final ConfigurationService configService;
    private final JShapeFactory shapeFactory;
    private final RenderFactory renderFactory;

    public BlockSpawner(ConfigurationService configService, JShapeFactory shapeFactory, RenderFactory renderFactory) {
        this.configService = configService;
        this.shapeFactory = shapeFactory;
        this.renderFactory = renderFactory;
    }

    @Override
    public @org.jetbrains.annotations.NotNull PhysicsBodyType getType() {
        return PhysicsBodyType.BLOCK;
    }

    @Override
    public PhysicsBody spawnBody(@org.jetbrains.annotations.NotNull BodySpawnContext context) {
        PhysicsWorld space = context.world();
        PhysicsBodyRegistry.BodyModel model = configService.getPhysicsBodyRegistry().require(context.bodyId());
        BodyDefinition def = model.bodyDefinition();
        boolean bypassValidation = context.getParam("bypass_validation", Boolean.class, false);
        UUID clusterId = context.getParam("cluster_id", UUID.class, UUID.randomUUID());

        RVec3 pos = space.toJolt(context.location());
        float yawRad = (float) Math.toRadians(-context.location().getYaw());
        float pitchRad = (float) Math.toRadians(context.location().getPitch());
        Quaternionf jomlQuat = new Quaternionf().rotationYXZ(yawRad, pitchRad, 0f);
        Quat rot = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        if (!bypassValidation && def instanceof BlockBodyDefinition blockDef) {
            ShapeRefC shapeRef = shapeFactory.createShape(blockDef.shapeLines());
            try {
                ValidationUtils.ValidationResult result = ValidationUtils.canSpawnAt(space, shapeRef, pos, rot);
                if (result != ValidationUtils.ValidationResult.SUCCESS) {
                    if (context.player() != null) {
                        MessageKey key = (result == ValidationUtils.ValidationResult.OUT_OF_BOUNDS)
                                ? MessageKey.SPAWN_FAIL_OUT_OF_BOUNDS
                                : MessageKey.SPAWN_FAIL_OBSTRUCTED;
                        configService.getMessageManager().send(context.player(), key);
                    }
                    return null;
                }
            } finally {
                shapeRef.close();
            }
        }

        BlockPhysicsBody body = new BlockPhysicsBody(
                space,
                context.bodyId(),
                configService.getPhysicsBodyRegistry(),
                renderFactory,
                shapeFactory,
                pos,
                rot,
                space.getEventDispatcher()
        );
        body.setClusterId(clusterId);

        Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
            PhysicsBodyPreSpawnEvent preSpawnEvent = new PhysicsBodyPreSpawnEvent(body);
            Bukkit.getPluginManager().callEvent(preSpawnEvent);
            if (preSpawnEvent.isCancelled()) {
                body.destroy();
                return;
            }
            Bukkit.getPluginManager().callEvent(new PhysicsBodyPostSpawnEvent(body));
        });

        return body;
    }
}
