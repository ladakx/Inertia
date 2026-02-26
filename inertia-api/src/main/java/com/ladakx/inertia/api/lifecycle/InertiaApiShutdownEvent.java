package com.ladakx.inertia.api.lifecycle;

import com.ladakx.inertia.api.InertiaApi;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired on the main thread right before Inertia unregisters its API services.
 * <p>
 * Plugins can use this to release resources bound to Inertia.
 */
public final class InertiaApiShutdownEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final InertiaApi api;

    public InertiaApiShutdownEvent(@NotNull InertiaApi api) {
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
