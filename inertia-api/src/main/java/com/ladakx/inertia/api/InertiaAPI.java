package com.ladakx.inertia.api;

import com.ladakx.inertia.api.world.IPhysicsWorld;
import com.ladakx.inertia.api.capability.ApiCapability;
import com.ladakx.inertia.api.capability.CapabilityService;
import com.ladakx.inertia.api.config.ConfigService;
import com.ladakx.inertia.api.rendering.RenderingService;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.version.ApiVersion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class InertiaAPI {
    private static final Logger LOGGER = Logger.getLogger(InertiaAPI.class.getName());
    private static volatile InertiaApiResolver resolver = new BukkitInertiaApiResolver();

    @Deprecated(forRemoval = false)
    public static InertiaAPI get() {
        LOGGER.log(Level.WARNING, "Legacy InertiaAPI.get() access detected. Switch to Bukkit ServicesManager + InertiaApiProvider.");
        return resolve();
    }

    public static @NotNull InertiaAPI resolve() {
        InertiaApiProvider provider = resolver.resolveProvider();
        if (provider == null) {
            throw new InertiaApiUnavailableException("Inertia API service is unavailable.");
        }
        return Objects.requireNonNull(provider.getApi(), "provider.getApi()");
    }

    static void setResolver(@NotNull InertiaApiResolver resolver) {
        InertiaAPI.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    static void resetResolver() {
        resolver = new BukkitInertiaApiResolver();
    }

    public abstract @NotNull ApiResult<PhysicsBody> createBodyResult(@NotNull Location location, @NotNull String bodyId);

    @Deprecated(forRemoval = false)
    public @Nullable PhysicsBody createBody(@NotNull Location location, @NotNull String bodyId) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(bodyId, "bodyId");
        return createBodyResult(location, bodyId).getValue();
    }

    public abstract boolean isWorldSimulated(@NotNull String worldName);

    @Nullable
    public abstract IPhysicsWorld getPhysicsWorld(@NotNull World world);

    @NotNull
    public abstract Collection<IPhysicsWorld> getAllPhysicsWorlds();

    @NotNull
    public abstract RenderingService rendering();

    /**
     * Config loading + validation helpers (including cross-plugin config sources).
     */
    @NotNull
    public abstract ConfigService configs();

    @NotNull
    public abstract CapabilityService capabilities();

    @NotNull
    public final ApiVersion apiVersion() {
        return capabilities().apiVersion();
    }

    public final boolean isCompatibleWith(@NotNull ApiVersion minimumVersion, @NotNull Collection<ApiCapability> requiredCapabilities) {
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

    public final boolean isCompatibleWith(@NotNull String minimumVersion, @NotNull Collection<ApiCapability> requiredCapabilities) {
        Objects.requireNonNull(minimumVersion, "minimumVersion");
        return isCompatibleWith(ApiVersion.parse(minimumVersion), requiredCapabilities);
    }

    interface InertiaApiResolver {
        @Nullable InertiaApiProvider resolveProvider();
    }

    private static final class BukkitInertiaApiResolver implements InertiaApiResolver {
        @Override
        public @Nullable InertiaApiProvider resolveProvider() {
            return Bukkit.getServicesManager().load(InertiaApiProvider.class);
        }
    }
}
