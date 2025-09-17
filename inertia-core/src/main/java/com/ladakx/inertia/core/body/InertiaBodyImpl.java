package com.ladakx.inertia.core.body;

import com.github.stephengold.joltjni.Body;
import com.ladakx.inertia.api.body.InertiaBody;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.core.physics.PhysicsManager;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class InertiaBodyImpl implements InertiaBody {

    private final Body joltBody;
    private final PhysicsManager physicsManager;
    private final MotionType motionType;

    // This state is updated by the main thread based on data from the physics thread.
    private final AtomicReference<PhysicsManager.BodyState> currentState;

    public InertiaBodyImpl(Body joltBody, PhysicsManager physicsManager, MotionType motionType) {
        this.joltBody = joltBody;
        this.physicsManager = physicsManager;
        this.motionType = motionType;
        // Initialize with a default state. The real state will be populated by the sync task.
        this.currentState = new AtomicReference<>(new PhysicsManager.BodyState(
                joltBody.getId(),
                new Vector(0, 0, 0),
                new Quaternionf()
        ));
    }

    @Override
    public int getId() {
        return joltBody.getId();
    }

    @Override
    public MotionType getMotionType() {
        return motionType;
    }

    @Override
    public Vector getPosition() {
        return currentState.get().position().clone();
    }

    @Override
    public Quaternionf getRotation() {
        return new Quaternionf(currentState.get().rotation());
    }

    @Override
    public void applyImpulse(Vector impulse) {
        // Implementation will be added in BodyFactory step.
        // This will queue a command in PhysicsManager.
    }

    /**
     * Internal method to update the body's state from the main thread sync task.
     * @param newState The latest state from the physics simulation.
     */
    public void updateState(PhysicsManager.BodyState newState) {
        this.currentState.set(newState);
    }

    public Body getJoltBody() {
        return joltBody;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InertiaBodyImpl that = (InertiaBodyImpl) o;
        return getId() == that.getId();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
