package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.readonly.ConstShape;
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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChainPhysicsBody extends DisplayedPhysicsBody {

    private final String bodyId;
    private final PhysicsDisplayComposite displayComposite;
    private boolean removed = false;

    public ChainPhysicsBody(@NotNull PhysicsWorld space,
                            @NotNull String bodyId,
                            @NotNull PhysicsBodyRegistry modelRegistry,
                            @NotNull RenderFactory renderFactory,
                            @NotNull JShapeFactory shapeFactory,
                            @NotNull RVec3 initialPosition,
                            @NotNull Quat initialRotation,
                            @Nullable Body parentBody) {
        super(space, createBodySettings(bodyId, modelRegistry, shapeFactory, initialPosition, initialRotation));
        this.bodyId = bodyId;

        if (parentBody != null) {
            createConstraint(modelRegistry, bodyId, parentBody);
        }

        this.displayComposite = createVisuals(space, bodyId, modelRegistry, renderFactory, initialPosition);
    }

    private void createConstraint(PhysicsBodyRegistry registry, String bodyId, Body parentBody) {
        PhysicsBodyRegistry.BodyModel model = registry.require(bodyId);
        if (!(model.bodyDefinition() instanceof ChainBodyDefinition chainDef)) {
            return;
        }

        double jointOffset = chainDef.chainSettings().jointOffset();
        RVec3 currentPos = getBody().getPosition();
        RVec3 pivotPoint = new RVec3(
                currentPos.xx(),
                currentPos.yy() + jointOffset,
                currentPos.zz()
        );

        PointConstraintSettings settings = new PointConstraintSettings();
        settings.setSpace(EConstraintSpace.WorldSpace);
        settings.setPoint1(pivotPoint);
        settings.setPoint2(pivotPoint);

        TwoBodyConstraint constraint = settings.create(parentBody, getBody());
        getSpace().addConstraint(constraint);
        addRelatedConstraint(constraint.toRef());
    }

    private PhysicsDisplayComposite createVisuals(PhysicsWorld space, String bodyId,
                                                  PhysicsBodyRegistry registry, RenderFactory factory,
                                                  RVec3 initialPos) {
        PhysicsBodyRegistry.BodyModel model = registry.require(bodyId);
        Optional<RenderModelDefinition> renderOpt = model.renderModel();

        if (renderOpt.isEmpty()) return null;

        RenderModelDefinition renderDef = renderOpt.get();
        World world = space.getWorldBukkit();
        Location spawnLoc = new Location(world, initialPos.xx(), initialPos.yy(), initialPos.zz());

        List<PhysicsDisplayComposite.DisplayPart> parts = new ArrayList<>();
        for (RenderEntityDefinition entityDef : renderDef.entities().values()) {
            VisualEntity visual = factory.create(world, spawnLoc, entityDef);
            if (visual.isValid()) {
                parts.add(new PhysicsDisplayComposite.DisplayPart(entityDef, visual));
            }
        }
        return new PhysicsDisplayComposite(getBody(), renderDef, world, parts);
    }

    private static BodyCreationSettings createBodySettings(String bodyId,
                                                           PhysicsBodyRegistry registry,
                                                           JShapeFactory shapeFactory,
                                                           RVec3 pos, Quat rot) {
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

        if (phys.motionType() == com.github.stephengold.joltjni.enumerate.EMotionType.Dynamic) {
            settings.setMotionQuality(EMotionQuality.LinearCast);
        }

        return settings;
    }

    @Override
    public @Nullable PhysicsDisplayComposite getDisplay() {
        return displayComposite;
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
    public void remove() {
        destroy();
    }

    @Override
    public void destroy() {
        if (removed) return;
        removed = true;
        super.destroy();
        if (displayComposite != null) {
            displayComposite.destroy();
        }
    }

    @Override
    public boolean isValid() {
        return !removed && getBody() != null;
    }

    @Override
    public void teleport(@NotNull Location location) {
        if (!isValid()) return;
        RVec3 pos = new RVec3(location.getX(), location.getY(), location.getZ());
        getSpace().getBodyInterface().setPosition(getBody().getId(), pos, EActivation.Activate);
    }

    @Override
    public void setLinearVelocity(@NotNull Vector velocity) {
        if (!isValid()) return;
        getSpace().getBodyInterface().setLinearVelocity(getBody().getId(), ConvertUtils.toVec3(velocity));
    }

    @Override
    public @NotNull Location getLocation() {
        if (!isValid()) return new Location(getSpace().getWorldBukkit(), 0, 0, 0);
        RVec3 pos = getBody().getPosition();
        return new Location(getSpace().getWorldBukkit(), pos.xx(), pos.yy(), pos.zz());
    }
}