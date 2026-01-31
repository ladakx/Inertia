package com.ladakx.inertia.common.pdc;

import com.ladakx.inertia.core.InertiaPlugin;
import org.bukkit.NamespacedKey;

public class InertiaPDCKeys {

    /**
     * The ID of the physics body.
     * Format: String
     */
    public static final NamespacedKey INERTIA_PHYSICS_BODY_ID
            = new NamespacedKey(InertiaPlugin.getInstance(), "body-id");

    /**
     * The UUID of the physics body.
     * Format: String
     */
    public static final NamespacedKey INERTIA_PHYSICS_BODY_UUID
            = new NamespacedKey(InertiaPlugin.getInstance(), "body-uuid");

    /**
     * Whether the physics body is simulated.
     * Format: String (true/false)
     */
    public static final NamespacedKey INERTIA_ENTITY_STATIC
            = new NamespacedKey(InertiaPlugin.getInstance(), "entity-static");

    /**
     * The render model ID used for rendering the model.
     * Format: String
     */
    public static final NamespacedKey INERTIA_RENDER_MODEL_ID
            = new NamespacedKey(InertiaPlugin.getInstance(), "render-model-id");

    /**
     * The entity ID of the used for rendering the model.
     * Format: String
     */
    public static final NamespacedKey INERTIA_RENDER_MODEL_ENTITY_ID
            = new NamespacedKey(InertiaPlugin.getInstance(), "render-model-entity-id");
}
