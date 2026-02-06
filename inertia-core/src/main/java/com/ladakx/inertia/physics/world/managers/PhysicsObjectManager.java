package com.ladakx.inertia.physics.world.managers;

import com.github.stephengold.joltjni.Body;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PhysicsObjectManager {
    private final List<AbstractPhysicsBody> objects = new CopyOnWriteArrayList<>();
    // Используем Set для быстрого добавления/удаления без дубликатов
    private final Set<AbstractPhysicsBody> activeObjects = ConcurrentHashMap.newKeySet();

    private final Map<Long, AbstractPhysicsBody> objectMap = new ConcurrentHashMap<>();
    // Дополнительная карта для быстрого поиска по ID тела (int), так как Listener дает ID, а не указатель (long)
    private final Map<Integer, AbstractPhysicsBody> bodyIdMap = new ConcurrentHashMap<>();
    private final Map<Integer, AbstractPhysicsBody> networkEntityIdMap = new ConcurrentHashMap<>();

    private final Map<UUID, AbstractPhysicsBody> uuidMap = new ConcurrentHashMap<>();

    public void add(@NotNull AbstractPhysicsBody object) {
        objects.add(object);
        uuidMap.put(object.getUuid(), object);
        registerBody(object, object.getBody());

        // Если тело создано активным, добавляем сразу
        if (object.getBody().isActive()) {
            activeObjects.add(object);
        }
    }

    public void remove(@NotNull AbstractPhysicsBody object) {
        objects.remove(object);
        activeObjects.remove(object);
        uuidMap.remove(object.getUuid());
        objectMap.values().removeIf(o -> o == object);
        bodyIdMap.values().removeIf(o -> o == object);
        networkEntityIdMap.values().removeIf(o -> o == object);
    }

    public void registerBody(@NotNull AbstractPhysicsBody object, @Nullable Body body) {
        if (body != null) {
            objectMap.put(body.va(), object);
            bodyIdMap.put(body.getId(), object);
        }
    }

    public void onBodyActivated(int bodyId) {
        AbstractPhysicsBody obj = bodyIdMap.get(bodyId);
        if (obj != null) {
            activeObjects.add(obj);
        }
    }

    public void onBodyDeactivated(int bodyId) {
        AbstractPhysicsBody obj = bodyIdMap.get(bodyId);
        if (obj != null) {
            activeObjects.remove(obj);
        }
    }

    public @Nullable AbstractPhysicsBody getByVa(long va) {
        return objectMap.get(va);
    }

    public @Nullable AbstractPhysicsBody getByUuid(UUID uuid) {
        return uuidMap.get(uuid);
    }

    public void registerNetworkEntityId(@NotNull AbstractPhysicsBody object, int entityId) {
        networkEntityIdMap.put(entityId, object);
    }

    public void unregisterNetworkEntityId(int entityId) {
        networkEntityIdMap.remove(entityId);
    }

    public @Nullable AbstractPhysicsBody getByNetworkEntityId(int entityId) {
        return networkEntityIdMap.get(entityId);
    }

    public @NotNull List<AbstractPhysicsBody> getAll() {
        return objects;
    }

    public @NotNull Collection<AbstractPhysicsBody> getActive() {
        return activeObjects;
    }

    public void clearAll() {
        List<AbstractPhysicsBody> snapshot = new ArrayList<>(objects);
        int count = 0;
        for (AbstractPhysicsBody obj : snapshot) {
            try {
                obj.destroy();
                count++;
            } catch (Exception e) {
                InertiaLogger.error("Error destroying object during clearAll", e);
            }
        }
        objects.clear();
        activeObjects.clear();
        objectMap.clear();
        bodyIdMap.clear();
        networkEntityIdMap.clear();
        uuidMap.clear();
        InertiaLogger.info("ObjectManager cleared " + count + " objects.");
    }
}
