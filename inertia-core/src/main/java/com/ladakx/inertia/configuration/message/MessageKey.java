package com.ladakx.inertia.configuration.message;

public enum MessageKey {
    // System
    PREFIX("prefix"),
    RELOAD_PLUGIN("reload-plugin"),
    NO_PERMISSIONS("no-permissions"),
    NOT_FOR_CONSOLE("not-for-console"),
    NOT_FOR_THIS_VERSION("not-for-this-version"),
    NOT_FOR_THIS_WORLD("not-for-this-world"),
    COMMAND_NOT_FOUND("command-not-found"),
    WRONG_ARGS_COMMAND("wrong-args-command"),
    ERROR_OCCURRED("error-occurred"),

    // WorldEdit
    WORLDEDIT_NOT_ENABLED("worldedit-not-enabled"),
    SELECT_REGION("select-region"),

    // Spawn
    SPAWN_SUCCESS("spawn-success"),
    SPAWN_FAIL_INVALID_ID("spawn-fail-invalid-id"),
    CHAIN_SPAWN_SUCCESS("chain-spawn-success"),
    RAGDOLL_SPAWN_SUCCESS("ragdoll-spawn-success"),
    SHAPE_SPAWN_SUCCESS("shape-spawn-success"),
    CLEAR_SUCCESS("clear-success"),
    CLEAR_SUCCESS_RADIUS("clear-success-radius"),
    CLEAR_SUCCESS_FILTER("clear-success-filter"),
    CLEAR_SUCCESS_COMBO("clear-success-combo"),
    CLEAR_NO_MATCH("clear-no-match"),

    // Shapes
    SHAPE_NOT_FOUND("shape-not-found"),
    SHAPE_LIST_AVAILABLE("shape-list-available"),
    SHAPE_USAGE("shape-usage"),
    SHAPE_INVALID_PARAMS("shape-invalid-params"),

    // Tools - Generic
    TOOL_RECEIVED("tool-received"),
    TOOL_NOT_FOUND("tool-not-found"),
    TOOL_BROKEN_NBT("tool-broken-nbt"),
    TOOL_INVALID_PARAMS("tool-invalid-params"),

    // Tools - Names & Lore
    TOOL_CHAIN_NAME("tool-chain-name"),
    TOOL_CHAIN_LORE("tool-chain-lore"),

    TOOL_RAGDOLL_NAME("tool-ragdoll-name"),
    TOOL_RAGDOLL_LORE("tool-ragdoll-lore"),

    TOOL_SHAPE_NAME("tool-shape-name"),
    TOOL_SHAPE_LORE("tool-shape-lore"),

    TOOL_TNT_NAME("tool-tnt-name"),
    TOOL_TNT_LORE("tool-tnt-lore"),

    TOOL_GRABBER_NAME("tool-grabber-name"),
    TOOL_GRABBER_LORE("tool-grabber-lore"),

    TOOL_WELDER_NAME("tool-welder-name"),
    TOOL_WELDER_LORE("tool-welder-lore"),

    TOOL_REMOVER_NAME("tool-remover-name"),
    TOOL_REMOVER_LORE("tool-remover-lore"),

    // Tools - Specific
    SHAPE_SPAWNED("shape-spawned"),
    SHAPE_SPAWN_ERROR("shape-spawn-error"),

    CHAIN_POINT_SET("chain-point-set"),
    CHAIN_SELECT_FIRST("chain-select-first"),
    CHAIN_MISSING_ID("chain-missing-id"),
    CHAIN_BUILDING("chain-building"),

    SELECTION_CLEARED("selection-cleared"),
    CHAIN_CREATED("chain-created"),
    INVALID_CHAIN_BODY("invalid-chain-body"),

    INVALID_RAGDOLL_BODY("invalid-ragdoll-body"),

    WELD_MODE_CHANGE("weld-mode-change"),
    WELD_REMOVED("weld-removed"),
    WELD_FIRST_SELECTED("weld-first-selected"),
    WELD_DESELECTED("weld-deselected"),
    WELD_CONNECTED("weld-connected"),

    REMOVER_USED("remover-used"),

    TNT_SPAWNED("tnt-spawned"),
    TNT_TOOL_RECEIVED("tnt-tool-received"),
    TNT_INVALID_FORCE("tnt-invalid-force"),
    TNT_TOOL_USAGE("tnt-tool-usage"),

    // Help
    HELP_COMMAND("help-command"),
    HELP_COMMAND_ADMIN("help-command-admin");

    private final String path;

    MessageKey(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}