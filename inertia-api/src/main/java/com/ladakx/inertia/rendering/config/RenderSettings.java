package com.ladakx.inertia.rendering.config;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight typed access to {@link RenderEntityDefinition#settings()}.
 * <p>
 * This intentionally stays simple and allocation-light. Parsing and validation
 * is expected to happen when configs are loaded (in inertia-core).
 */
public final class RenderSettings {

    private RenderSettings() {
        throw new UnsupportedOperationException();
    }

    public static @Nullable Object get(Map<String, Object> settings, String key) {
        if (settings == null || settings.isEmpty() || key == null || key.isEmpty()) return null;
        Object current = settings;
        int from = 0;
        while (true) {
            int dot = key.indexOf('.', from);
            String part = dot == -1 ? key.substring(from) : key.substring(from, dot);
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
            if (dot == -1) return current;
            from = dot + 1;
        }
    }

    public static @Nullable String getString(Map<String, Object> settings, String key) {
        Object v = get(settings, key);
        if (v == null) return null;
        if (v instanceof String s) return s;
        return String.valueOf(v);
    }

    public static @Nullable Boolean getBoolean(Map<String, Object> settings, String key) {
        Object v = get(settings, key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) {
            String raw = s.trim().toLowerCase(Locale.ROOT);
            if (raw.equals("true")) return true;
            if (raw.equals("false")) return false;
        }
        return null;
    }

    public static @Nullable Integer getInt(Map<String, Object> settings, String key) {
        Object v = get(settings, key);
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public static @Nullable Float getFloat(Map<String, Object> settings, String key) {
        Object v = get(settings, key);
        if (v instanceof Float f) return f;
        if (v instanceof Number n) return n.floatValue();
        if (v instanceof String s) {
            try {
                return Float.parseFloat(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public static @Nullable Double getDouble(Map<String, Object> settings, String key) {
        Object v = get(settings, key);
        if (v instanceof Double d) return d;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static @Nullable Map<String, Object> getSection(Map<String, Object> settings, String key) {
        Object v = get(settings, key);
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    @SuppressWarnings("unchecked")
    public static @Nullable List<Object> getList(Map<String, Object> settings, String key) {
        Object v = get(settings, key);
        if (v instanceof List<?> list) return (List<Object>) list;
        return null;
    }

    public static <E extends Enum<E>> @Nullable E getEnum(Map<String, Object> settings, String key, Class<E> enumClass) {
        String raw = getString(settings, key);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Enum.valueOf(enumClass, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

