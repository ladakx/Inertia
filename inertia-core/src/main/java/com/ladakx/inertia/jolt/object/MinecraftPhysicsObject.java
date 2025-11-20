package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Абстрактний клас для фізичних об’єктів Minecraft, пов’язаних з Jolt Body.
 * Забезпечує створення тіла, керування життєвим циклом та оновлення позиції Bukkit-сутності.
 */
public abstract class MinecraftPhysicsObject {

    private final List<Integer> relatedBodies = new CopyOnWriteArrayList<>();
    private final List<TwoBodyConstraintRef> constraints = new CopyOnWriteArrayList<>();

    private final @NotNull MinecraftSpace space;
    private final @NotNull BodyCreationSettings bodySettings;
    private final @NotNull Body body;

    private @Nullable Entity entity;

    public MinecraftPhysicsObject(@NotNull MinecraftSpace space, @NotNull BodyCreationSettings bodySettings) {
        this.space = space;
        this.bodySettings = bodySettings;

        this.body = space.getBodyInterface().createBody(this.bodySettings);
        space.getBodyInterface().addBody(body, EActivation.Activate);
        space.addObject(this);
    }

    /**
     * Створює та зберігає Bukkit-сутність, пов’язану з цим фізичним тілом.
     * Можна викликати після створення об’єкта.
     */
    public @Nullable Entity setInstance() {
        this.entity = createEntity();
        if (this.entity != null) {
            RVec3 pos = body.getPosition();

            Location location = new Location(
                    space.getWorldBukkit(),
                    pos.xx(),
                    pos.yy(),
                    pos.zz()
            );
            this.entity.teleport(location);
        }
        return this.entity;
    }

    public void addRelated(@NotNull Body related) {
        this.relatedBodies.add(related.getId());
    }

    public void addRelatedConstraint(@NotNull TwoBodyConstraintRef related) {
        this.constraints.add(related);
    }

    public void removeRelatedConstraint(@NotNull TwoBodyConstraintRef related) {
        this.constraints.remove(related);
    }

    public void destroy() {
        // Видаляємо всі констрейнти
        for (TwoBodyConstraintRef constraint : constraints) {
            space.removeConstraint(constraint.getPtr());
        }

        // Видаляємо пов’язані тіла
        var bi = space.getPhysicsSystem().getBodyInterface();
        for (int relatedObject : relatedBodies) {
            bi.removeBody(relatedObject);
            bi.destroyBody(relatedObject);
        }

        // Видаляємо основне тіло
        bi.removeBody(body.getId());
        bi.destroyBody(body.getId());

        // Прибираємо з менеджера простору
        space.removeObject(this);

        // Видаляємо Bukkit-сутність
        if (entity != null) {
            entity.remove();
            entity = null;
        }
    }

    public void activate() {
        space.getPhysicsSystem().getBodyInterface().activateBody(body.getId());
    }

    public boolean isActive() {
        return body.isActive();
    }

    public @NotNull Body getBody() {
        return body;
    }

    /**
     * Створення Bukkit-сутності (звичайно через World#spawnEntity).
     * Місце спавна / тип сутності вирішується в реалізації.
     */
    public abstract @Nullable Entity createEntity();

    public @Nullable Entity getEntity() {
        return entity;
    }

    /**
     * Оновити позицію та орієнтацію Bukkit-сутності згідно з Jolt-тілом.
     * Викликається щотік з тік-лупу плагіна.
     */
    public void update() {

    }

    protected @NotNull MinecraftSpace getSpace() {
        return space;
    }
}