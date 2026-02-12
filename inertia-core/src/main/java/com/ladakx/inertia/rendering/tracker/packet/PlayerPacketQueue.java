package com.ladakx.inertia.rendering.tracker.packet;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class PlayerPacketQueue {
    private final Object mutex = new Object();
    private final EnumMap<PacketPriority, ArrayDeque<QueuedPacket>> byPriority = new EnumMap<>(PacketPriority.class);
    private final Map<Integer, QueuedPacket> lastTeleportByVisualId = new HashMap<>();

    public PlayerPacketQueue() {
        byPriority.put(PacketPriority.DESTROY, new ArrayDeque<>());
        byPriority.put(PacketPriority.SPAWN, new ArrayDeque<>());
        byPriority.put(PacketPriority.TELEPORT, new ArrayDeque<>());
        byPriority.put(PacketPriority.METADATA, new ArrayDeque<>());
    }

    public int add(QueuedPacket packet, java.util.function.BiPredicate<Integer, Long> tokenValidator) {
        int coalesced = 0;
        if (packet == null) {
            return 0;
        }
        if (packet.visualId() != null && packet.tokenVersion() >= 0L && tokenValidator != null
                && !tokenValidator.test(packet.visualId(), packet.tokenVersion())) {
            return 0;
        }
        synchronized (mutex) {
            if (packet.priority() == PacketPriority.TELEPORT && packet.coalescible() && packet.visualId() != null) {
                QueuedPacket previous = lastTeleportByVisualId.put(packet.visualId(), packet);
                if (previous != null && byPriority.get(PacketPriority.TELEPORT).remove(previous)) {
                    coalesced = 1;
                }
            }
            byPriority.get(packet.priority()).addLast(packet);
        }
        return coalesced;
    }

    public QueuedPacket peek() {
        synchronized (mutex) {
            QueuedPacket packet = byPriority.get(PacketPriority.DESTROY).peekFirst();
            if (packet != null) return packet;
            packet = byPriority.get(PacketPriority.SPAWN).peekFirst();
            if (packet != null) return packet;
            packet = byPriority.get(PacketPriority.TELEPORT).peekFirst();
            if (packet != null) return packet;
            return byPriority.get(PacketPriority.METADATA).peekFirst();
        }
    }

    public QueuedPacket poll() {
        synchronized (mutex) {
            QueuedPacket packet = byPriority.get(PacketPriority.DESTROY).pollFirst();
            if (packet != null) return packet;
            packet = byPriority.get(PacketPriority.SPAWN).pollFirst();
            if (packet != null) return packet;
            packet = byPriority.get(PacketPriority.TELEPORT).pollFirst();
            if (packet != null) {
                if (packet.visualId() != null) {
                    lastTeleportByVisualId.remove(packet.visualId(), packet);
                }
                return packet;
            }
            return byPriority.get(PacketPriority.METADATA).pollFirst();
        }
    }

    public int size() {
        synchronized (mutex) {
            int total = 0;
            for (ArrayDeque<QueuedPacket> queue : byPriority.values()) {
                total += queue.size();
            }
            return total;
        }
    }

    public boolean isEmpty() {
        synchronized (mutex) {
            for (ArrayDeque<QueuedPacket> queue : byPriority.values()) {
                if (!queue.isEmpty()) return false;
            }
            return true;
        }
    }

    public void clear() {
        synchronized (mutex) {
            byPriority.values().forEach(ArrayDeque::clear);
            lastTeleportByVisualId.clear();
        }
    }

    public void invalidateVisual(int visualId, long activeTokenVersion) {
        synchronized (mutex) {
            pruneQueue(PacketPriority.SPAWN, visualId, activeTokenVersion);
            pruneQueue(PacketPriority.TELEPORT, visualId, activeTokenVersion);
            pruneQueue(PacketPriority.METADATA, visualId, activeTokenVersion);
        }
    }

    public void pruneBeforeBulkDestroy(int[] visualIds) {
        if (visualIds == null || visualIds.length == 0) {
            return;
        }
        IntOpenHashSet set = new IntOpenHashSet(visualIds.length);
        for (int visualId : visualIds) {
            set.add(visualId);
        }
        synchronized (mutex) {
            pruneQueue(PacketPriority.SPAWN, set);
            pruneQueue(PacketPriority.TELEPORT, set);
            pruneQueue(PacketPriority.METADATA, set);
        }
    }

    private void pruneQueue(PacketPriority priority, int visualId, long activeTokenVersion) {
        ArrayDeque<QueuedPacket> queue = byPriority.get(priority);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        Iterator<QueuedPacket> iterator = queue.iterator();
        while (iterator.hasNext()) {
            QueuedPacket packet = iterator.next();
            if (packet.visualId() == null || packet.visualId() != visualId) {
                continue;
            }
            if (packet.tokenVersion() >= 0L && packet.tokenVersion() != activeTokenVersion) {
                iterator.remove();
                if (priority == PacketPriority.TELEPORT) {
                    lastTeleportByVisualId.remove(visualId, packet);
                }
            }
        }
    }

    private void pruneQueue(PacketPriority priority, IntOpenHashSet visualIds) {
        ArrayDeque<QueuedPacket> queue = byPriority.get(priority);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        Iterator<QueuedPacket> iterator = queue.iterator();
        while (iterator.hasNext()) {
            QueuedPacket packet = iterator.next();
            Integer visualId = packet.visualId();
            if (visualId == null || !visualIds.contains(visualId.intValue())) {
                continue;
            }
            iterator.remove();
            if (priority == PacketPriority.TELEPORT) {
                lastTeleportByVisualId.remove(visualId, packet);
            }
        }
    }
}
