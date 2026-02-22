package com.ladakx.inertia.api.rendering.entity;

import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ordered collection of local transform components (composition stack).
 * <p>
 * Default state is empty (identity transform).
 * <p>
 * <b>Order of application</b>
 * <p>
 * Components are applied in insertion order (first inserted = applied first).
 * For each component {@code C}:
 * <ol>
 *     <li>{@code pos += rot * C.translation}</li>
 *     <li>{@code rot = rot * C.rotation}</li>
 * </ol>
 * After all components are applied, the stack produces a single local transform (pos, rot) that can be
 * applied on top of a base/world transform as:
 * <ul>
 *     <li>{@code worldRot = baseRot * localRot}</li>
 *     <li>{@code worldPos = basePos + baseRot * localPos}</li>
 * </ul>
 * This matches the typical parent->child transform composition model and is predictable across updates.
 * <p>
 * Performance: the stack caches the composed local transform and recomputes it only when components change.
 */
public final class TransformStack {

    private final LinkedHashMap<String, TransformComponent> components = new LinkedHashMap<>();
    private final Vector3f composedPos = new Vector3f();
    private final Quaternionf composedRot = new Quaternionf();
    private boolean dirty = true;

    // scratch to avoid allocations during composition
    private final Vector3f scratchVec = new Vector3f();

    public void put(@NotNull String key, @NotNull TransformComponent component) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(component, "component");
        TransformComponent prev = components.put(key, component);
        if (prev != component) {
            dirty = true;
        }
    }

    public boolean remove(@NotNull String key) {
        Objects.requireNonNull(key, "key");
        boolean removed = components.remove(key) != null;
        if (removed) dirty = true;
        return removed;
    }

    public void clear() {
        if (components.isEmpty()) return;
        components.clear();
        dirty = true;
    }

    public boolean isEmpty() {
        return components.isEmpty();
    }

    /**
     * Returns a snapshot view (in insertion order).
     */
    public @NotNull Map<String, TransformComponent> components() {
        return Collections.unmodifiableMap(components);
    }

    /**
     * Writes composed local translation into {@code out} and returns it.
     */
    public @NotNull Vector3f getLocalTranslation(@NotNull Vector3f out) {
        Objects.requireNonNull(out, "out");
        recomputeIfDirty();
        return out.set(composedPos);
    }

    /**
     * Writes composed local rotation into {@code out} and returns it.
     */
    public @NotNull Quaternionf getLocalRotation(@NotNull Quaternionf out) {
        Objects.requireNonNull(out, "out");
        recomputeIfDirty();
        return out.set(composedRot);
    }

    private void recomputeIfDirty() {
        if (!dirty) return;
        dirty = false;

        composedPos.zero();
        composedRot.identity();

        for (TransformComponent component : components.values()) {
            // pos += rot * t
            scratchVec.set(component.translation());
            composedRot.transform(scratchVec);
            composedPos.add(scratchVec);

            // rot = rot * r
            composedRot.mul(component.rotation());
        }
    }
}

