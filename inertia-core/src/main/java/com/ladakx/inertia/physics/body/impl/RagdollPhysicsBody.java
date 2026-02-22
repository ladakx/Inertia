package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.api.body.type.IRagdoll;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.physics.body.config.RagdollDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.rendering.staticent.BukkitStaticEntityPersister;
import com.ladakx.inertia.rendering.runtime.PhysicsDisplayComposite;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RagdollPhysicsBody extends DisplayedPhysicsBody implements IRagdoll {

    private final String bodyId;
    private final String partName;
    private final @Nullable String skinNickname;
    private boolean removed = false;
    private final CollisionGroup collisionGroup;
    private final GroupFilterTableRef groupFilterRef;
    private TwoBodyConstraintRef parentJointRef;
    private Integer parentBodyId = null;

    public RagdollPhysicsBody(@NotNull PhysicsWorld space,
                              @NotNull String bodyId,
                              @NotNull String partName,
                              @NotNull PhysicsBodyRegistry modelRegistry,
                              @NotNull RenderFactory renderFactory,
                              @NotNull JShapeFactory shapeFactory,
                              @NotNull RVec3 initialPosition,
                              @NotNull Quat initialRotation,
                              @NotNull Map<String, Body> spawnedParts,
                              @NotNull GroupFilterTableRef groupFilter,
                              int groupId,
                              int partIndex,
                              @Nullable String skinNickname) {
        super(space, createBodySettings(bodyId, partName, modelRegistry, shapeFactory, initialPosition, initialRotation, groupFilter, groupId, partIndex), renderFactory, modelRegistry);
        this.bodyId = bodyId;
        this.partName = partName;
        this.skinNickname = (skinNickname == null || skinNickname.isBlank()) ? null : skinNickname;
        this.collisionGroup = getBody().getCollisionGroup();
        this.groupFilterRef = groupFilter;

        RagdollDefinition def = (RagdollDefinition) modelRegistry.require(bodyId).bodyDefinition();
        RagdollDefinition.RagdollPartDefinition partDef = def.parts().get(partName);

        if (partDef.parentName() != null) {
            Body parentBody = spawnedParts.get(partDef.parentName());
            if (parentBody != null) {
                this.parentBodyId = parentBody.getId();
                createConstraint(partDef, parentBody, getBody(), groupFilter);
            }
        }

        this.displayComposite = recreateDisplay();
    }

    public @Nullable String getSkinNickname() { return skinNickname; }

    @Override
    public String getPartKey() { return partName; }

    @Override
    protected PhysicsDisplayComposite recreateDisplay() {
        PhysicsBodyRegistry.BodyModel model = modelRegistry.require(bodyId);
        String renderModelId = ((RagdollDefinition) model.bodyDefinition()).parts().get(partName).renderModelId();
        if (renderModelId == null) return null;

        var renderConfig = InertiaPlugin.getInstance().getConfigManager().getRenderConfig();
        var renderDefOpt = renderConfig.find(renderModelId);
        if (renderDefOpt.isEmpty()) return null;

        RenderModelDefinition renderDef = renderDefOpt.get();
        World world = getSpace().getWorldBukkit();
        RVec3 currentPos = getBody().getPosition();
        Location spawnLoc = new Location(world, currentPos.xx(), currentPos.yy(), currentPos.zz());

        List<PhysicsDisplayComposite.DisplayPart> parts = new ArrayList<>();
        for (java.util.Map.Entry<String, RenderEntityDefinition> entry : renderDef.entities().entrySet()) {
            String entityKey = entry.getKey();
            RenderEntityDefinition entityDef = withSkin(entry.getValue());
            NetworkVisual visual = renderFactory.create(world, spawnLoc, entityDef);
            parts.add(new PhysicsDisplayComposite.DisplayPart(entityDef, visual));
        }

        var plugin = InertiaPlugin.getInstance();
        return new PhysicsDisplayComposite(
                this,
                renderDef,
                world,
                parts,
                plugin != null ? plugin.getNetworkEntityTracker() : null,
                new BukkitStaticEntityPersister(plugin != null ? plugin.getItemRegistry() : null)
        );
    }

    private RenderEntityDefinition withSkin(RenderEntityDefinition def) {
        if (skinNickname == null || skinNickname.isBlank()) return def;
        if (def.itemModelKey() == null || def.itemModelKey().isBlank()) return def;
        if (def.itemModelKey().contains("@skin=")) return def;

        String itemKeyWithSkin = def.itemModelKey() + "@skin=" + skinNickname;
        return new RenderEntityDefinition(
                def.key(),
                def.kind(),
                itemKeyWithSkin,
                def.blockType(),
                def.displayMode(),
                def.localOffset(),
                def.localRotation(),
                def.scale(),
                def.translation(),
                def.showWhenActive(),
                def.showWhenSleeping(),
                def.rotTranslation(),
                def.viewRange(),
                def.shadowRadius(),
                def.shadowStrength(),
                def.interpolationDuration(),
                def.teleportDuration(),
                def.billboard(),
                def.brightnessBlock(),
                def.brightnessSky(),
                def.small(),
                def.invisible(),
                def.marker(),
                def.basePlate(),
                def.arms(),
                def.settings()
        );
    }

    private static BodyCreationSettings createBodySettings(String bodyId, String partName,
                                                           PhysicsBodyRegistry registry,
                                                           JShapeFactory shapeFactory,
                                                           RVec3 pos, Quat rot,
                                                           GroupFilterTableRef groupFilter,
                                                           int groupId,
                                                           int partIndex) {
        RagdollDefinition def = (RagdollDefinition) registry.require(bodyId).bodyDefinition();
        RagdollDefinition.RagdollPartDefinition partDef = def.parts().get(partName);

        ConstShape shape;
        if (partDef.shapeString() != null && !partDef.shapeString().isEmpty()) {
            shape = shapeFactory.createShape(List.of(partDef.shapeString()));
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

        CollisionGroup group = new CollisionGroup(groupFilter, groupId, partIndex);
        settings.setCollisionGroup(group);

        settings.getMassProperties().setMass(partDef.mass());
        RagdollDefinition.PartPhysicsSettings phys = partDef.physics();
        settings.setLinearDamping(phys.linearDamping());
        settings.setAngularDamping(phys.angularDamping());
        settings.setFriction(phys.friction());
        settings.setRestitution(phys.restitution());
        settings.setGravityFactor(phys.gravityFactor());
        settings.setMotionQuality(EMotionQuality.LinearCast);
        settings.setAllowSleeping(true);

        return settings;
    }

    private void createConstraint(RagdollDefinition.RagdollPartDefinition partDef, Body parentBody, Body childBody, GroupFilterTableRef groupFilter) {
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
            try {
                settings.makeFixedAxis(EAxis.valueOf(axis));
            } catch (Exception ignored) {
            }
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

        this.parentJointRef = constraint.toRef();
        addRelatedConstraint(parentJointRef);
    }

    @Override
    public @NotNull String getPartName() {
        return partName;
    }

    @Override
    public @Nullable InertiaPhysicsBody getParentPart() {
        if (parentBodyId == null) return null;
        com.github.stephengold.joltjni.readonly.ConstBody body = getSpace().getBodyById(parentBodyId);
        if (body != null) {
            return getSpace().getObjectByVa(body.targetVa());
        }
        return null;
    }

    @Override
    public void detachFromParent() {
        if (parentJointRef != null) {
            TwoBodyConstraint constraint = parentJointRef.getPtr();
            if (constraint != null) {
                getSpace().removeConstraint(constraint);
            }
            removeRelatedConstraint(parentJointRef);
            parentJointRef = null;
            parentBodyId = null;
        }
    }

    @Override
    public @NotNull String getBodyId() {
        return bodyId;
    }

    @Override
    public @NotNull PhysicsBodyType getType() {
        return PhysicsBodyType.RAGDOLL;
    }

    @Override
    public void destroy() {
        if (removed) return;
        removed = true;
        super.destroy();
    }
}
