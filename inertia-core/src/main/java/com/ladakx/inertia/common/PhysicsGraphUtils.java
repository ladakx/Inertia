package com.ladakx.inertia.common;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Утилиты для анализа графа физических соединений.
 */
public final class PhysicsGraphUtils {

    private PhysicsGraphUtils() {
        // Utility class
    }

    /**
     * Собирает кластер всех физических тел, соединенных с переданным корневым телом
     * через систему ограничений (Constraints). Использует BFS.
     *
     * @param space Физический мир.
     * @param root  Начальное тело для поиска.
     * @return Набор (Set) всех уникальных тел в кластере (включая root).
     */
    public static Set<AbstractPhysicsBody> collectConnectedBodies(@NotNull PhysicsWorld space, @NotNull AbstractPhysicsBody root) {
        Set<AbstractPhysicsBody> visited = new HashSet<>();
        Deque<AbstractPhysicsBody> queue = new ArrayDeque<>();

        visited.add(root);
        queue.add(root);

        while (!queue.isEmpty()) {
            AbstractPhysicsBody current = queue.poll();
            if (current == null || !current.isValid()) continue;

            List<TwoBodyConstraintRef> refs = current.getConstraintSnapshot();
            for (TwoBodyConstraintRef ref : refs) {
                if (ref == null) continue;

                try {
                    TwoBodyConstraint constraint = ref.getPtr();
                    if (constraint == null) continue;

                    enqueueOwner(space, constraint.getBody1(), visited, queue);
                    enqueueOwner(space, constraint.getBody2(), visited, queue);
                } catch (Exception e) {
                    InertiaLogger.warn("Error traversing constraints during graph analysis: " + e.getMessage());
                }
            }
        }
        return visited;
    }

    private static void enqueueOwner(PhysicsWorld space,
                                     Body body,
                                     Set<AbstractPhysicsBody> visited,
                                     Deque<AbstractPhysicsBody> queue) {
        if (body == null) return;

        AbstractPhysicsBody owner = space.getObjectByVa(body.va());
        if (owner != null && !visited.contains(owner)) {
            visited.add(owner);
            queue.add(owner);
        }
    }
}