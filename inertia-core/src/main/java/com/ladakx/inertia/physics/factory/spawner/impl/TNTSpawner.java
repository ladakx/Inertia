package com.ladakx.inertia.physics.factory.spawner.impl;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.ladakx.inertia.api.events.PhysicsBodySpawnEvent;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.body.config.BlockBodyDefinition;
import com.ladakx.inertia.physics.body.impl.TNTPhysicsBody;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.factory.ValidationUtils;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.factory.spawner.BodySpawnContext;
import com.ladakx.inertia.physics.factory.spawner.BodySpawner;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.RenderFactory;
import org.bukkit.Bukkit;
import org.bukkit.util.Vector;

import java.util.Optional;

public class TNTSpawner implements BodySpawner {

    private final ConfigurationService configService;
    private final JShapeFactory shapeFactory;
    private final RenderFactory renderFactory;

    public TNTSpawner(ConfigurationService configService, JShapeFactory shapeFactory, RenderFactory renderFactory) {
        this.configService = configService;
        this.shapeFactory = shapeFactory;
        this.renderFactory = renderFactory;
    }

    @Override
    public @org.jetbrains.annotations.NotNull PhysicsBodyType getType() {
        return PhysicsBodyType.TNT;
    }

    @Override
    public boolean spawn(@org.jetbrains.annotations.NotNull BodySpawnContext context) {
        PhysicsWorld space = context.world();
        if (!space.isInsideWorld(context.location())) {
             throw new IllegalArgumentException("Cannot spawn TNT outside world bounds");
        }
        if (!space.canSpawnBodies(1)) {
            throw new IllegalStateException("World body limit reached");
        }

        PhysicsBodyRegistry.BodyModel model = configService.getPhysicsBodyRegistry().require(context.bodyId());
        RVec3 pos = space.toJolt(context.location());
        Quat rot = new Quat(0, 0, 0, 1);

        if (model.bodyDefinition() instanceof BlockBodyDefinition blockDef) {
            ShapeRefC shapeRef = shapeFactory.createShape(blockDef.shapeLines());
            try {
                ValidationUtils.ValidationResult result = ValidationUtils.canSpawnAt(space, shapeRef, pos, rot);
                if (result != ValidationUtils.ValidationResult.SUCCESS) {
                    if (result == ValidationUtils.ValidationResult.OUT_OF_BOUNDS) {
                        throw new IllegalArgumentException("Cannot spawn TNT outside world bounds");
                    } else if (result == ValidationUtils.ValidationResult.OBSTRUCTED) {
                        throw new IllegalStateException("Spawn location obstructed");
                    }
                }
            } finally {
                shapeRef.close();
            }
        }

        float force = context.getParam("force", Float.class, 20.0f);
        Vector velocity = context.getParam("velocity", Vector.class, null);
        int fuse = context.getParam("fuse", Integer.class, 80);

        TNTPhysicsBody tnt = new TNTPhysicsBody(
                space,
                context.bodyId(),
                configService.getPhysicsBodyRegistry(),
                renderFactory,
                shapeFactory,
                pos,
                rot,
                force,
                fuse
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

        return true;
    }
}