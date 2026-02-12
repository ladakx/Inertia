package com.ladakx.inertia.rendering.tracker.packet;

public record QueuedPacket(Object packet,
                           PacketPriority priority,
                           int estimatedBytes,
                           Integer visualId,
                           boolean coalescible,
                           boolean criticalMetadata,
                           long enqueuedAtNanos,
                           long destroyRegisteredAtTick,
                           long tokenVersion) {}
