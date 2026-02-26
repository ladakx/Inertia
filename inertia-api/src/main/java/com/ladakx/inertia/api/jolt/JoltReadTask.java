package com.ladakx.inertia.api.jolt;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * Physics-thread read-only task.
 */
@FunctionalInterface
@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
public interface JoltReadTask<T> {
    T run(@NotNull JoltReadContext ctx) throws Exception;
}

