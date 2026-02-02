package com.ladakx.inertia.physics.factory.spawner.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.api.events.PhysicsBodySpawnEvent;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.utils.MiscUtils;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.body.config.BodyPhysicsSettings;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.body.impl.ChainPhysicsBody;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.factory.ValidationUtils;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.factory.spawner.BodySpawnContext;
import com.ladakx.inertia.physics.factory.spawner.BodySpawner;
import com.ladakx.inertia.rendering.RenderFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.Optional;

public class ChainSpawner implements BodySpawner {

    private final ConfigurationService configService;
    private final JShapeFactory shapeFactory;
    private final RenderFactory renderFactory;

    public ChainSpawner(ConfigurationService configService, JShapeFactory shapeFactory, RenderFactory renderFactory) {
        this.configService = configService;
        this.shapeFactory = shapeFactory;
        this.renderFactory = renderFactory;
    }

    @Override
    public @org.jetbrains.annotations.NotNull PhysicsBodyType getType() {
        return PhysicsBodyType.CHAIN;
    }

    @Override
    public boolean spawn(@org.jetbrains.annotations.NotNull BodySpawnContext context) {
        PhysicsBodyRegistry registry = configService.getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(context.bodyId());

        if (modelOpt.isEmpty()) {
             throw new IllegalArgumentException("Chain body not found: " + context.bodyId());
        }
        if (!(modelOpt.get().bodyDefinition() instanceof ChainBodyDefinition def)) {
            throw new IllegalArgumentException("Body ID '" + context.bodyId() + "' is not of type CHAIN.");
        }

        int size = context.getParam("size", Integer.class, 10);
        Location startLoc = context.location(); // already offset in Tool logic or Command logic

        if (!context.world().isInsideWorld(startLoc)) {
            if (context.player() != null) configService.getMessageManager().send(context.player(), MessageKey.ERROR_OCCURRED, "{error}", "Outside of world bounds!");
            return false;
        }

        if (!context.world().canSpawnBodies(size)) {
            if (context.player() != null) configService.getMessageManager().send(context.player(), MessageKey.SPAWN_LIMIT_REACHED,
                    "{limit}", String.valueOf(context.world().getSettings().performance().maxBodies()));
            return false;
        }

        Vector direction = new Vector(0, -1, 0);
        double spacing = def.creation().spacing();
        RVec3 localStartPos = context.world().toJolt(startLoc);
        Quaternionf jomlQuat = new Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), new org.joml.Vector3f(0, -1, 0));
        Quat linkRotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        ShapeRefC shapeRef = shapeFactory.createShape(def.shapeLines());
        try {
            for (int i = 0; i < size; i++) {
                double offsetY = i * spacing * direction.getY();
                RVec3 pos = new RVec3(localStartPos.xx(), localStartPos.yy() + offsetY, localStartPos.zz());
                ValidationUtils.ValidationResult result = ValidationUtils.canSpawnAt(context.world(), shapeRef, pos, linkRotation);
                if (result != ValidationUtils.ValidationResult.SUCCESS) {
                    if (context.player() != null) {
                        MessageKey key = (result == ValidationUtils.ValidationResult.OUT_OF_BOUNDS)
                                ? MessageKey.SPAWN_FAIL_OUT_OF_BOUNDS
                                : MessageKey.SPAWN_FAIL_OBSTRUCTED;
                        configService.getMessageManager().send(context.player(), key);
                    }
                    return false;
                }
            }
        } finally {
            shapeRef.close();
        }

        Body parentBody = null;
        GroupFilterTable groupFilter = new GroupFilterTable(size);

        for (int i = 0; i < size; i++) {
            if (i > 0) {
                groupFilter.disableCollision(i, i - 1);
            }

            double offsetY = i * spacing * direction.getY();
            RVec3 pos = new RVec3(localStartPos.xx(), localStartPos.yy() + offsetY, localStartPos.zz());

            ChainPhysicsBody link = new ChainPhysicsBody(
                    context.world(),
                    context.bodyId(),
                    registry,
                    renderFactory,
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
        return true;
    }
}