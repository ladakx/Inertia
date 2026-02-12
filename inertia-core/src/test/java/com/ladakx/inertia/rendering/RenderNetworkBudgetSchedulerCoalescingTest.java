package com.ladakx.inertia.rendering;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderNetworkBudgetSchedulerCoalescingTest {

    @Test
    void shouldCoalesceOnlyWithinSamePlayerAndVisualPair() {
        RenderNetworkBudgetScheduler scheduler = new RenderNetworkBudgetScheduler();
        scheduler.applyThreadingSettings(20_000_000L);

        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        AtomicInteger firstA = new AtomicInteger();
        AtomicInteger latestA = new AtomicInteger();
        AtomicInteger forB = new AtomicInteger();

        scheduler.enqueueMetadataCoalesced(playerA, 42, firstA::incrementAndGet);
        scheduler.enqueueMetadataCoalesced(playerA, 42, latestA::incrementAndGet);
        scheduler.enqueueMetadataCoalesced(playerB, 42, forB::incrementAndGet);

        scheduler.runTick(Collections.emptyList());

        assertEquals(0, firstA.get());
        assertEquals(1, latestA.get());
        assertEquals(1, forB.get());
        assertEquals(1L, scheduler.getCoalescedTaskCount());
    }
}
