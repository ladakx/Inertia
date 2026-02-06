package com.ladakx.inertia.rendering;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Генератор унікальних ID для packet-based entities.
 */
public final class EntityIdProvider {

    private static final int START_ID = 1_000_000_000;
    private static final EntityIdProvider INSTANCE = new EntityIdProvider();
    private final AtomicInteger counter = new AtomicInteger(START_ID);

    private EntityIdProvider() {
    }

    public static EntityIdProvider getInstance() {
        return INSTANCE;
    }

    public int getNextId() {
        return counter.getAndIncrement();
    }
}
