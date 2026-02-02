package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.api.events.PhysicsBodyDestroyEvent;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.utils.ConvertUtils;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractPhysicsBody implements InertiaPhysicsBody {

    private final List<Integer> relatedBodies = new CopyOnWriteArrayList<>();
    private final List<TwoBodyConstraintRef> constraints = new CopyOnWriteArrayList<>();
    private final @NotNull PhysicsWorld space;
    private final @NotNull BodyCreationSettings bodySettings;
    private final @NotNull Body body;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final java.util.UUID uuid = java.util.UUID.randomUUID();

    public AbstractPhysicsBody(@NotNull PhysicsWorld space,
                               @NotNull BodyCreationSettings bodySettings) {
        this.space = space;
        this.bodySettings = bodySettings;
        this.body = space.getBodyInterface().createBody(this.bodySettings);
        space.getBodyInterface().addBody(body, EActivation.Activate);
        space.addObject(this);
    }

    public void addRelated(@NotNull Body related) {
        if (related == null) {
            InertiaLogger.warn("Attempted to register a null related body in AbstractPhysicsBody.");
            return;
        }
        this.relatedBodies.add(related.getId());
    }

    public void addRelatedConstraint(@NotNull TwoBodyConstraintRef related) {
        if (related == null) return;
        TwoBodyConstraint newConstraint = related.getPtr();
        if (newConstraint == null) return;
        long newVa = newConstraint.va();
        for (TwoBodyConstraintRef existing : constraints) {
            TwoBodyConstraint existingConstraint = existing.getPtr();
            if (existingConstraint != null && existingConstraint.va() == newVa) {
                return;
            }
        }
        this.constraints.add(related);
    }

    public void removeRelatedConstraint(@NotNull TwoBodyConstraintRef related) {
        if (related == null) return;
        TwoBodyConstraint target = related.getPtr();
        if (target == null) return;
        long targetVa = target.va();
        constraints.removeIf(existing -> {
            TwoBodyConstraint constraint = existing.getPtr();
            return constraint != null && constraint.va() == targetVa;
        });
    }

    public @NotNull List<TwoBodyConstraintRef> getConstraintSnapshot() {
        return new ArrayList<>(constraints);
    }

    public @NotNull Body getBody() {
        return body;
    }

    public @NotNull PhysicsWorld getSpace() {
        return space;
    }

    public java.util.UUID getUuid() {
        return uuid;
    }

    @Override
    public boolean isValid() {
        return !destroyed.get() && getBody() != null;
    }

    @Override
    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
            Bukkit.getPluginManager().callEvent(new PhysicsBodyDestroyEvent(this));
        });

        BodyInterface bodyInterface = space.getBodyInterface();
        String worldName = space.getWorldBukkit().getName();

        // 1. Constraints
        List<TwoBodyConstraintRef> constraintSnapshot = new ArrayList<>(constraints);
        for (TwoBodyConstraintRef ref : constraintSnapshot) {
            if (ref == null) continue;
            try {
                TwoBodyConstraint constraint = ref.getPtr();
                if (constraint != null) {
                    space.removeConstraint(constraint);
                }
            } catch (Exception e) {
                InertiaLogger.error("Failed to remove Jolt constraint for physics object in world " + worldName + ": " + e);
            }
        }
        constraints.clear();

        // 2. Related Bodies
        List<Integer> relatedSnapshot = new ArrayList<>(relatedBodies);
        for (int relatedId : relatedSnapshot) {
            try {
                bodyInterface.removeBody(relatedId);
            } catch (Exception e) {
                InertiaLogger.warn("Failed to remove related Jolt body id " + relatedId + " in world " + worldName + ": " + e);
            }
            try {
                bodyInterface.destroyBody(relatedId);
            } catch (Exception e) {
                InertiaLogger.warn("Failed to destroy related Jolt body id " + relatedId + " in world " + worldName + ": " + e);
            }
        }
        relatedBodies.clear();

        // 3. Main Body
        int mainBodyId = body.getId();
        try {
            bodyInterface.removeBody(mainBodyId);
        } catch (Exception e) {
            InertiaLogger.warn("Failed to remove main Jolt body id " + mainBodyId + " in world " + worldName + ": " + e);
        }
        try {
            bodyInterface.destroyBody(mainBodyId);
        } catch (Exception e) {
            InertiaLogger.warn("Failed to destroy main Jolt body id " + mainBodyId + " in world " + worldName + ": " + e);
        }

        space.removeObject(this);
    }

    @Override
    public @NotNull Location getLocation() {
        if (!isValid()) return new Location(space.getWorldBukkit(), 0, 0, 0);
        RVec3 pos = body.getPosition();
        return space.toBukkit(pos); // Use Space conversion
    }

    @Override
    public void teleport(@NotNull Location location) {
        if (!isValid()) return;
        RVec3 pos = space.toJolt(location); // Use Space conversion
        space.getBodyInterface().setPosition(body.getId(), pos, EActivation.Activate);
    }

    @Override
    public void move(@NotNull Vector offset) {
        if (!isValid()) return;
        RVec3 current = body.getPosition();
        // Offset is relative, no origin shift needed for addition
        RVec3 newPos = new RVec3(current.xx() + offset.getX(), current.yy() + offset.getY(), current.zz() + offset.getZ());
        space.getBodyInterface().setPosition(body.getId(), newPos, EActivation.Activate);
    }

    @Override
    public @NotNull Vector getLinearVelocity() {
        if (!isValid()) return new Vector();
        return ConvertUtils.toBukkit(space.getBodyInterface().getLinearVelocity(body.getId()));
    }

    @Override
    public void setLinearVelocity(@NotNull Vector velocity) {
        if (!isValid()) return;
        space.getBodyInterface().setLinearVelocity(body.getId(), ConvertUtils.toVec3(velocity));
    }

    @Override
    public @NotNull Vector getAngularVelocity() {
        if (!isValid()) return new Vector();
        return ConvertUtils.toBukkit(space.getBodyInterface().getAngularVelocity(body.getId()));
    }

    @Override
    public void setAngularVelocity(@NotNull Vector velocity) {
        if (!isValid()) return;
        space.getBodyInterface().setAngularVelocity(body.getId(), ConvertUtils.toVec3(velocity));
    }

    @Override
    public void addImpulse(@NotNull Vector impulse) {
        if (!isValid()) return;
        space.getBodyInterface().addImpulse(body.getId(), ConvertUtils.toVec3(impulse));
    }

    @Override
    public void addTorque(@NotNull Vector torque) {
        if (!isValid()) return;
        space.getBodyInterface().addTorque(body.getId(), ConvertUtils.toVec3(torque));
    }

    @Override
    public void setFriction(float friction) {
        if (!isValid()) return;
        space.getBodyInterface().setFriction(body.getId(), friction);
    }

    @Override
    public float getFriction() {
        if (!isValid()) return 0f;
        return space.getBodyInterface().getFriction(body.getId());
    }

    @Override
    public void setRestitution(float restitution) {
        if (!isValid()) return;
        space.getBodyInterface().setRestitution(body.getId(), restitution);
    }

    @Override
    public float getRestitution() {
        if (!isValid()) return 0f;
        return space.getBodyInterface().getRestitution(body.getId());
    }

    @Override
    public void setGravityFactor(float factor) {
        if (!isValid()) return;
        space.getBodyInterface().setGravityFactor(body.getId(), factor);
    }

    @Override
    public float getGravityFactor() {
        if (!isValid()) return 1f;
        return space.getBodyInterface().getGravityFactor(body.getId());
    }

    @Override
    public void activate() {
        if (!isValid()) return;
        space.getBodyInterface().activateBody(body.getId());
    }

    @Override
    public void deactivate() {
        if (!isValid()) return;
        space.getBodyInterface().deactivateBody(body.getId());
    }

    @Override
    public boolean isActive() {
        if (!isValid()) return false;
        return space.getBodyInterface().isActive(body.getId());
    }

    @Override
    public void setMotionType(@NotNull MotionType motionType) {
        if (!isValid()) return;
        com.github.stephengold.joltjni.enumerate.EMotionType joltType = switch (motionType) {
            case STATIC -> com.github.stephengold.joltjni.enumerate.EMotionType.Static;
            case KINEMATIC -> com.github.stephengold.joltjni.enumerate.EMotionType.Kinematic;
            case DYNAMIC -> com.github.stephengold.joltjni.enumerate.EMotionType.Dynamic;
        };
        space.getBodyInterface().setMotionType(body.getId(), joltType, EActivation.Activate);
    }

    @Override
    public @NotNull MotionType getMotionType() {
        if (!isValid()) return MotionType.STATIC;
        com.github.stephengold.joltjni.enumerate.EMotionType type = space.getBodyInterface().getMotionType(body.getId());
        if (type == com.github.stephengold.joltjni.enumerate.EMotionType.Static) return MotionType.STATIC;
        if (type == com.github.stephengold.joltjni.enumerate.EMotionType.Kinematic) return MotionType.KINEMATIC;
        return MotionType.DYNAMIC;
    }
}