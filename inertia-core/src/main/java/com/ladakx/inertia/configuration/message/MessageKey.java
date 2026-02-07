package com.ladakx.inertia.configuration.message;

public enum MessageKey {
    PREFIX("prefix"),
    RELOAD_PLUGIN("reload-plugin"),
    NOT_FOR_CONSOLE("not-for-console"),
    NOT_FOR_THIS_WORLD("not-for-this-world"),
    ERROR_OCCURRED("error-occurred"),

    SPAWN_LIMIT_REACHED("spawn-limit-reached"),
    SPAWN_FAIL_OBSTRUCTED("spawn-fail-obstructed"),
    SPAWN_FAIL_OUT_OF_BOUNDS("spawn-fail-out-of-bounds"),
    SPAWN_SUCCESS("spawn-success"),
    CHAIN_SPAWN_SUCCESS("chain-spawn-success"),
    RAGDOLL_SPAWN_SUCCESS("ragdoll-spawn-success"),
    SHAPE_SPAWN_SUCCESS("shape-spawn-success"),

    CLEAR_SUCCESS("clear-success"),
    CLEAR_SUCCESS_RADIUS("clear-success-radius"),
    CLEAR_NO_MATCH("clear-no-match"),

    SHAPE_NOT_FOUND("shape-not-found"),
    SHAPE_USAGE("shape-usage"),
    SHAPE_INVALID_PARAMS("shape-invalid-params"),

    TOOL_RECEIVED("tool-received"),
    TOOL_NOT_FOUND("tool-not-found"),
    TOOL_BROKEN_NBT("tool-broken-nbt"),

    BODY_FROZEN("body-frozen"),
    CANNOT_FREEZE_BODY("cannot-freeze-body"),
    STATIC_SUCCESS("static-success"),

    TOOL_CHAIN_NAME("tool-chain-name"),
    TOOL_CHAIN_LORE("tool-chain-lore"),
    TOOL_RAGDOLL_NAME("tool-ragdoll-name"),
    TOOL_RAGDOLL_LORE("tool-ragdoll-lore"),
    TOOL_SHAPE_NAME("tool-shape-name"),
    TOOL_SHAPE_LORE("tool-shape-lore"),
    TOOL_TNT_NAME("tool-tnt-name"),
    TOOL_TNT_LORE("tool-tnt-lore"),
    TOOL_STATIC_NAME("tool-static-name"),
    TOOL_STATIC_LORE("tool-static-lore"),
    TOOL_GRABBER_NAME("tool-grabber-name"),
    TOOL_GRABBER_LORE("tool-grabber-lore"),
    TOOL_WELDER_NAME("tool-welder-name"),
    TOOL_WELDER_LORE("tool-welder-lore"),
    TOOL_REMOVER_NAME("tool-remover-name"),
    TOOL_REMOVER_LORE("tool-remover-lore"),
    TOOL_INSPECT_NAME("tool-inspect-name"),
    TOOL_INSPECT_LORE("tool-inspect-lore"),

    SHAPE_SPAWNED("shape-spawned"),
    SHAPE_SPAWN_ERROR("shape-spawn-error"),
    CHAIN_POINT_SET("chain-point-set"),
    CHAIN_SELECT_FIRST("chain-select-first"),
    CHAIN_MISSING_ID("chain-missing-id"),
    CHAIN_BUILDING("chain-building"),
    SELECTION_CLEARED("selection-cleared"),
    INVALID_CHAIN_BODY("invalid-chain-body"),
    INVALID_RAGDOLL_BODY("invalid-ragdoll-body"),
    WELD_MODE_CHANGE("weld-mode-change"),
    WELD_FIRST_SELECTED("weld-first-selected"),
    WELD_DESELECTED("weld-deselected"),
    WELD_CONNECTED("weld-connected"),
    REMOVER_USED("remover-used"),

    TNT_SPAWNED("tnt-spawned"),
    TNT_TOOL_RECEIVED("tnt-tool-received"),

    ADMIN_SIMULATION_PAUSED("admin-simulation-paused"),
    ADMIN_STATS_HEADER("admin-stats-header"),
    ADMIN_STATS_PERFORMANCE("admin-stats-performance"),
    ADMIN_STATS_BODIES("admin-stats-bodies"),
    ADMIN_STATS_FOOTER("admin-stats-footer"),

    DEBUG_INSPECT_HEADER("debug-inspect-header"),
    DEBUG_INSPECT_INFO("debug-inspect-info"),
    DEBUG_INSPECT_VELOCITY("debug-inspect-velocity"),
    DEBUG_INSPECT_PROPS("debug-inspect-props"),
    DEBUG_INSPECT_STATE("debug-inspect-state"),
    DEBUG_INSPECT_FOOTER("debug-inspect-footer"),
    DEBUG_INSPECT_MISS("debug-inspect-miss"),

    DEBUG_PERF_TOGGLE("debug-perf-toggle"),
    DEBUG_PERF_FORMAT("debug-perf-format"),
    DEBUG_HITBOX_TOGGLE("debug-hitbox-toggle"),

    HELP_INDEX_1("help-index-1"),
    HELP_INDEX_2("help-index-2"),
    HELP_TIP_TOPICS("help-tip-topics"),
    HELP_SPAWN_1("help-spawn-1"),
    HELP_SPAWN_2("help-spawn-2"),
    HELP_CLEAR_1("help-clear-1"),
    HELP_ENTITY_1("help-entity-1"),
    HELP_DEBUG_1("help-debug-1"),
    HELP_TOOL_1("help-tool-1"),
    HELP_ADMIN_1("help-admin-1");

    private final String path;

    MessageKey(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
