package com.ladakx.inertia.files.config.message;

public enum MessageKey {
    RELOAD_PLUGIN("reload-plugin"),
    NO_PERMISSIONS("no-permissions"), // Було No_Perm, стало повне слово для ясності
    NOT_FOR_CONSOLE("not-for-console"),
    PLAYER_NOT_FOUND("player-not-found"),
    WRONG_ARGS_COMMAND("wrong-args-command"),
    DEBUG_RENDER_HITBOXES("debug-render-hitboxes"),
    HELP_COMMAND("help-command"),
    HELP_COMMAND_ADMIN("help-command-admin"),
    DEBUG_BLOCK_BB_TITLE("debug-block-bb-title"),
    DEBUG_BLOCK_BB_INFO("debug-block-bb-info"),
    DEBUG_BLOCK_NOT_FOUND("debug-block-not-found"),
    DEBUG_BLOCKS_SPAWNED("debug-blocks-spawned"),
    DEBUG_BLOCKS_CLEARED("debug-blocks-cleared"),
    NOT_FOR_THIS_VERSION("not-for-this-version"),
    NOT_FOR_THIS_WORLD("not-for-this-world"),
    SELECT_REGION("select-region"),
    DEBUGSHAPE_CREATE_SUCCESS("debug-shape-create-success"), // Розділив debugshape
    WORLDEDIT_NOT_ENABLED("worldedit-not-enabled"),
    COMMAND_DOES_NOT_EXIST("command-not-found");

    private final String path;

    MessageKey(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}