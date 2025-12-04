package com.ladakx.inertia.physics.body.config;

import com.ladakx.inertia.jolt.object.PhysicsObjectType;

/**
 * Маркерний інтерфейс для всіх типів фізичних визначень.
 * Дозволяє зберігати блоки, ланцюги та регдоли в одній колекції.
 */
public interface BodyDefinition {
    String id();
    PhysicsObjectType type();
}