package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.render.runtime.PhysicsDisplayComposite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DisplayedPhysicsObject extends AbstractPhysicsObject {

    public DisplayedPhysicsObject(@NotNull MinecraftSpace space, @NotNull BodyCreationSettings bodySettings) {
        super(space, bodySettings);
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
