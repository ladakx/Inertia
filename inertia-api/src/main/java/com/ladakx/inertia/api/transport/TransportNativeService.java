package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Full native transport access for integrations that need parity with jolt-jni vehicle APIs.
 * <p>
 * Tasks execute on the physics thread of the transport's world. Native objects returned in
 * contexts are only valid during task execution and must not be retained.
 */
@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface TransportNativeService {

    @NotNull <T> CompletableFuture<T> submitRead(@NotNull TransportId id,
                                                 @NotNull TransportNativeReadTask<T> task);

    @NotNull <T> CompletableFuture<T> submitWrite(@NotNull TransportId id,
                                                  @NotNull TransportNativeWriteTask<T> task);
}
