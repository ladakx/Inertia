package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface TransportService {

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    @NotNull ApiResult<TransportHandle> spawn(@NotNull Plugin owner,
                                              @NotNull World world,
                                              @NotNull TransportSpec spec,
                                              @Nullable Map<String, String> customData);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    boolean destroy(@NotNull TransportId id);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void destroyAll(@NotNull Plugin owner);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    boolean setInput(@NotNull TransportId id, @NotNull TransportInput input);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    boolean setTrackedInput(@NotNull TransportId id, @NotNull TrackedInput input);

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @Nullable TransportState getState(@NotNull TransportId id);

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @Nullable TransportHandle get(@NotNull TransportId id);

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @NotNull Collection<TransportHandle> getAll();

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @NotNull Collection<TransportHandle> getByOwner(@NotNull Plugin owner);

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @NotNull Collection<TransportHandle> getByWorld(@NotNull World world);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void setPersistentData(@NotNull Plugin owner,
                           @NotNull TransportId id,
                           @Nullable Map<String, String> customData);

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @NotNull Map<String, String> getPersistentData(@NotNull TransportId id);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    @NotNull ApiResult<Integer> saveOwned(@NotNull Plugin owner, @NotNull String relativePath);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    @NotNull ApiResult<Integer> loadOwned(@NotNull Plugin owner, @NotNull String relativePath);
}
