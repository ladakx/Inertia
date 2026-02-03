package com.ladakx.inertia.api.service;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterfaceLocking;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.debug.ShapeDrawer;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DebugRenderService {

    private final PhysicsWorldRegistry worldRegistry;
    private final Map<UUID, Integer> debugPlayers = new ConcurrentHashMap<>();
    private final StaticDebugManager staticDebugManager = new StaticDebugManager();
    private BukkitTask renderTask;
    private static final int DEFAULT_RANGE = 20;

    public DebugRenderService(PhysicsWorldRegistry worldRegistry) {
        this.worldRegistry = worldRegistry;
    }

    public void start() {
        this.renderTask = Bukkit.getScheduler().runTaskTimer(InertiaPlugin.getInstance(), this::renderTick, 1L, 2L);
    }

    public void stop() {
        if (renderTask != null && !renderTask.isCancelled()) {
            renderTask.cancel();
        }
        debugPlayers.clear();
        staticDebugManager.cleanup();
    }

    public void toggleDebug(Player player, Integer range) {
        if (debugPlayers.containsKey(player.getUniqueId())) {
            debugPlayers.remove(player.getUniqueId());
        } else {
            debugPlayers.put(player.getUniqueId(), range != null ? range : DEFAULT_RANGE);
        }
    }

    public boolean isDebugEnabled(Player player) {
        return debugPlayers.containsKey(player.getUniqueId());
    }

    private void renderTick() {
        if (debugPlayers.isEmpty()) return;
        Map<PhysicsWorld, List<Player>> worldPlayers = new HashMap<>();
        for (UUID uuid : debugPlayers.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                debugPlayers.remove(uuid);
                continue;
            }
            PhysicsWorld space = worldRegistry.getSpace(p.getWorld());
            if (space != null) {
                worldPlayers.computeIfAbsent(space, k -> new ArrayList<>()).add(p);
            }
        }
        for (Map.Entry<PhysicsWorld, List<Player>> entry : worldPlayers.entrySet()) {
            PhysicsWorld space = entry.getKey();
            List<Player> players = entry.getValue();
            ConstBodyLockInterfaceLocking bli = space.getPhysicsSystem().getBodyLockInterface();
            RVec3 origin = space.getOrigin();

            // 1. Обновление статичных тел (BlockDisplays) - Использует пользовательский range
            for (Player p : players) {
                int range = debugPlayers.get(p.getUniqueId());
                staticDebugManager.update(p, space, range);
            }

            // 2. Отрисовка динамических тел (Particles) - Жесткий лимит 8 блоков
            Set<Integer> dynamicBodiesToRender = new HashSet<>();
            double particleRangeSq = 8.0 * 8.0; // Hardcoded radius for particles

            for (Player p : players) {
                Location pLoc = p.getLocation();
                for (AbstractPhysicsBody obj : space.getObjects()) {
                    if (obj.isValid() && obj.getMotionType() != MotionType.STATIC) {
                        if (obj.getLocation().distanceSquared(pLoc) <= particleRangeSq) {
                            dynamicBodiesToRender.add(obj.getBody().getId());
                        }
                    }
                }
            }

            for (int bodyId : dynamicBodiesToRender) {
                try (com.github.stephengold.joltjni.BodyLockRead lock = new com.github.stephengold.joltjni.BodyLockRead(bli, bodyId)) {
                    if (lock.succeeded()) {
                        ConstBody body = lock.getBody();
                        ShapeDrawer.drawBody(space.getBukkitWorld(), body, origin);
                    }
                }
            }
        }
    }
}