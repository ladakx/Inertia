package com.ladakx.inertia.utils.mesh;

import com.github.stephengold.joltjni.Vec3;

import java.util.Collection;

/**
 * Провайдер мешів для побудови ConvexHullShape.
 * <p>
 * Ідея:
 *   - У shape-конфігу ти вказуєш логічний ID/шлях меша, наприклад:
 *       "type=convex_hull mesh=car_body"
 *       "type=convex_hull mesh=models/vehicle.obj"
 *   - Реалізація MeshProvider сама вирішує, як інтерпретувати цей рядок:
 *       * завантажити BlockBench-JSON і дістати вершини;
 *       * або прочитати Wavefront OBJ, який ти експортував із BlockBench;
 *       * або щось інше.
 * <p>
 * Повертаються вершини у локальних координатах моделі, які підуть у
 * ConvexHullShapeSettings(points).create().
 */
public interface MeshProvider {

    /**
     * Завантажити вершини для побудови опуклої оболонки.
     *
     * @param meshId логічний ID/шлях меша з конфігу
     * @return колекція вершин (не null, але може кинути виняток, якщо нічого не знайдено)
     */
    Collection<Vec3> loadConvexHullPoints(String meshId);
}