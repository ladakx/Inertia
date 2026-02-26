package com.ladakx.inertia.api.extension;

import com.ladakx.inertia.api.capability.ApiCapability;
import com.ladakx.inertia.api.version.ApiVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Entry point for deep integrations with Inertia.
 * <p>
 * Extensions are registered at runtime via {@link ExtensionRegistry}.
 */
public interface InertiaExtension {

    /**
     * Stable identifier in reverse-domain or plugin-id format (e.g. {@code "com.example.myaddon"}).
     */
    @NotNull String id();

    /**
     * Minimum Inertia API version required by this extension.
     */
    default @NotNull ApiVersion minimumApiVersion() {
        return new ApiVersion(1, 2, 0);
    }

    /**
     * Capabilities required by this extension (checked during registration).
     */
    default @NotNull Set<ApiCapability> requiredCapabilities() {
        return Set.of();
    }

    /**
     * Called once after successful registration.
     */
    void onLoad(@NotNull ExtensionContext context) throws Exception;

    /**
     * Called when the extension is explicitly unregistered or when the owner plugin disables.
     */
    default void onUnload() throws Exception {
    }
}

