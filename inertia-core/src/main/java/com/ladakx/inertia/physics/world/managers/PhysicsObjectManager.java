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
    private final Map<Long, AbstractPhysicsBody> objectMap = new ConcurrentHashMap<>();
    private final Map<UUID, AbstractPhysicsBody> uuidMap = new ConcurrentHashMap<>();

    public void add(@NotNull AbstractPhysicsBody object) {
        objects.add(object);
        uuidMap.put(object.getUuid(), object);
        registerBody(object, object.getBody());
    }

    public void remove(@NotNull AbstractPhysicsBody object) {
        objects.remove(object);
        uuidMap.remove(object.getUuid());
        // Очистка objectMap от ссылок на это тело происходит сложнее, 
        // так как одно тело может иметь несколько Jolt-тел (ragdoll).
        // Для простоты и производительности удаляем по итератору при разрушении 
        // или доверяем тому, что VA адрес перестанет быть валидным.
        objectMap.values().removeIf(o -> o == object);
    }

    public void registerBody(@NotNull AbstractPhysicsBody object, @Nullable Body body) {
        if (body != null) {
            objectMap.put(body.va(), object);
        }
    }

    public @Nullable AbstractPhysicsBody getByVa(long va) {
        return objectMap.get(va);
    }

    public @Nullable AbstractPhysicsBody getByUuid(UUID uuid) {
        return uuidMap.get(uuid);
    }

    public @NotNull List<AbstractPhysicsBody> getAll() {
        return objects;
    }

    public void clearAll() {
        // Создаем копию для безопасной итерации при удалении
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
        objectMap.clear();
        uuidMap.clear();
        InertiaLogger.info("ObjectManager cleared " + count + " objects.");
    }
}