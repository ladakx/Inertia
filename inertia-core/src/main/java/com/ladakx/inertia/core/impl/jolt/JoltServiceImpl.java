package com.ladakx.inertia.core.impl.jolt;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.jolt.*;
import com.ladakx.inertia.api.world.PhysicsWorld;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class JoltServiceImpl implements JoltService {

    @Override
    public @NotNull <T> CompletableFuture<T> submitRead(@NotNull PhysicsWorld world, @NotNull JoltReadTask<T> task) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        CompletableFuture<T> future = new CompletableFuture<>();

        com.ladakx.inertia.physics.world.PhysicsWorld space = requireSpace(world);
        space.schedulePhysicsTask(() -> {
            try {
                future.complete(task.run(new ReadContext(space)));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    @Override
    public @NotNull <T> CompletableFuture<T> submitWrite(@NotNull PhysicsWorld world, @NotNull JoltWriteTask<T> task) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        CompletableFuture<T> future = new CompletableFuture<>();

        com.ladakx.inertia.physics.world.PhysicsWorld space = requireSpace(world);
        space.schedulePhysicsTask(() -> {
            try {
                future.complete(task.run(new WriteContext(space)));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private static com.ladakx.inertia.physics.world.PhysicsWorld requireSpace(@NotNull PhysicsWorld world) {
        if (world instanceof com.ladakx.inertia.physics.world.PhysicsWorld space) {
            return space;
        }
        throw new IllegalArgumentException("Unsupported PhysicsWorld implementation: " + world.getClass().getName());
    }

    private static class ReadContext implements JoltReadContext {
        private final com.ladakx.inertia.physics.world.PhysicsWorld space;

        private ReadContext(com.ladakx.inertia.physics.world.PhysicsWorld space) {
            this.space = space;
        }

        @Override
        public @NotNull PhysicsSystem physicsSystem() {
            return space.getPhysicsSystem();
        }

        @Override
        public @NotNull BodyInterface bodyInterface() {
            return space.getBodyInterface();
        }

        @Override
        public @NotNull Body bodyOf(@NotNull PhysicsBody body) {
            Objects.requireNonNull(body, "body");
            if (!body.isValid()) {
                throw new IllegalArgumentException("PhysicsBody is not valid: " + body.getBodyId());
            }
            if (!(body instanceof com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody internal)) {
                throw new IllegalArgumentException("Unsupported PhysicsBody implementation: " + body.getClass().getName());
            }
            if (internal.getSpace() != space) {
                throw new IllegalArgumentException("PhysicsBody does not belong to the provided world");
            }
            return internal.getBody();
        }
    }

    private static final class WriteContext extends ReadContext implements JoltWriteContext {
        private WriteContext(com.ladakx.inertia.physics.world.PhysicsWorld space) {
            super(space);
        }
    }
}
