package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * Optional per-tick controller for a transport instance.
 * <p>
 * Called on the main thread. Keep logic lightweight; heavy work should be moved off-thread.
 */
@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
public interface TransportController {
    void tick(@NotNull Transport transport, @NotNull TransportTickContext context) throws Exception;
}

