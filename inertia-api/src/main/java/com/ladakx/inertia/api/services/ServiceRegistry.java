package com.ladakx.inertia.api.services;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Inertia-scoped service registry for cross-plugin integrations.
 * <p>
 * This is intentionally separate from Bukkit's {@code ServicesManager} to:
 * <ul>
 *     <li>support multiple services of the same Java type (via {@link ServiceKey})</li>
 *     <li>track ownership for cleanup</li>
 *     <li>avoid global namespace collisions</li>
 * </ul>
 */
public interface ServiceRegistry {

    /**
     * Registers a service implementation owned by {@code owner}.
     *
     * @throws IllegalStateException if the key is already registered
     */
    <T> void register(@NotNull Plugin owner, @NotNull ServiceKey<T> key, @NotNull T implementation);

    /**
     * Replaces any existing service for {@code key}.
     *
     * @return previous implementation or null
     */
    <T> @Nullable T replace(@NotNull Plugin owner, @NotNull ServiceKey<T> key, @NotNull T implementation);

    <T> @Nullable T get(@NotNull ServiceKey<T> key);

    default <T> @NotNull T require(@NotNull ServiceKey<T> key) {
        T value = get(key);
        if (value == null) {
            throw new IllegalStateException("Missing service: " + key.id() + " (" + key.type().getName() + ")");
        }
        return value;
    }

    boolean unregister(@NotNull ServiceKey<?> key);

    void unregisterAll(@NotNull Plugin owner);
}

