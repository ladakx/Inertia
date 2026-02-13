package com.ladakx.inertia.api.interaction;

import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface PhysicsInteraction {

    /**
     * Выпускает луч из точки start в направлении direction на расстояние distance.
     * Возвращает информацию о первом (ближайшем) столкновении.
     *
     * @param start     Точка начала луча.
     * @param direction Направление луча.
     * @param distance  Максимальная дистанция.
     * @return RaycastHit или null, если ничего не задето.
     */
    @Nullable
    RaycastHit raycast(@NotNull Location start, @NotNull Vector direction, double distance);

    /**
     * Выпускает луч и возвращает ВСЕ столкновения на пути луча, отсортированные по дистанции.
     */
    @NotNull
    List<RaycastHit> raycastAll(@NotNull Location start, @NotNull Vector direction, double distance);

    /**
     * Возвращает список всех физических тел, находящихся в радиусе radius от точки center.
     */
    @NotNull
    Collection<InertiaPhysicsBody> getOverlappingSphere(@NotNull Location center, double radius);

    /**
     * Создает физический взрыв, расталкивающий объекты.
     *
     * @param center Точка эпицентра.
     * @param force  Сила взрыва.
     * @param radius Радиус воздействия.
     */
    void createExplosion(@NotNull Location center, float force, float radius);
}