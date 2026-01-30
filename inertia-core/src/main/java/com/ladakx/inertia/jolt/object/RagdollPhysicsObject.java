package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.config.ConfigManager;
import com.ladakx.inertia.jolt.shape.JShapeFactory;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.nms.render.RenderFactory;
import com.ladakx.inertia.nms.render.runtime.VisualObject;
import com.ladakx.inertia.physics.body.config.RagdollDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.render.config.RenderEntityDefinition;
import com.ladakx.inertia.render.config.RenderModelDefinition;
import com.ladakx.inertia.render.runtime.PhysicsDisplayComposite;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RagdollPhysicsObject extends DisplayedPhysicsObject {

    private final String bodyId;
    private final PhysicsDisplayComposite displayComposite;
    private boolean removed = false;
    private final CollisionGroup collisionGroup;

    public RagdollPhysicsObject(@NotNull MinecraftSpace space,
                                @NotNull String bodyId,
                                @NotNull String partName,
                                @NotNull PhysicsBodyRegistry modelRegistry,
                                @NotNull RenderFactory renderFactory,
                                @NotNull RVec3 initialPosition,
                                @NotNull Quat initialRotation,
                                @NotNull Map<String, Body> spawnedParts,
                                @NotNull GroupFilterTable groupFilter,
                                int partIndex
    ) {
        super(space, createBodySettings(bodyId, partName, modelRegistry, initialPosition, initialRotation, groupFilter, partIndex));
        this.bodyId = bodyId;
        this.collisionGroup = getBody().getCollisionGroup();

        RagdollDefinition def = (RagdollDefinition) modelRegistry.require(bodyId).bodyDefinition();
        RagdollDefinition.RagdollPartDefinition partDef = def.parts().get(partName);

        if (partDef.parentName() != null) {
            Body parentBody = spawnedParts.get(partDef.parentName());
            if (parentBody != null) {
                createConstraint(partDef, parentBody, getBody(), groupFilter);
            }
        }

        this.displayComposite = createVisuals(space, bodyId, partName, modelRegistry, renderFactory, initialPosition);
    }

    private static BodyCreationSettings createBodySettings(String bodyId, String partName,
                                                           PhysicsBodyRegistry registry,
                                                           RVec3 pos, Quat rot,
                                                           GroupFilterTable groupFilter,
                                                           int partIndex) {
        RagdollDefinition def = (RagdollDefinition) registry.require(bodyId).bodyDefinition();
        RagdollDefinition.RagdollPartDefinition partDef = def.parts().get(partName);

        ConstShape shape;
        if (partDef.shapeString() != null && !partDef.shapeString().isEmpty()) {
            shape = JShapeFactory.createShape(List.of(partDef.shapeString()));
        } else {
            Vector size = partDef.size();
            shape = new BoxShape(new Vec3((float) size.getX() / 2f, (float) size.getY() / 2f, (float) size.getZ() / 2f));
        }

        BodyCreationSettings settings = new BodyCreationSettings();
        settings.setShape(shape);
        settings.setPosition(pos);
        settings.setRotation(rot);
        settings.setMotionType(EMotionType.Dynamic);
        settings.setObjectLayer(0);

        CollisionGroup group = new CollisionGroup(groupFilter, 0, partIndex);
        settings.setCollisionGroup(group);
        settings.getMassProperties().setMass(partDef.mass());

        RagdollDefinition.PartPhysicsSettings phys = partDef.physics();
        settings.setLinearDamping(phys.linearDamping());
        settings.setAngularDamping(phys.angularDamping());
        settings.setFriction(phys.friction());
        settings.setRestitution(phys.restitution());

        settings.setMotionQuality(EMotionQuality.LinearCast);
        settings.setAllowSleeping(true);

        return settings;
    }

    private void createConstraint(RagdollDefinition.RagdollPartDefinition partDef, Body parentBody, Body childBody, GroupFilterTable groupFilter) {
        RagdollDefinition.JointSettings joint = partDef.joint();
        if (joint == null) return;

        if (!partDef.collideWithParent()) {
            int parentSubId = parentBody.getCollisionGroup().getSubGroupId();
            int childSubId = childBody.getCollisionGroup().getSubGroupId();
            groupFilter.disableCollision(parentSubId, childSubId);
        }

        RVec3 parentPos = parentBody.getPosition();
        Quat parentRot = parentBody.getRotation();
        RVec3 childPos = childBody.getPosition();
        Quat childRot = childBody.getRotation();

        RVec3 pivot1 = new RVec3(joint.pivotOnParent().getX(), joint.pivotOnParent().getY(), joint.pivotOnParent().getZ());
        pivot1.rotateInPlace(parentRot);
        pivot1.addInPlace(parentPos.xx(), parentPos.yy(), parentPos.zz());

        RVec3 pivot2 = new RVec3(joint.pivotOnChild().getX(), joint.pivotOnChild().getY(), joint.pivotOnChild().getZ());
        pivot2.rotateInPlace(childRot);
        pivot2.addInPlace(childPos.xx(), childPos.yy(), childPos.zz());

        SixDofConstraintSettings settings = new SixDofConstraintSettings();

        for (String axis : joint.fixedAxes()) {
            try { settings.makeFixedAxis(EAxis.valueOf(axis)); } catch (Exception ignored) {}
        }

        float defaultLimit = (float) Math.PI - 0.2f;

        if (!joint.limits().containsKey(EAxis.RotationX) && !joint.fixedAxes().contains("RotationX"))
            settings.setLimitedAxis(EAxis.RotationX, -defaultLimit, defaultLimit);
        if (!joint.limits().containsKey(EAxis.RotationY) && !joint.fixedAxes().contains("RotationY"))
            settings.setLimitedAxis(EAxis.RotationY, -defaultLimit, defaultLimit);
        if (!joint.limits().containsKey(EAxis.RotationZ) && !joint.fixedAxes().contains("RotationZ"))
            settings.setLimitedAxis(EAxis.RotationZ, -defaultLimit, defaultLimit);

        joint.limits().forEach((axis, limit) -> {
            settings.setLimitedAxis(axis, limit.min(), limit.max());
        });

        settings.setPosition1(pivot1);
        settings.setPosition2(pivot2);

        TwoBodyConstraint constraint = settings.create(parentBody, childBody);
        constraint.setNumVelocityStepsOverride(15);
        constraint.setNumPositionStepsOverride(5);

        getSpace().addConstraint(constraint);
        addRelatedConstraint(constraint.toRef());
    }

    private PhysicsDisplayComposite createVisuals(MinecraftSpace space, String bodyId, String partName,
                                                  PhysicsBodyRegistry registry, RenderFactory factory,
                                                  RVec3 initialPos) {
        PhysicsBodyRegistry.BodyModel model = registry.require(bodyId);
        String renderModelId = ((RagdollDefinition)model.bodyDefinition()).parts().get(partName).renderModelId();
        if (renderModelId == null) return null;

        var renderConfig = InertiaPlugin.getInstance().getConfigManager().getRenderConfig();
        var renderDefOpt = renderConfig.find(renderModelId);

        if (renderDefOpt.isEmpty()) return null;
        RenderModelDefinition renderDef = renderDefOpt.get();

        World world = space.getWorldBukkit();
        Location spawnLoc = new Location(world, initialPos.xx(), initialPos.yy(), initialPos.zz());

        List<PhysicsDisplayComposite.DisplayPart> parts = new ArrayList<>();
        for (RenderEntityDefinition entityDef : renderDef.entities().values()) {
            VisualObject visual = factory.create(world, spawnLoc, entityDef);
            if (visual.isValid()) {
                parts.add(new PhysicsDisplayComposite.DisplayPart(entityDef, visual));
            }
        }
        return new PhysicsDisplayComposite(getBody(), renderDef, world, parts);
    }

    @Override public @Nullable PhysicsDisplayComposite getDisplay() { return displayComposite; }
    @Override public @NotNull String getBodyId() { return bodyId; }
    @Override public @NotNull com.ladakx.inertia.jolt.object.PhysicsObjectType getType() { return com.ladakx.inertia.jolt.object.PhysicsObjectType.RAGDOLL; }
    @Override public void remove() { destroy(); }
    @Override public void destroy() { if (removed) return; removed = true; super.destroy(); if (displayComposite != null) displayComposite.destroy(); }
    @Override public boolean isValid() { return !removed && getBody() != null; }
    @Override public void teleport(@NotNull Location location) {}
    @Override public void setLinearVelocity(@NotNull Vector velocity) {}
    @Override public @NotNull Location getLocation() {
        RVec3 pos = getBody().getPosition();
        return new Location(getSpace().getWorldBukkit(), pos.xx(), pos.yy(), pos.zz());
    }
}