package com.ladakx.inertia.api.rendering.model;

/**
 * Strategy for mapping model IDs from a foreign config section into the Inertia runtime registry.
 */
public enum RenderIdPolicy {
    /**
     * Use IDs exactly as provided in the config section.
     */
    AS_IS,
    /**
     * If the ID is not namespaced (no ':'), prefix it with {@code owner.getName() + ":"}.
     */
    NAMESPACE_OWNER_IF_MISSING
}

