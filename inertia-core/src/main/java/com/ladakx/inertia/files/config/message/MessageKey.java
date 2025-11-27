package com.ladakx.inertia.files.config.message;

public enum MessageKey {
    RELOAD_PLUGIN("reload-plugin"),
    NO_PERMISSIONS("no-permissions"), // Було No_Perm, стало повне слово для ясності
    NOT_FOR_CONSOLE("not-for-console"),
    PLAYER_NOT_FOUND("player-not-found"),
    WRONG_ARGS_COMMAND("wrong-args-command"),
    HELP_COMMAND("help-command"),
    HELP_COMMAND_ADMIN("help-command-admin"),
    NOT_FOR_THIS_VERSION("not-for-this-version"),
    NOT_FOR_THIS_WORLD("not-for-this-world"),
    SELECT_REGION("select-region"),
    DEBUGSHAPE_CREATE_SUCCESS("debug-shape-create-success"), // Розділив debugshape
    WORLDEDIT_NOT_ENABLED("worldedit-not-enabled"),
    COMMAND_DOES_NOT_EXIST("command-not-found"),

    SPAWN_SUCCESS("spawn-success"),
    SPAWN_FAIL_INVALID_ID("spawn-fail-invalid-id"),
    CLEAR_SUCCESS("clear-success");

    private final String path;

    MessageKey(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}