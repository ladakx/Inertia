package com.ladakx.inertia.render.config.enums;

import java.util.Locale;

public enum InertiaDisplayMode {
    NONE,
    THIRDPERSON_LEFTHAND,
    THIRDPERSON_RIGHTHAND,
    FIRSTPERSON_LEFTHAND,
    FIRSTPERSON_RIGHTHAND,
    HEAD,
    GUI,
    GROUND,
    FIXED;

    public static InertiaDisplayMode parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}