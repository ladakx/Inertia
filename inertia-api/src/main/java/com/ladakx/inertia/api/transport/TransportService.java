package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Transport platform API.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>register third-party {@link TransportType} implementations</li>
 *     <li>spawn and lifecycle-manage transport instances</li>
 *     <li>provide safe cleanup on plugin disable</li>
 * </ul>
 */
@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface TransportService {

    @NotNull ApiResult<TransportTypeHandle> registerType(@NotNull Plugin owner, @NotNull TransportType type);

    @NotNull Collection<TransportTypeDescriptor> getRegisteredTypes();

    @Nullable TransportTypeDescriptor getType(@NotNull TransportTypeKey key);

    @NotNull ApiResult<Void> unregisterType(@NotNull TransportTypeKey key);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    @NotNull ApiResult<Transport> spawn(@NotNull Plugin owner, @NotNull TransportSpawnRequest request);

    @Nullable Transport get(@NotNull UUID transportId);

    @NotNull Collection<Transport> getAll();

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    @NotNull ApiResult<Void> destroy(@NotNull UUID transportId);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void destroyAll(@NotNull Plugin owner);
}

