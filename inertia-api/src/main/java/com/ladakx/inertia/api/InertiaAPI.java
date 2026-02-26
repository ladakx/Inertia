package com.ladakx.inertia.api;

import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.capability.ApiCapability;
import com.ladakx.inertia.api.capability.CapabilityService;
import com.ladakx.inertia.api.config.ConfigService;
import com.ladakx.inertia.api.diagnostics.DiagnosticsService;
import com.ladakx.inertia.api.extension.ExtensionRegistry;
import com.ladakx.inertia.api.jolt.JoltService;
import com.ladakx.inertia.api.jolt.JoltServices;
import com.ladakx.inertia.api.rendering.RenderingService;
import com.ladakx.inertia.api.services.ServiceRegistry;
import com.ladakx.inertia.api.transport.TransportService;
import com.ladakx.inertia.api.transport.TransportServices;
import com.ladakx.inertia.api.version.ApiVersion;
import com.ladakx.inertia.api.world.PhysicsWorld;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

/**
 * Stable public API surface for third-party plugins.
 * <p>
 * This interface is designed for long-term binary compatibility.
 */
public interface InertiaApi {

    @NotNull ApiResult<PhysicsBody> createBodyResult(@NotNull Location location, @NotNull String bodyId);

    boolean isWorldSimulated(@NotNull String worldName);

    @Nullable PhysicsWorld getPhysicsWorld(@NotNull World world);

    @NotNull Collection<PhysicsWorld> getAllPhysicsWorlds();

    @NotNull RenderingService rendering();

    @NotNull ConfigService configs();

    @NotNull CapabilityService capabilities();

    @NotNull DiagnosticsService diagnostics();

    @NotNull ApiVersion apiVersion();

    @NotNull ServiceRegistry services();

    @NotNull ExtensionRegistry extensions();

    /**
     * Transport platform service (requires {@link com.ladakx.inertia.api.capability.ApiCapability#TRANSPORT_PLATFORM}).
     */
    default @NotNull TransportService transports() {
        return services().require(TransportServices.TRANSPORTS);
    }

    /**
     * Advanced native Jolt access (requires {@link com.ladakx.inertia.api.capability.ApiCapability#JOLT_NATIVE_ACCESS}).
     */
    default @NotNull JoltService jolt() {
        return services().require(JoltServices.JOLT);
    }

    default boolean isCompatibleWith(@NotNull ApiVersion minimumVersion, @NotNull Collection<ApiCapability> requiredCapabilities) {
        Objects.requireNonNull(minimumVersion, "minimumVersion");
        Objects.requireNonNull(requiredCapabilities, "requiredCapabilities");
        if (!apiVersion().isAtLeast(minimumVersion)) {
            return false;
        }
        for (ApiCapability capability : requiredCapabilities) {
            if (!capabilities().supports(Objects.requireNonNull(capability, "capability"))) {
                return false;
            }
        }
        return true;
    }

    default boolean isCompatibleWith(@NotNull String minimumVersion, @NotNull Collection<ApiCapability> requiredCapabilities) {
        Objects.requireNonNull(minimumVersion, "minimumVersion");
        return isCompatibleWith(ApiVersion.parse(minimumVersion), requiredCapabilities);
    }
}
