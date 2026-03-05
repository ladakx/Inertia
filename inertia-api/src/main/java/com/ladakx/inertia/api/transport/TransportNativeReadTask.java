package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
public interface TransportNativeReadTask<T> {
    T run(@NotNull TransportNativeReadContext ctx) throws Exception;
}
