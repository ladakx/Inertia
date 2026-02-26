package com.ladakx.inertia.api.jolt;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * Physics-thread write task.
 */
@FunctionalInterface
@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_ONLY)
public interface JoltWriteTask<T> {
    T run(@NotNull JoltWriteContext ctx) throws Exception;
}
