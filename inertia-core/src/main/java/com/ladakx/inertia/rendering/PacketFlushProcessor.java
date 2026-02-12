package com.ladakx.inertia.rendering;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

final class PacketFlushProcessor {

    record FlushStats(long totalSent, int onlinePlayersWithPackets, int tickPeak, int deferred) {}

    FlushStats flush(Map<UUID, PlayerPacketQueue> packetBuffer,
                     PacketFactory packetFactory,
                     java.util.function.BiPredicate<Integer, Long> tokenValidator,
                     int maxPacketsPerPlayerPerTick,
                     int destroyDrainExtraPacketsPerPlayerPerTick,
                     boolean destroyDrainFastPathActive,
                     boolean massDestroyBudgetBoost,
                     int maxBytesPerPlayerPerTick,
                     long tickCounter,
                     AtomicLong droppedPackets,
                     AtomicLong deferredPackets,
                     AtomicLong destroyLatencyTickTotal,
                     AtomicLong destroyLatencySamples,
                     AtomicLong destroyLatencyPeakTicks) {
        Objects.requireNonNull(packetBuffer, "packetBuffer");
        if (packetFactory == null) {
            return new FlushStats(0L, 0, 0, 0);
        }

        long totalSent = 0;
        int onlinePlayersWithPackets = 0;
        int tickPeak = 0;
        int deferred = 0;

        for (Map.Entry<UUID, PlayerPacketQueue> entry : packetBuffer.entrySet()) {
            PlayerPacketQueue queue = entry.getValue();
            if (queue == null || queue.isEmpty()) continue;

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                if (droppedPackets != null) droppedPackets.addAndGet(queue.size());
                queue.clear();
                continue;
            }

            int maxPackets = maxPacketsPerPlayerPerTick <= 0 ? Integer.MAX_VALUE : maxPacketsPerPlayerPerTick;
            int maxPacketsWithDestroyBurst = maxPackets;
            if (destroyDrainFastPathActive && destroyDrainExtraPacketsPerPlayerPerTick > 0 && maxPackets != Integer.MAX_VALUE) {
                maxPacketsWithDestroyBurst = maxPackets + destroyDrainExtraPacketsPerPlayerPerTick;
            }
            if (massDestroyBudgetBoost && maxPacketsWithDestroyBurst != Integer.MAX_VALUE) {
                maxPacketsWithDestroyBurst += Math.max(16, maxPacketsWithDestroyBurst / 2);
            }

            int maxBytes = maxBytesPerPlayerPerTick <= 0 ? Integer.MAX_VALUE : maxBytesPerPlayerPerTick;
            int boostedDestroyBytes = massDestroyBudgetBoost && maxBytes != Integer.MAX_VALUE
                    ? maxBytes + (maxBytes / 2)
                    : maxBytes;

            List<Object> packetsToSend = new ArrayList<>();
            int sentPackets = 0;
            int sentBytes = 0;

            while (sentPackets < maxPacketsWithDestroyBurst) {
                QueuedPacket next = queue.peek();
                if (next == null) break;

                if (next.visualId() != null && next.tokenVersion() >= 0L && tokenValidator != null
                        && !tokenValidator.test(next.visualId(), next.tokenVersion())) {
                    queue.poll();
                    if (droppedPackets != null) droppedPackets.incrementAndGet();
                    continue;
                }

                boolean isDestroyPacket = next.priority() == PacketPriority.DESTROY;
                boolean allowDestroyPacketBurst = isDestroyPacket && (destroyDrainFastPathActive || massDestroyBudgetBoost);
                if (sentPackets >= maxPackets && !allowDestroyPacketBurst) {
                    break;
                }

                int activeByteBudget = (massDestroyBudgetBoost && isDestroyPacket) ? boostedDestroyBytes : maxBytes;
                boolean fitsByteBudget = sentBytes + next.estimatedBytes() <= activeByteBudget;
                if (!fitsByteBudget && sentPackets > 0) break;

                QueuedPacket polled = queue.poll();
                if (polled == null) break;

                if (polled.visualId() != null && polled.tokenVersion() >= 0L && tokenValidator != null
                        && !tokenValidator.test(polled.visualId(), polled.tokenVersion())) {
                    if (droppedPackets != null) droppedPackets.incrementAndGet();
                    continue;
                }

                packetsToSend.add(polled.packet());
                sentPackets++;
                sentBytes += polled.estimatedBytes();
                if (polled.priority() == PacketPriority.DESTROY && polled.destroyRegisteredAtTick() >= 0) {
                    long latencyTicks = Math.max(0L, tickCounter - polled.destroyRegisteredAtTick());
                    if (destroyLatencyTickTotal != null) destroyLatencyTickTotal.addAndGet(latencyTicks);
                    if (destroyLatencySamples != null) destroyLatencySamples.incrementAndGet();
                    if (destroyLatencyPeakTicks != null) destroyLatencyPeakTicks.accumulateAndGet(latencyTicks, Math::max);
                }
            }

            if (!packetsToSend.isEmpty()) {
                packetFactory.sendBundle(player, packetsToSend);
                totalSent += sentPackets;
                onlinePlayersWithPackets++;
                tickPeak = Math.max(tickPeak, sentPackets);
            }

            int deferredForPlayer = queue.size();
            if (deferredForPlayer > 0) {
                deferred += deferredForPlayer;
            }
        }

        if (deferred > 0 && deferredPackets != null) {
            deferredPackets.addAndGet(deferred);
        }

        return new FlushStats(totalSent, onlinePlayersWithPackets, tickPeak, deferred);
    }
}

