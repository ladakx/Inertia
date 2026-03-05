package com.ladakx.inertia.api.transport.events;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.transport.TransportSpec;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
public final class TransportPreSpawnEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final TransportLifecyclePayload payload;
    private final TransportSpec spec;
    private final Map<String, String> customData;
    private boolean cancelled;

    public TransportPreSpawnEvent(@NotNull TransportLifecyclePayload payload,
                                  @NotNull TransportSpec spec,
                                  @NotNull Map<String, String> customData) {
        super(false);
        this.payload = Objects.requireNonNull(payload, "payload");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.customData = Map.copyOf(Objects.requireNonNull(customData, "customData"));
    }

    public @NotNull TransportLifecyclePayload getPayload() {
        return payload;
    }

    public @NotNull TransportSpec getSpec() {
        return spec;
    }

    public @NotNull Map<String, String> getCustomData() {
        return customData;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
