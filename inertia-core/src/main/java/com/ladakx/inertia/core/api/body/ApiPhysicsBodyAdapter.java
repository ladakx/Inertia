package com.ladakx.inertia.core.api.body;

import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ApiPhysicsBodyAdapter {
    private final ConcurrentMap<String, PhysicsBody> cache = new ConcurrentHashMap<>();

    public @NotNull PhysicsBody adapt(@NotNull InertiaPhysicsBody body) {
        Objects.requireNonNull(body, "body");
        return cache.compute(body.getBodyId(), (key, existing) -> {
            if (existing instanceof DelegatingApiPhysicsBody delegating && delegating.delegate == body) {
                return existing;
            }
            return new DelegatingApiPhysicsBody(body);
        });
    }

    private static final class DelegatingApiPhysicsBody implements PhysicsBody {
        private final InertiaPhysicsBody delegate;

        private DelegatingApiPhysicsBody(@NotNull InertiaPhysicsBody delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public @NotNull String getBodyId() { return delegate.getBodyId(); }

        @Override
        public @NotNull PhysicsBodyType getType() { return delegate.getType(); }

        @Override
        public boolean isValid() { return delegate.isValid(); }

        @Override
        public void destroy() { delegate.destroy(); }

        @Override
        public @NotNull Location getLocation() { return delegate.getLocation(); }

        @Override
        public void teleport(@NotNull Location location) { delegate.teleport(Objects.requireNonNull(location, "location")); }

        @Override
        public void move(@NotNull Vector offset) { delegate.move(Objects.requireNonNull(offset, "offset")); }

        @Override
        public @NotNull Vector getLinearVelocity() { return delegate.getLinearVelocity(); }

        @Override
        public void setLinearVelocity(@NotNull Vector velocity) { delegate.setLinearVelocity(Objects.requireNonNull(velocity, "velocity")); }

        @Override
        public @NotNull Vector getAngularVelocity() { return delegate.getAngularVelocity(); }

        @Override
        public void setAngularVelocity(@NotNull Vector velocity) { delegate.setAngularVelocity(Objects.requireNonNull(velocity, "velocity")); }

        @Override
        public void addImpulse(@NotNull Vector impulse) { delegate.addImpulse(Objects.requireNonNull(impulse, "impulse")); }

        @Override
        public void addTorque(@NotNull Vector torque) { delegate.addTorque(Objects.requireNonNull(torque, "torque")); }

        @Override
        public void setFriction(float friction) { delegate.setFriction(friction); }

        @Override
        public float getFriction() { return delegate.getFriction(); }

        @Override
        public void setRestitution(float restitution) { delegate.setRestitution(restitution); }

        @Override
        public float getRestitution() { return delegate.getRestitution(); }

        @Override
        public void setGravityFactor(float factor) { delegate.setGravityFactor(factor); }

        @Override
        public float getGravityFactor() { return delegate.getGravityFactor(); }

        @Override
        public void activate() { delegate.activate(); }

        @Override
        public void deactivate() { delegate.deactivate(); }

        @Override
        public boolean isActive() { return delegate.isActive(); }

        @Override
        public void setMotionType(@NotNull MotionType motionType) { delegate.setMotionType(Objects.requireNonNull(motionType, "motionType")); }

        @Override
        public @NotNull MotionType getMotionType() { return delegate.getMotionType(); }
    }
}
