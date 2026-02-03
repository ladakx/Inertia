package com.ladakx.inertia.api.service;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterfaceLocking;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.pdc.InertiaPDCKeys;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.debug.ShapeDrawer;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.VisualEntity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StaticDebugManager {

    // Заменили BlockDisplay на VisualEntity
    private final Map<Integer, List<VisualEntity>> staticVisuals = new ConcurrentHashMap<>();
    private final Map<Integer, Set<UUID>> viewers = new ConcurrentHashMap<>();

    public void update(Player player, PhysicsWorld space, int range) {
        ConstBodyLockInterfaceLocking bli = space.getPhysicsSystem().getBodyLockInterface();
        RVec3 origin = space.getOrigin();
        World world = space.getBukkitWorld();

        double rangeSq = range * range;

        Set<Integer> nearbyStatic = new HashSet<>();
        for (AbstractPhysicsBody obj : space.getObjects()) {
            if (obj.isValid() && obj.getMotionType() == com.ladakx.inertia.api.body.MotionType.STATIC) {
                if (obj.getLocation().distanceSquared(player.getLocation()) <= rangeSq) {
                    nearbyStatic.add(obj.getBody().getId());
                }
            }
        }

        for (int bodyId : space.getSystemStaticBodyIds()) {
            try (com.github.stephengold.joltjni.BodyLockRead lock = new com.github.stephengold.joltjni.BodyLockRead(bli, bodyId)) {
                if (lock.succeeded()) {
                    ConstBody body = lock.getBody();
                    if (isBodyInRange(body, space, player, rangeSq, origin)) {
                        nearbyStatic.add(bodyId);
                    }
                }
            }
        }

        for (int bodyId : nearbyStatic) {
            if (!staticVisuals.containsKey(bodyId)) {
                createVisuals(space, bodyId, world, origin, bli);
            }

            viewers.computeIfAbsent(bodyId, k -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());

            List<VisualEntity> displays = staticVisuals.get(bodyId);
            if (displays != null) {
                for (VisualEntity ve : displays) {
                    if (ve.isValid()) {
                        ve.setVisible(true); // Для VisualEntity мы просто делаем setVisible
                        // Важно: в 1.21 реализации BlockDisplay по умолчанию скрыты.
                        // Нам нужно показать их конкретному игроку.
                        // VisualEntity не имеет метода showTo(Player).
                        // В этом случае мы полагаемся на то, что setVisible(true) делает entity видимой.
                        // Если нужна по-игровая видимость, VisualEntity нужно расширить,
                        // но для MVP дебага достаточно глобальной видимости или использования Packet-based подхода.
                        // Учитывая текущую архитектуру VisualEntity, мы покажем их всем.
                        // (Улучшение на будущее: добавить метод show(Player) в VisualEntity)

                        // Хак для BlockDisplay: так как мы используем setVisibleByDefault(false) в фабрике,
                        // нам нужно использовать NMS/Bukkit API для показа игроку.
                        // Но VisualEntity абстрагирует это.
                        // Давайте просто включим их глобально в update, так как это дебаг.
                    }
                }
            }
        }

        Iterator<Map.Entry<Integer, Set<UUID>>> it = viewers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Set<UUID>> entry = it.next();
            int bodyId = entry.getKey();
            Set<UUID> bodyViewers = entry.getValue();

            if (bodyViewers.contains(player.getUniqueId())) {
                boolean stillInRate = nearbyStatic.contains(bodyId);

                if (!stillInRate) {
                    bodyViewers.remove(player.getUniqueId());
                    List<VisualEntity> displays = staticVisuals.get(bodyId);
                    if (displays != null && bodyViewers.isEmpty()) {
                        for (VisualEntity ve : displays) ve.setVisible(false);
                    }
                }
            }

            if (bodyViewers.isEmpty()) {
                List<VisualEntity> displays = staticVisuals.remove(bodyId);
                if (displays != null) {
                    for (VisualEntity ve : displays) ve.remove();
                }
                it.remove();
            }
        }
    }

    private void createVisuals(PhysicsWorld space, int bodyId, World world, RVec3 origin, ConstBodyLockInterfaceLocking bli) {
        try (com.github.stephengold.joltjni.BodyLockRead lock = new com.github.stephengold.joltjni.BodyLockRead(bli, bodyId)) {
            if (lock.succeeded()) {
                ConstBody body = lock.getBody();
                List<ShapeDrawer.Line> lines = ShapeDrawer.getBodyLines(body, origin);
                List<VisualEntity> entities = new ArrayList<>();

                for (ShapeDrawer.Line line : lines) {
                    Vector start = new Vector(line.start().x, line.start().y, line.start().z);
                    Vector end = new Vector(line.end().x, line.end().y, line.end().z);

                    // Используем RenderFactory из плагина
                    VisualEntity ve = InertiaPlugin.getInstance().getRenderFactory()
                            .createDebugLine(world, start, end, 0.05f, Color.RED);

                    if (ve.isValid()) {
                        // Помечаем тегом (если поддерживается)
                        var pdc = ve.getPersistentDataContainer();
                        if (pdc != null) {
                            pdc.set(InertiaPDCKeys.INERTIA_ENTITY_STATIC, PersistentDataType.STRING, "debug_line");
                        }
                        ve.setVisible(true);
                        entities.add(ve);
                    }
                }
                staticVisuals.put(bodyId, entities);
            }
        } catch (Exception e) {
            InertiaLogger.error("Failed to create static debug visuals for body " + bodyId, e);
        }
    }

    private boolean isBodyInRange(ConstBody body, PhysicsWorld space, Player player, double rangeSq, RVec3 origin) {
        Location playerLoc = player.getLocation();
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

    public void cleanup() {
        for (List<VisualEntity> list : staticVisuals.values()) {
            for (VisualEntity ve : list) ve.remove();
        }
        staticVisuals.clear();
        viewers.clear();
    }
}
