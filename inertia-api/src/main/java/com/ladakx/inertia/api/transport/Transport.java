package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Runtime transport instance managed by the platform.
 */
@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface Transport {
    @NotNull UUID id();

    @NotNull TransportTypeKey type();

    /**
     * Plugin that owns this instance (for cleanup and accountability).
     */
    @NotNull Plugin owner();

    /**
     * Primary physics body (usually the chassis/root).
     */
    @NotNull PhysicsBody primaryBody();

    /**
     * All physics bodies that constitute this transport.
     */
    @NotNull List<PhysicsBody> bodies();

    boolean isValid();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void destroy();
}

