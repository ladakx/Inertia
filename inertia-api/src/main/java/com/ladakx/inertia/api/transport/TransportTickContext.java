package com.ladakx.inertia.api.transport;

/**
 * Tick information passed to {@link TransportController}.
 */
public record TransportTickContext(long tickNumber, float deltaSeconds) {
}

