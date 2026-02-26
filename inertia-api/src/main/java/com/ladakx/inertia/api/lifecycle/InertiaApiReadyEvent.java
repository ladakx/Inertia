package com.ladakx.inertia.api.lifecycle;

import com.ladakx.inertia.api.InertiaApi;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired on the main thread when Inertia has registered its API services.
 * <p>
 * Intended for plugins that want a single "API is available" hook instead of polling.
 */
public final class InertiaApiReadyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final InertiaApi api;

    public InertiaApiReadyEvent(@NotNull InertiaApi api) {
        super(false);
        this.api = Objects.requireNonNull(api, "api");
    }

    public @NotNull InertiaApi api() {
        return api;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
