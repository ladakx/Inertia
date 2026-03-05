package com.ladakx.inertia.api;

import com.ladakx.inertia.api.body.PhysicsBodiesService;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.body.PhysicsBodyServices;
import com.ladakx.inertia.api.capability.ApiCapability;
import com.ladakx.inertia.api.capability.CapabilityService;
import com.ladakx.inertia.api.config.ConfigService;
import com.ladakx.inertia.api.diagnostics.DiagnosticsService;
import com.ladakx.inertia.api.extension.ExtensionRegistry;
import com.ladakx.inertia.api.jolt.JoltService;
import com.ladakx.inertia.api.jolt.JoltServices;
import com.ladakx.inertia.api.player.PlayerToolServices;
import com.ladakx.inertia.api.player.PlayerToolsService;
import com.ladakx.inertia.api.rendering.RenderingService;
import com.ladakx.inertia.api.rendering.interaction.RenderInteractionService;
import com.ladakx.inertia.api.rendering.interaction.RenderInteractionServices;
import com.ladakx.inertia.api.rendering.model.RenderModelRegistryService;
import com.ladakx.inertia.api.rendering.model.RenderingModelServices;
import com.ladakx.inertia.api.rendering.transform.RenderTransformService;
import com.ladakx.inertia.api.rendering.transform.RenderingTransformServices;
import com.ladakx.inertia.api.services.ServiceRegistry;
import com.ladakx.inertia.api.transport.TransportService;
import com.ladakx.inertia.api.transport.TransportAdvancedService;
import com.ladakx.inertia.api.transport.TransportAdvancedServices;
import com.ladakx.inertia.api.transport.TransportNativeService;
import com.ladakx.inertia.api.transport.TransportNativeServices;
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
     * Advanced native Jolt access (requires {@link com.ladakx.inertia.api.capability.ApiCapability#JOLT_NATIVE_ACCESS}).
     */
    default @NotNull JoltService jolt() {
        return services().require(JoltServices.JOLT);
    }

    /**
     * Physics body facade for external plugins (ownership + safe cleanup).
     */
    default @NotNull PhysicsBodiesService bodies() {
        return services().require(PhysicsBodyServices.BODIES);
    }

    /**
     * Runtime registry for render models (config/programmatic integrations).
     */
    default @NotNull RenderModelRegistryService renderModels() {
        return services().require(RenderingModelServices.MODELS);
    }

    /**
     * Registry and resolver for pluggable render transform algorithms.
     */
    default @NotNull RenderTransformService renderTransforms() {
        return services().require(RenderingTransformServices.TRANSFORMS);
    }

    /**
     * Resolver and event bridge for packet-render entity interactions.
     */
    default @NotNull RenderInteractionService renderInteractions() {
        return services().require(RenderInteractionServices.INTERACTION);
    }

    /**
     * Transport domain facade (wheeled/tracked/motorcycle vehicles).
     */
    default @NotNull TransportService transport() {
        return services().require(TransportServices.TRANSPORT);
    }

    /**
     * Advanced low-level transport controls and telemetry.
     */
    default @NotNull TransportAdvancedService transportAdvanced() {
        return services().require(TransportAdvancedServices.ADVANCED);
    }

    /**
     * Full native transport access (task-scoped jolt-jni vehicle objects).
     */
    default @NotNull TransportNativeService transportNative() {
        return services().require(TransportNativeServices.NATIVE);
    }

    /**
     * Public player tools facade (camera rotation + sound playback).
     */
    default @NotNull PlayerToolsService playerTools() {
        return services().require(PlayerToolServices.TOOLS);
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
