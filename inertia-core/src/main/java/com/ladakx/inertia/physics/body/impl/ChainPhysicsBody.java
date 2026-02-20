package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.api.body.type.IChain;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.physics.body.config.BodyPhysicsSettings;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.rendering.staticent.BukkitStaticEntityPersister;
import com.ladakx.inertia.rendering.runtime.PhysicsDisplayComposite;
import com.ladakx.inertia.common.utils.MiscUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChainPhysicsBody extends DisplayedPhysicsBody implements IChain {

    private final String bodyId;
    private boolean removed = false;
    private final int calculatedIterations;
    private final int linkIndex;
    private final int totalChainLength;
    private TwoBodyConstraintRef parentConstraintRef;
    private boolean anchored = false;

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
        this.linkIndex = chainIndex;
        this.totalChainLength = totalChainLength;

        this.calculatedIterations = calculateIterations(modelRegistry.require(bodyId), totalChainLength);

        if (parentBody != null) {
            createConstraint(modelRegistry, bodyId, parentBody);
        }

        this.displayComposite = recreateDisplay();
    }

    public boolean isAnchored() { return anchored; }
    public void setAnchored(boolean anchored) { this.anchored = anchored; }

    @Override
    public String getPartKey() { return String.valueOf(linkIndex); }

    @Override
    protected PhysicsDisplayComposite recreateDisplay() {
        PhysicsBodyRegistry.BodyModel model = modelRegistry.require(getBodyId());
        Optional<RenderModelDefinition> renderOpt = model.renderModel();
        if (renderOpt.isEmpty()) return null;

        RenderModelDefinition renderDef = renderOpt.get();
        World world = getSpace().getWorldBukkit();
        RVec3 currentPos = getBody().getPosition();
        Location spawnLoc = new Location(world, currentPos.xx(), currentPos.yy(), currentPos.zz());

        List<PhysicsDisplayComposite.DisplayPart> parts = new ArrayList<>();
        for (Map.Entry<String, RenderEntityDefinition> entry : renderDef.entities().entrySet()) {
            RenderEntityDefinition entityDef = entry.getValue();
            NetworkVisual visual = renderFactory.create(world, spawnLoc, entityDef);
            parts.add(new PhysicsDisplayComposite.DisplayPart(entityDef, visual));
        }

        var plugin = com.ladakx.inertia.core.InertiaPlugin.getInstance();
        return new PhysicsDisplayComposite(
                this,
                renderDef,
                world,
                parts,
                plugin != null ? plugin.getNetworkEntityTracker() : null,
                new BukkitStaticEntityPersister(plugin != null ? plugin.getItemRegistry() : null)
        );
    }

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
        RVec3 parentPos = parentBody.getPosition();
        double dx = parentPos.xx() - currentPos.xx();
        double dy = parentPos.yy() - currentPos.yy();
        double dz = parentPos.zz() - currentPos.zz();

        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6) {
            dx = 0.0;
            dy = 1.0;
            dz = 0.0;
            len = 1.0;
        }

        double nx = dx / len;
        double ny = dy / len;
        double nz = dz / len;

        RVec3 pivotPoint = new RVec3(
                currentPos.xx() + nx * jointOffset,
                currentPos.yy() + ny * jointOffset,
                currentPos.zz() + nz * jointOffset
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
        this.parentConstraintRef = constraint.toRef();
        addRelatedConstraint(parentConstraintRef);
    }

    @Override
    public int getLinkIndex() {
        return linkIndex;
    }

    @Override
    public int getChainLength() {
        return totalChainLength;
    }

    @Override
    public void breakLink() {
        if (parentConstraintRef != null) {
            TwoBodyConstraint constraint = parentConstraintRef.getPtr();
            if (constraint != null) {
                getSpace().removeConstraint(constraint);
            }
            removeRelatedConstraint(parentConstraintRef);
            parentConstraintRef = null;
        }
    }

    @Override
    public @NotNull String getBodyId() {
        return bodyId;
    }

    @Override
    public @NotNull PhysicsBodyType getType() {
        return PhysicsBodyType.CHAIN;
    }

    @Override
    public void destroy() {
        if (removed) return;
        removed = true;
        super.destroy();
    }
}