package com.ladakx.inertia.rendering.config.enums;

import java.util.Locale;

public enum InertiaBillboard {
    FIXED,
    VERTICAL,
    HORIZONTAL,
    CENTER;

    public static InertiaBillboard parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}