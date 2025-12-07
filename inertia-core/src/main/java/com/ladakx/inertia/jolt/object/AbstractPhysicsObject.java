package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.api.body.InertiaPhysicsObject;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for Minecraft physics objects backed by a Jolt {@link Body}.
 * <p>
 * This class owns:
 * <ul>
 *     <li>the primary {@link Body} instance,</li>
 *     <li>any additional related bodies (for composite objects),</li>
 *     <li>all {@link TwoBodyConstraintRef} that directly belong to this object.</li>
 * </ul>
 * <p>
 * All Jolt resources are released in {@link #destroy()} in a safe and
 * idempotent way.
 */
public abstract class AbstractPhysicsObject implements InertiaPhysicsObject {

    private final List<Integer> relatedBodies = new CopyOnWriteArrayList<>();
    private final List<TwoBodyConstraintRef> constraints = new CopyOnWriteArrayList<>();

    private final @NotNull MinecraftSpace space;
    private final @NotNull BodyCreationSettings bodySettings;
    private final @NotNull Body body;

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    /**
     * Create a new physics object and register its primary body in the
     * owning {@link MinecraftSpace}.
     *
     * @param space        the owning physics space (not null)
     * @param bodySettings body settings used to create the primary body (not null)
     */
    public AbstractPhysicsObject(@NotNull MinecraftSpace space,
                                 @NotNull BodyCreationSettings bodySettings) {
        this.space = space;
        this.bodySettings = bodySettings;

        this.body = space.getBodyInterface().createBody(this.bodySettings);
        space.getBodyInterface().addBody(body, EActivation.Activate);
        space.addObject(this);
    }

    /**
     * Register an additional body that belongs to this physics object.
     * This is used for composite physics objects that control multiple
     * Jolt bodies.
     *
     * @param related related body to track (not null)
     */
    public void addRelated(@NotNull Body related) {
        if (related == null) {
            InertiaLogger.warn("Attempted to register a null related body in AbstractPhysicsObject.");
            return;
        }
        this.relatedBodies.add(related.getId());
    }

    /**
     * Register a constraint reference that belongs to this physics object.
     * The same native constraint is tracked at most once per object.
     *
     * @param related constraint reference (not null)
     */
    public void addRelatedConstraint(@NotNull TwoBodyConstraintRef related) {
        if (related == null) {
            return;
        }
        TwoBodyConstraint newConstraint = related.getPtr();
        if (newConstraint == null) {
            return;
        }
        long newVa = newConstraint.va();

        for (TwoBodyConstraintRef existing : constraints) {
            TwoBodyConstraint existingConstraint = existing.getPtr();
            if (existingConstraint != null && existingConstraint.va() == newVa) {
                return;
            }
        }

        this.constraints.add(related);
    }

    /**
     * Unregister a constraint reference that belongs to this physics object.
     * Matching is done by the native constraint virtual address, so it works
     * even if a different {@link TwoBodyConstraintRef} wrapper is provided.
     *
     * @param related constraint reference (not null)
     */
    public void removeRelatedConstraint(@NotNull TwoBodyConstraintRef related) {
        if (related == null) {
            return;
        }
        TwoBodyConstraint target = related.getPtr();
        if (target == null) {
            return;
        }
        long targetVa = target.va();

        constraints.removeIf(existing -> {
            TwoBodyConstraint constraint = existing.getPtr();
            return constraint != null && constraint.va() == targetVa;
        });
    }

    /**
     * Returns a snapshot of all constraint references currently associated
     * with this physics object.
     * <p>
     * The returned list is a copy and can be safely iterated without
     * additional synchronization.
     *
     * @return list of constraint references (never null)
     */
    public @NotNull List<TwoBodyConstraintRef> getConstraintSnapshot() {
        return new ArrayList<>(constraints);
    }

    /**
     * Access the primary Jolt body used by this physics object.
     *
     * @return primary {@link Body} (never null)
     */
    public @NotNull Body getBody() {
        return body;
    }

    /**
     * Access the owning physics space.
     *
     * @return owning {@link MinecraftSpace} (never null)
     */
    public @NotNull MinecraftSpace getSpace() {
        return space;
    }

    /**
     * Destroy all Jolt resources owned by this object (constraints and bodies)
     * and unregister this object from its {@link MinecraftSpace}.
     * <p>
     * The method is idempotent: subsequent calls will be no-ops.
     */
    @Override
    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }

        BodyInterface bodyInterface = space.getBodyInterface();
        String worldName = space.getWorldBukkit().getName();

        List<TwoBodyConstraintRef> constraintSnapshot = new ArrayList<>(constraints);
        for (TwoBodyConstraintRef ref : constraintSnapshot) {
            if (ref == null) {
                continue;
            }
            try {
                TwoBodyConstraint constraint = ref.getPtr();
                if (constraint != null) {
                    space.removeConstraint(constraint);
                }
            } catch (Exception e) {
                InertiaLogger.error(
                        "Failed to remove Jolt constraint for physics object in world "
                                + worldName + ": " + e
                );
            }
        }
        constraints.clear();

        List<Integer> relatedSnapshot = new ArrayList<>(relatedBodies);
        for (int relatedId : relatedSnapshot) {
            try {
                bodyInterface.removeBody(relatedId);
            } catch (Exception e) {
                InertiaLogger.warn(
                        "Failed to remove related Jolt body id "
                                + relatedId + " in world " + worldName + ": " + e
                );
            }
            try {
                bodyInterface.destroyBody(relatedId);
            } catch (Exception e) {
                InertiaLogger.warn(
                        "Failed to destroy related Jolt body id "
                                + relatedId + " in world " + worldName + ": " + e
                );
            }
        }
        relatedBodies.clear();

        int mainBodyId = body.getId();
        try {
            bodyInterface.removeBody(mainBodyId);
        } catch (Exception e) {
            InertiaLogger.warn(
                    "Failed to remove main Jolt body id "
                            + mainBodyId + " in world " + worldName + ": " + e
            );
        }
        try {
            bodyInterface.destroyBody(mainBodyId);
        } catch (Exception e) {
            InertiaLogger.warn(
                    "Failed to destroy main Jolt body id "
                            + mainBodyId + " in world " + worldName + ": " + e
            );
        }

        space.removeObject(this);
    }
}