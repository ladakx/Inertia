package com.ladakx.inertia.api.body.type;

import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Представляет собой часть тела (конечность) рэгдолла.
 */
public interface IRagdoll extends InertiaPhysicsBody {

    /**
     * Возвращает название части тела, определенное в конфигурации (например, "head", "left_arm").
     */
    @NotNull
    String getPartName();

    /**
     * Возвращает родительскую часть тела, к которой прикреплена данная часть.
     * @return Родительское тело или null, если это корневая часть.
     */
    @Nullable
    InertiaPhysicsBody getParentPart();

    /**
     * Разрывает сустав (joint), соединяющий эту часть с родительской.
     */
    void detachFromParent();
}