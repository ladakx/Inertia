package com.ladakx.inertia.api.service;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterfaceLocking;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.configuration.ConfigurationService;
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

    private final ConfigurationService configurationService;
    private final PhysicsWorldRegistry worldRegistry;
    private final Map<UUID, Integer> debugPlayers = new ConcurrentHashMap<>();
    private BukkitTask renderTask;

    public DebugRenderService(PhysicsWorldRegistry worldRegistry, ConfigurationService configurationService) {
        this.worldRegistry = worldRegistry;
        this.configurationService = configurationService;
    }

    public void start() {
        int interval = Math.max(1, configurationService.getInertiaConfig().GENERAL.DEBUG.hitboxRenderIntervalTicks);
        this.renderTask = Bukkit.getScheduler().runTaskTimer(InertiaPlugin.getInstance(), this::renderTick, 1L, interval);
    }

    public void stop() {
        if (renderTask != null && !renderTask.isCancelled()) {
            renderTask.cancel();
        }
        debugPlayers.clear();
    }

    public int toggleDebug(Player player, Integer range) {
        if (debugPlayers.containsKey(player.getUniqueId())) {
            debugPlayers.remove(player.getUniqueId());
            return -1;
        } else {
            int resolvedRange = resolveRange(range);
            debugPlayers.put(player.getUniqueId(), resolvedRange);
            return resolvedRange;
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
            PhysicsWorld space = worldRegistry.getWorld(p.getWorld());
            if (space != null) {
                worldPlayers.computeIfAbsent(space, k -> new ArrayList<>()).add(p);
            }
        }
        for (Map.Entry<PhysicsWorld, List<Player>> entry : worldPlayers.entrySet()) {
            PhysicsWorld space = entry.getKey();
            List<Player> players = entry.getValue();
            ConstBodyLockInterfaceLocking bli = space.getPhysicsSystem().getBodyLockInterface();
            RVec3 origin = space.getOrigin();

            Set<Integer> bodiesToRender = new HashSet<>();
            for (Player p : players) {
                int range = debugPlayers.get(p.getUniqueId());
                collectBodiesInRange(p, space, range, bodiesToRender, bli, origin);
            }

            for (int bodyId : bodiesToRender) {
                try (com.github.stephengold.joltjni.BodyLockRead lock = new com.github.stephengold.joltjni.BodyLockRead(bli, bodyId)) {
                    if (lock.succeeded()) {
                        ConstBody body = lock.getBody();
                        ShapeDrawer.drawBody(space.getBukkitWorld(), body, origin);
                    }
                }
            }
        }
    }

    private int resolveRange(Integer range) {
        int defaultRange = configurationService.getInertiaConfig().GENERAL.DEBUG.hitboxDefaultRange;
        int maxRange = configurationService.getInertiaConfig().GENERAL.DEBUG.hitboxMaxRange;
        int resolvedRange = range != null ? range : defaultRange;
        if (resolvedRange < 1) {
            resolvedRange = 1;
        }
        if (maxRange > 0) {
            resolvedRange = Math.min(resolvedRange, maxRange);
        }
        return resolvedRange;
    }

    private void collectBodiesInRange(Player player,
                                      PhysicsWorld space,
                                      int range,
                                      Set<Integer> bodiesToRender,
                                      ConstBodyLockInterfaceLocking bli,
                                      RVec3 origin) {
        Location playerLoc = player.getLocation();
        double rangeSq = range * range;

        for (AbstractPhysicsBody obj : space.getObjects()) {
            if (!obj.isValid()) {
                continue;
            }
            if (obj.getLocation().distanceSquared(playerLoc) <= rangeSq) {
                bodiesToRender.add(obj.getBody().getId());
            }
        }

        for (int bodyId : space.getSystemStaticBodyIds()) {
            if (bodiesToRender.contains(bodyId)) {
                continue;
            }
            try (com.github.stephengold.joltjni.BodyLockRead lock = new com.github.stephengold.joltjni.BodyLockRead(bli, bodyId)) {
                if (lock.succeeded()) {
                    ConstBody body = lock.getBody();
                    if (isBodyInRange(body, space, playerLoc, rangeSq, origin)) {
                        bodiesToRender.add(bodyId);
                    }
                }
            }
        }
    }

    private boolean isBodyInRange(ConstBody body,
                                  PhysicsWorld space,
                                  Location playerLoc,
                                  double rangeSq,
                                  RVec3 origin) {
        Location bodyLoc = space.toBukkit(body.getPosition());
        if (bodyLoc.distanceSquared(playerLoc) <= rangeSq) {
            return true;
        }

        ConstShape shape = body.getShape();
        AaBox bounds = shape.getWorldSpaceBounds(RMat44.sRotationTranslation(body.getRotation(), body.getPosition()), Vec3.sReplicate(1.0f));
        Vec3 min = bounds.getMin();
        Vec3 max = bounds.getMax();

        double minX = min.getX() + origin.xx();
        double minY = min.getY() + origin.yy();
        double minZ = min.getZ() + origin.zz();
        double maxX = max.getX() + origin.xx();
        double maxY = max.getY() + origin.yy();
        double maxZ = max.getZ() + origin.zz();

        double dx = 0.0;
        if (playerLoc.getX() < minX) {
            dx = minX - playerLoc.getX();
        } else if (playerLoc.getX() > maxX) {
            dx = playerLoc.getX() - maxX;
        }

        double dy = 0.0;
        if (playerLoc.getY() < minY) {
            dy = minY - playerLoc.getY();
        } else if (playerLoc.getY() > maxY) {
            dy = playerLoc.getY() - maxY;
        }

        double dz = 0.0;
        if (playerLoc.getZ() < minZ) {
            dz = minZ - playerLoc.getZ();
        } else if (playerLoc.getZ() > maxZ) {
            dz = playerLoc.getZ() - maxZ;
        }

        return (dx * dx + dy * dy + dz * dz) <= rangeSq;
    }
}
