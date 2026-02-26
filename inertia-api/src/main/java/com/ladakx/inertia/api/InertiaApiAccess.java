package com.ladakx.inertia.api;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Resolver utility for {@link InertiaApi}.
 * <p>
 * Preferred entrypoint for external plugins.
 */
public final class InertiaApiAccess {
    private static volatile Resolver resolver = new BukkitResolver();

    private InertiaApiAccess() {
    }

    public static @NotNull InertiaApi resolve() {
        InertiaApiProvider provider = resolver.resolveProvider();
        if (provider == null) {
            throw new InertiaApiUnavailableException("Inertia API service is unavailable.");
        }
        return Objects.requireNonNull(provider.getApi(), "provider.getApi()");
    }

    interface Resolver {
        @Nullable InertiaApiProvider resolveProvider();
    }

    private static final class BukkitResolver implements Resolver {
        @Override
        public @Nullable InertiaApiProvider resolveProvider() {
            return Bukkit.getServicesManager().load(InertiaApiProvider.class);
        }
    }
}

