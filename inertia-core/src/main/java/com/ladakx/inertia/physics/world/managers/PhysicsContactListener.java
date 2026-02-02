package com.ladakx.inertia.physics.world.managers;

import com.github.stephengold.joltjni.*;
import com.ladakx.inertia.api.events.PhysicsBodySpawnEvent;
import com.ladakx.inertia.api.events.PhysicsCollisionEvent;
import com.ladakx.inertia.common.utils.ConvertUtils;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import org.bukkit.Bukkit;
import org.bukkit.util.Vector;

public class PhysicsContactListener extends CustomContactListener {
    private final PhysicsObjectManager objectManager;

    public PhysicsContactListener(PhysicsObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @Override
    public void onContactAdded(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
        AbstractPhysicsBody obj1 = objectManager.getByVa(body1Va);
        AbstractPhysicsBody obj2 = objectManager.getByVa(body2Va);

        if (obj1 != null && obj2 != null) {
            ContactManifold manifold = new ContactManifold(manifoldVa);

            // Расчет точки контакта: BaseOffset (Double)
            // Если точные точки недоступны, используем центр масс первого тела как приближение
            // так как getWorldSpaceContactPointOn1 отсутствует в этой версии биндинга

            RVec3 contactPointJolt = manifold.getBaseOffset();
            // В идеале нужно добавить manifold.getRelativeContactPointsOn1().get(0) если доступно,
            // но для безопасности используем baseOffset, что является центром зоны контакта.

            Vector contactPoint = ConvertUtils.toBukkit(contactPointJolt);

            PhysicsCollisionEvent event = new PhysicsCollisionEvent(obj1, obj2, contactPoint);
            //Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
                Bukkit.getPluginManager().callEvent(event);
            //});
        }
    }
}