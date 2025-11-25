package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.render.runtime.PhysicsDisplayComposite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Абстрактний клас для фізичних об'єктів Minecraft, пов'язаних з Jolt Body. [3]
 */
public abstract class AbstractPhysicsObject {


    private final List<Integer> relatedBodies = new CopyOnWriteArrayList<>();
    private final List<com.github.stephengold.joltjni.TwoBodyConstraintRef> constraints =
            new CopyOnWriteArrayList<>();

    private final @NotNull MinecraftSpace space;
    private final @NotNull BodyCreationSettings bodySettings;
    private final @NotNull Body body;

    public AbstractPhysicsObject(@NotNull MinecraftSpace space,
                                 @NotNull BodyCreationSettings bodySettings) {
        this.space = space;
        this.bodySettings = bodySettings;

        this.body = space.getBodyInterface().createBody(this.bodySettings);
        space.getBodyInterface().addBody(body, EActivation.Activate);
        space.addObject(this);
    }

    public void addRelated(@NotNull Body related) {
        this.relatedBodies.add(related.getId());
    }

    public void addRelatedConstraint(@NotNull com.github.stephengold.joltjni.TwoBodyConstraintRef related) {
        this.constraints.add(related);
    }

    public void removeRelatedConstraint(@NotNull com.github.stephengold.joltjni.TwoBodyConstraintRef related) {
        this.constraints.remove(related);
    }

    /**
     * Доступ до Jolt-тіла для системи відмальовування.
     */
    public @NotNull Body getBody() {
        return body;
    }

    public @NotNull MinecraftSpace getSpace() {
        return space;
    }

    /**
     * Повертає збережену Bukkit-сутність, пов'язану з цим фізичним тілом.
     */
    public abstract @Nullable PhysicsDisplayComposite getDisplay();

    /**
     * Оновити стан об'єкта (позиція/орієнтація сутностей).
     * За замовчуванням — нічого не робить; реалізується в підкласах.
     */
    public abstract void update();
}