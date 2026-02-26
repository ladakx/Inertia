package com.ladakx.inertia.api.jolt;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;

/**
 * Write-capable Jolt context.
 * <p>
 * This context is only valid within the scope of a {@link JoltWriteTask}.
 */
@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_ONLY)
public interface JoltWriteContext extends JoltReadContext {
}
