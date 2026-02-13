package com.ladakx.inertia.api.interaction;

import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Результат попадания луча (Raycast).
 *
 * @param body     Физическое тело, в которое попал луч.
 * @param point    Точка соприкосновения в мировых координатах.
 * @param fraction Доля пройденного пути луча (от 0.0 до 1.0).
 */
public record RaycastHit(@NotNull InertiaPhysicsBody body, @NotNull Vector point, double fraction) {
}