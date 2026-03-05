package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;

@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_ONLY)
public interface TransportNativeWriteContext extends TransportNativeReadContext {
}
