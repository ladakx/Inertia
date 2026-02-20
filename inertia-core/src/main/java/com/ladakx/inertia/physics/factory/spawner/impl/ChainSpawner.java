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
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.body.config.BodyPhysicsSettings;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.body.impl.ChainPhysicsBody;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public InertiaPhysicsBody spawnBody(@org.jetbrains.annotations.NotNull BodySpawnContext context) {
        PhysicsBodyRegistry registry = configService.getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(context.bodyId());
        if (modelOpt.isEmpty()) {
            throw new IllegalArgumentException("Chain body not found: " + context.bodyId());
        }
        if (!(modelOpt.get().bodyDefinition() instanceof ChainBodyDefinition def)) {
            throw new IllegalArgumentException("Body ID '" + context.bodyId() + "' is not of type CHAIN.");
        }

        int size = context.getParam("size", Integer.class, -1);
        Location startLoc = context.location();
        Location endLoc = context.getParam("end", Location.class, null);
        boolean bypassValidation = context.getParam("bypass_validation", Boolean.class, false);
        UUID clusterId = context.getParam("cluster_id", UUID.class, UUID.randomUUID());

        if (!bypassValidation && !context.world().isInsideWorld(startLoc)) {
            if (context.player() != null) configService.getMessageManager().send(context.player(), MessageKey.ERROR_OCCURRED, "{error}", "Outside of world bounds!");
            return null;
        }

        double spacing = def.creation().spacing();
        Vector direction;
        double step;

        if (endLoc != null) {
            if (!startLoc.getWorld().equals(endLoc.getWorld())) {
                if (context.player() != null) configService.getMessageManager().send(context.player(), MessageKey.ERROR_OCCURRED, "{error}", "Start/end world mismatch!");
                return null;
            }
            Vector delta = endLoc.toVector().subtract(startLoc.toVector());
            double dist = delta.length();
            if (dist < 1.0e-4) {
                direction = new Vector(0, -1, 0);
                if (size <= 0) size = 2;
                step = spacing;
            } else {
                direction = delta.clone().multiply(1.0 / dist);
                if (size <= 0) {
                    size = Math.max(2, (int) Math.ceil(dist / spacing) + 1);
                }
                step = dist / (size - 1);
            }
        } else {
            if (size <= 0) size = 10;
            direction = new Vector(0, -1, 0);
            step = spacing;
        }

        if (!context.world().canSpawnBodies(size)) {
            if (context.player() != null) configService.getMessageManager().send(context.player(), MessageKey.SPAWN_LIMIT_REACHED,
                    "{limit}", String.valueOf(context.world().getSettings().performance().maxBodies()));
            return null;
        }

        Quaternionf jomlQuat;
        if (direction.lengthSquared() < 1.0e-8) {
            jomlQuat = new Quaternionf();
        } else {
            Vector dir = direction.clone().normalize();
            jomlQuat = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()));
        }
        Quat baseRotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);
        ShapeRefC shapeRef = shapeFactory.createShape(def.shapeLines());

        try {
            if (!bypassValidation) {
                for (int i = 0; i < size; i++) {
                    Location linkLoc = startLoc.clone().add(direction.clone().multiply(i * step));
                    if (!context.world().isInsideWorld(linkLoc)) {
                        if (context.player() != null) configService.getMessageManager().send(context.player(), MessageKey.SPAWN_FAIL_OUT_OF_BOUNDS);
                        return null;
                    }
                    RVec3 pos = context.world().toJolt(linkLoc);
                    if (endLoc == null) {
                        ValidationUtils.ValidationResult result = ValidationUtils.canSpawnAt(context.world(), shapeRef, pos, baseRotation);
                        if (result != ValidationUtils.ValidationResult.SUCCESS) {
                            if (context.player() != null) {
                                MessageKey key = (result == ValidationUtils.ValidationResult.OUT_OF_BOUNDS)
                                        ? MessageKey.SPAWN_FAIL_OUT_OF_BOUNDS
                                        : MessageKey.SPAWN_FAIL_OBSTRUCTED;
                                configService.getMessageManager().send(context.player(), key);
                            }
                            return null;
                        }
                    }
                }
            }
        } finally {
            shapeRef.close();
        }

        Body parentBody = null;
        GroupFilterTable groupFilter = new GroupFilterTable(size);
        ChainPhysicsBody firstLink = null;
        ChainPhysicsBody lastLink = null;

        for (int i = 0; i < size; i++) {
            if (i > 0) {
                groupFilter.disableCollision(i, i - 1);
            }

            // ВАЖНО: Мы спавним звенья идеально ровно, чтобы правильно рассчитать суставы
            Location linkLoc = startLoc.clone().add(direction.clone().multiply(i * step));
            RVec3 pos = context.world().toJolt(linkLoc);

            ChainPhysicsBody link = new ChainPhysicsBody(
                    context.world(),
                    context.bodyId(),
                    registry,
                    renderFactory,
                    shapeFactory,
                    pos,
                    baseRotation,
                    parentBody,
                    groupFilter,
                    i,
                    size
            );
            link.setClusterId(clusterId);

            if (firstLink == null) firstLink = link;
            lastLink = link;
            parentBody = link.getBody();
            Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
                Bukkit.getPluginManager().callEvent(new PhysicsBodySpawnEvent(link));
            });
        }

        if (endLoc != null && firstLink != null && lastLink != null) {
            Vector dirNorm = direction.clone();
            if (dirNorm.lengthSquared() < 1.0e-8) {
                dirNorm = new Vector(0, -1, 0);
            } else {
                dirNorm.normalize();
            }
            RVec3 startAnchor = context.world().toJolt(startLoc);
            RVec3 endAnchor = context.world().toJolt(endLoc);
            anchorToWorld(context.world(), firstLink, startAnchor, def);
            anchorToWorld(context.world(), lastLink, endAnchor, def);
        }

        return lastLink;
    }

    private void anchorToWorld(PhysicsWorld world, ChainPhysicsBody link, RVec3 anchorPos, ChainBodyDefinition def) {
        Body fixedBody = Body.sFixedToWorld();
        SixDofConstraintSettings settings = new SixDofConstraintSettings();
        settings.setSpace(EConstraintSpace.WorldSpace);
        settings.setPosition1(anchorPos);
        settings.setPosition2(anchorPos);
        settings.makeFixedAxis(EAxis.TranslationX);
        settings.makeFixedAxis(EAxis.TranslationY);
        settings.makeFixedAxis(EAxis.TranslationZ);
        float swingRad = (float) Math.toRadians(def.limits().swingLimitAngle());
        settings.setLimitedAxis(EAxis.RotationX, -swingRad, swingRad);
        settings.setLimitedAxis(EAxis.RotationZ, -swingRad, swingRad);
        switch (def.limits().twistMode()) {
            case LOCKED -> settings.makeFixedAxis(EAxis.RotationY);
            case LIMITED -> settings.setLimitedAxis(EAxis.RotationY, -0.1f, 0.1f);
            case FREE -> settings.makeFreeAxis(EAxis.RotationY);
        }
        TwoBodyConstraint constraint = settings.create(fixedBody, link.getBody());
        world.addConstraint(constraint);
        link.addRelatedConstraint(constraint.toRef());
        world.getBodyInterface().activateBody(link.getBody().getId());
        link.setAnchored(true);
        link.setWorldAnchor(anchorPos);
    }
}