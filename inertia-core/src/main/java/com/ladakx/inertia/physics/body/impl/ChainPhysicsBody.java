package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.common.pdc.InertiaPDCUtils;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.rendering.VisualEntity;
import com.ladakx.inertia.physics.body.config.BodyPhysicsSettings;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.rendering.runtime.PhysicsDisplayComposite;
import com.ladakx.inertia.common.utils.ConvertUtils;
import com.ladakx.inertia.common.utils.MiscUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChainPhysicsBody extends DisplayedPhysicsBody {

    private final String bodyId;
    private boolean removed = false;
    private final int calculatedIterations;

    public ChainPhysicsBody(@NotNull PhysicsWorld space,
                            @NotNull String bodyId,
                            @NotNull PhysicsBodyRegistry modelRegistry,
                            @NotNull RenderFactory renderFactory,
                            @NotNull JShapeFactory shapeFactory,
                            @NotNull RVec3 initialPosition,
                            @NotNull Quat initialRotation,
                            @Nullable Body parentBody,
                            @NotNull GroupFilterTable groupFilter,
                            int chainIndex,
                            int totalChainLength) {
        super(space, createBodySettings(bodyId, modelRegistry, shapeFactory, initialPosition, initialRotation, groupFilter, chainIndex, totalChainLength), renderFactory, modelRegistry);
        this.bodyId = bodyId;
        this.calculatedIterations = calculateIterations(modelRegistry.require(bodyId), totalChainLength);

        if (parentBody != null) {
            createConstraint(modelRegistry, bodyId, parentBody);
        }

        this.displayComposite = recreateDisplay();
    }

    @Override
    protected PhysicsDisplayComposite recreateDisplay() {
        PhysicsBodyRegistry.BodyModel model = modelRegistry.require(bodyId);
        Optional<RenderModelDefinition> renderOpt = model.renderModel();

        if (renderOpt.isEmpty()) return null;

        RenderModelDefinition renderDef = renderOpt.get();
        World world = getSpace().getWorldBukkit();

        // Use current body position
        RVec3 currentPos = getBody().getPosition();
        Location spawnLoc = new Location(world, currentPos.xx(), currentPos.yy(), currentPos.zz());
        UUID bodyUuid = getUuid();

        List<PhysicsDisplayComposite.DisplayPart> parts = new ArrayList<>();
        for (Map.Entry<String, RenderEntityDefinition> entry : renderDef.entities().entrySet()) {
            String entityKey = entry.getKey();
            RenderEntityDefinition entityDef = entry.getValue();

            VisualEntity visual = renderFactory.create(world, spawnLoc, entityDef);
            if (visual.isValid()) {
                InertiaPDCUtils.applyInertiaTags(
                        visual,
                        bodyId,
                        bodyUuid,
                        renderDef.id(),
                        entityKey
                );
                parts.add(new PhysicsDisplayComposite.DisplayPart(entityDef, visual));
            }
        }
        return new PhysicsDisplayComposite(getBody(), renderDef, world, parts);
    }

    // ... (Остальные методы calculateGravity, calculateIterations, getLerpFactor, createBodySettings, createConstraint без изменений)
    // Копируем методы из предыдущей версии ChainPhysicsBody, они не меняются логически, только конструктор и recreateDisplay

    private static float calculateGravity(PhysicsBodyRegistry.BodyModel model, int length) {
        if (!(model.bodyDefinition() instanceof ChainBodyDefinition def) || !def.adaptive().enabled()) {
            return ((ChainBodyDefinition) model.bodyDefinition()).physicsSettings().gravityFactor();
        }
        ChainBodyDefinition.AdaptiveSettings adapt = def.adaptive();
        double factor = getLerpFactor(length, adapt.minLength(), adapt.maxLength());
        return (float) MiscUtils.lerp(adapt.maxGravity(), adapt.minGravity(), factor);
    }

    private int calculateIterations(PhysicsBodyRegistry.BodyModel model, int length) {
        if (!(model.bodyDefinition() instanceof ChainBodyDefinition def)) return 2;
        if (!def.adaptive().enabled()) {
            return def.stabilization().positionIterations();
        }
        ChainBodyDefinition.AdaptiveSettings adapt = def.adaptive();
        double factor = getLerpFactor(length, adapt.minLength(), adapt.maxLength());
        return (int) MiscUtils.lerp(adapt.minIterations(), adapt.maxIterations(), factor);
    }

    private static double getLerpFactor(int current, int min, int max) {
        if (current <= min) return 0.0;
        if (current >= max) return 1.0;
        return (double) (current - min) / (max - min);
    }

    private static BodyCreationSettings createBodySettings(String bodyId,
                                                           PhysicsBodyRegistry registry,
                                                           JShapeFactory shapeFactory,
                                                           RVec3 pos, Quat rot,
                                                           GroupFilterTable groupFilter,
                                                           int chainIndex,
                                                           int totalLength) {
        PhysicsBodyRegistry.BodyModel model = registry.require(bodyId);
        if (!(model.bodyDefinition() instanceof ChainBodyDefinition def)) {
            throw new IllegalArgumentException("Body '" + bodyId + "' is not a CHAIN definition.");
        }

        BodyPhysicsSettings phys = def.physicsSettings();
        ConstShape shape = shapeFactory.createShape(def.shapeLines());

        BodyCreationSettings settings = new BodyCreationSettings();
        settings.setShape(shape);
        settings.setPosition(pos);
        settings.setRotation(rot);
        settings.setMotionType(phys.motionType());
        settings.setObjectLayer(phys.objectLayer());
        settings.getMassProperties().setMass(phys.mass());
        settings.setFriction(phys.friction());
        settings.setRestitution(phys.restitution());
        settings.setLinearDamping(phys.linearDamping());
        settings.setAngularDamping(phys.angularDamping());

        float adaptiveGravity = calculateGravity(model, totalLength);
        settings.setGravityFactor(adaptiveGravity);

        CollisionGroup group = new CollisionGroup(groupFilter, 0, chainIndex);
        settings.setCollisionGroup(group);

        if (phys.motionType() == com.github.stephengold.joltjni.enumerate.EMotionType.Dynamic) {
            settings.setMotionQuality(EMotionQuality.LinearCast);
        }

        settings.setAllowSleeping(true);

        return settings;
    }

    private void createConstraint(PhysicsBodyRegistry registry, String bodyId, Body parentBody) {
        PhysicsBodyRegistry.BodyModel model = registry.require(bodyId);
        if (!(model.bodyDefinition() instanceof ChainBodyDefinition chainDef)) return;

        double jointOffset = chainDef.creation().jointOffset();
        RVec3 currentPos = getBody().getPosition();
        RVec3 pivotPoint = new RVec3(
                currentPos.xx(),
                currentPos.yy() + jointOffset,
                currentPos.zz()
        );

        SixDofConstraintSettings settings = new SixDofConstraintSettings();
        settings.setSpace(EConstraintSpace.WorldSpace);
        settings.setPosition1(pivotPoint);
        settings.setPosition2(pivotPoint);

        settings.makeFixedAxis(EAxis.TranslationX);
        settings.makeFixedAxis(EAxis.TranslationY);
        settings.makeFixedAxis(EAxis.TranslationZ);

        float swingRad = (float) Math.toRadians(chainDef.limits().swingLimitAngle());
        settings.setLimitedAxis(EAxis.RotationX, -swingRad, swingRad);
        settings.setLimitedAxis(EAxis.RotationZ, -swingRad, swingRad);

        switch (chainDef.limits().twistMode()) {
            case LOCKED -> settings.makeFixedAxis(EAxis.RotationY);
            case LIMITED -> settings.setLimitedAxis(EAxis.RotationY, -0.1f, 0.1f);
            case FREE -> settings.makeFreeAxis(EAxis.RotationY);
        }

        TwoBodyConstraint constraint = settings.create(parentBody, getBody());

        if (calculatedIterations > 0) {
            constraint.setNumPositionStepsOverride(calculatedIterations);
            int velIter = chainDef.stabilization().velocityIterations();
            constraint.setNumVelocityStepsOverride(velIter);
        }

        getSpace().addConstraint(constraint);
        addRelatedConstraint(constraint.toRef());
    }

    @Override public @NotNull String getBodyId() { return bodyId; }
    @Override public @NotNull PhysicsBodyType getType() { return PhysicsBodyType.CHAIN; }
    @Override public void remove() { destroy(); }
    @Override public void destroy() {
        if (removed) return;
        removed = true;
        super.destroy();
    }
    @Override public boolean isValid() { return !removed && getBody() != null; }
    @Override public void teleport(@NotNull Location location) {
        if (!isValid()) return;
        RVec3 pos = new RVec3(location.getX(), location.getY(), location.getZ());
        getSpace().getBodyInterface().setPosition(getBody().getId(), pos, EActivation.Activate);
    }
    @Override public void setLinearVelocity(@NotNull Vector velocity) {
        if (!isValid()) return;
        getSpace().getBodyInterface().setLinearVelocity(getBody().getId(), ConvertUtils.toVec3(velocity));
    }
    @Override public @NotNull Location getLocation() {
        if (!isValid()) return new Location(getSpace().getWorldBukkit(), 0, 0, 0);
        RVec3 pos = getBody().getPosition();
        return new Location(getSpace().getWorldBukkit(), pos.xx(), pos.yy(), pos.zz());
    }
}