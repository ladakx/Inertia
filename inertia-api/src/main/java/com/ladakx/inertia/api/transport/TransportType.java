package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * Third-party transport type definition.
 * <p>
 * Registered via {@link TransportService#registerType(org.bukkit.plugin.Plugin, TransportType)}.
 */
@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
public interface TransportType {
    @NotNull TransportTypeDescriptor descriptor();

    /**
     * Builds a new transport instance. Called on the main thread.
     */
    @NotNull ApiResult<TransportBuildResult> build(@NotNull TransportBuildContext context) throws Exception;
}

