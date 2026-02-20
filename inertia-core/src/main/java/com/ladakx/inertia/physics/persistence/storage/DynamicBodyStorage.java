package com.ladakx.inertia.physics.persistence.storage;

import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class DynamicBodyStorage {

    private final PhysicsWorldRegistry physicsWorldRegistry;

    public DynamicBodyStorage(PhysicsWorldRegistry physicsWorldRegistry) {
        this.physicsWorldRegistry = Objects.requireNonNull(physicsWorldRegistry, "physicsWorldRegistry");
    }

    public List<DynamicBodyStorageRecord> snapshot() {
        Collection<PhysicsWorld> spaces = physicsWorldRegistry.getAllSpaces();
        List<DynamicBodyStorageRecord> records = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (PhysicsWorld space : spaces) {
            String worldName = space.getBukkitWorld().getName();
            for (InertiaPhysicsBody body : space.getBodies()) {
                if (body.getMotionType() == MotionType.STATIC || !body.isValid()) {
                    continue;
                }
                if (!(body instanceof AbstractPhysicsBody abstractPhysicsBody)) {
                    continue;
                }
                Location location = body.getLocation();
                int chunkX = location.getBlockX() >> 4;
                int chunkZ = location.getBlockZ() >> 4;
                records.add(new DynamicBodyStorageRecord(
                        abstractPhysicsBody.getUuid(),
                        worldName,
                        body.getBodyId(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        chunkX,
                        chunkZ,
                        now
                ));
            }
        }
        return records;
    }
}
