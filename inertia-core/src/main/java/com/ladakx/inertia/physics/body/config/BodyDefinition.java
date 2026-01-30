package com.ladakx.inertia.physics.body.config;

import com.ladakx.inertia.physics.body.PhysicsBodyType;

/**
 * Маркерний інтерфейс для всіх типів фізичних визначень.
 * Дозволяє зберігати блоки, ланцюги та регдоли в одній колекції.
 */
public interface BodyDefinition {
    String id();
    PhysicsBodyType type();
}