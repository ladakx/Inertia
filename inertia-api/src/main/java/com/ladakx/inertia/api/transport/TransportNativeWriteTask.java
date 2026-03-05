package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_ONLY)
public interface TransportNativeWriteTask<T> {
    T run(@NotNull TransportNativeWriteContext ctx) throws Exception;
}
