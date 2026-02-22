package com.ladakx.inertia.rendering.config;

import com.ladakx.inertia.common.logging.InertiaLogger;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Validates {@link RenderEntityDefinition#settings()} once during config load.
 * <p>
 * This is intentionally lenient: it warns on mistakes but doesn't fail startup.
 */
public final class RenderEntitySettingsValidator {

    private static final Set<String> COMMON_KEYS = Set.of(
            "silent",
            "gravity",
            "no-gravity",
            "invulnerable",
            "glowing",
            "collidable",
            "persistent",
            "custom-name",
            "custom-name-visible"
    );

    private static final Set<String> COMMON_SECTIONS = Set.of(
            "boat",
            "shulker",
            "interaction"
    );

    private static final Set<String> BOAT_KEYS = Set.of(
            "boat.type",
            "boat.chest",
            // legacy/short forms (supported by our runtime fallbacks)
            "type",
            "chest"
    );

    private static final Set<String> SHULKER_KEYS = Set.of(
            "shulker.color",
            "shulker.color-id",
            "shulker.peek",
            "shulker.ai",
            // legacy/short forms
            "color",
            "color-id",
            "peek",
            "ai"
    );

    private static final Set<String> INTERACTION_KEYS = Set.of(
            "interaction.width",
            "interaction.height",
            "interaction.responsive",
            // legacy/short forms
            "width",
            "height",
            "responsive"
    );

    private static final Set<String> BOAT_WOODS = Set.of(
            "OAK",
            "SPRUCE",
            "BIRCH",
            "JUNGLE",
            "ACACIA",
            "DARK_OAK",
            "MANGROVE",
            "CHERRY",
            "BAMBOO"
    );

    private RenderEntitySettingsValidator() {
        throw new UnsupportedOperationException();
    }

    public static void validate(String modelId, String entityKey, RenderEntityDefinition.EntityKind kind, Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) return;

        String ctx = "render model '" + modelId + "', entity '" + entityKey + "', kind " + kind;

        warnUnknownKeys(ctx, kind, settings);
        warnSemanticConflicts(ctx, kind, settings);
        validateCommonTypes(ctx, settings);

        switch (kind) {
            case BOAT -> validateBoat(ctx, settings);
            case SHULKER -> validateShulker(ctx, settings);
            case INTERACTION -> validateInteraction(ctx, settings);
            default -> {
                // For display kinds (BLOCK_DISPLAY/ITEM_DISPLAY/ARMOR_STAND) we only validate common keys.
            }
        }
    }

    private static void warnUnknownKeys(String ctx, RenderEntityDefinition.EntityKind kind, Map<String, Object> settings) {
        Set<String> allowedLeaf = new HashSet<>(COMMON_KEYS);
        Set<String> allowedSections = new HashSet<>(COMMON_SECTIONS);

        switch (kind) {
            case BOAT -> allowedLeaf.addAll(BOAT_KEYS);
            case SHULKER -> allowedLeaf.addAll(SHULKER_KEYS);
            case INTERACTION -> allowedLeaf.addAll(INTERACTION_KEYS);
            default -> {
            }
        }

        List<String> unknown = new ArrayList<>();
        List<String> unknownSections = new ArrayList<>();

        Deque<Map.Entry<String, Object>> stack = new ArrayDeque<>();
        for (Map.Entry<String, Object> e : settings.entrySet()) {
            stack.push(Map.entry(e.getKey(), e.getValue()));
        }

        while (!stack.isEmpty()) {
            Map.Entry<String, Object> entry = stack.pop();
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key == null || key.isBlank()) continue;

            if (value instanceof Map<?, ?> map) {
                if (!allowedSections.contains(key)) {
                    unknownSections.add(key);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) map;
                for (Map.Entry<String, Object> n : nested.entrySet()) {
                    String childKey = key + "." + n.getKey();
                    stack.push(Map.entry(childKey, n.getValue()));
                }
                continue;
            }

            if (!allowedLeaf.contains(key)) {
                unknown.add(key);
            }
        }

        if (!unknownSections.isEmpty()) {
            unknownSections.sort(String::compareTo);
            InertiaLogger.warn("Unknown settings section(s) in " + ctx + ": " + unknownSections);
        }
        if (!unknown.isEmpty()) {
            unknown.sort(String::compareTo);
            InertiaLogger.warn("Unknown settings key(s) in " + ctx + ": " + unknown);
        }
    }

    private static void warnSemanticConflicts(String ctx, RenderEntityDefinition.EntityKind kind, Map<String, Object> settings) {
        if (kind == RenderEntityDefinition.EntityKind.BOAT) {
            if (RenderSettings.get(settings, "boat.type") != null && RenderSettings.get(settings, "type") != null) {
                InertiaLogger.warn("Both 'boat.type' and 'type' are set in " + ctx + ". Prefer 'boat.type'.");
            }
            if (RenderSettings.get(settings, "boat.chest") != null && RenderSettings.get(settings, "chest") != null) {
                InertiaLogger.warn("Both 'boat.chest' and 'chest' are set in " + ctx + ". Prefer 'boat.chest'.");
            }
        }
        if (kind == RenderEntityDefinition.EntityKind.INTERACTION) {
            if (RenderSettings.get(settings, "interaction.width") != null && RenderSettings.get(settings, "width") != null) {
                InertiaLogger.warn("Both 'interaction.width' and 'width' are set in " + ctx + ". Prefer 'interaction.width'.");
            }
            if (RenderSettings.get(settings, "interaction.height") != null && RenderSettings.get(settings, "height") != null) {
                InertiaLogger.warn("Both 'interaction.height' and 'height' are set in " + ctx + ". Prefer 'interaction.height'.");
            }
            if (RenderSettings.get(settings, "interaction.responsive") != null && RenderSettings.get(settings, "responsive") != null) {
                InertiaLogger.warn("Both 'interaction.responsive' and 'responsive' are set in " + ctx + ". Prefer 'interaction.responsive'.");
            }
        }
        if (kind == RenderEntityDefinition.EntityKind.SHULKER) {
            if (RenderSettings.get(settings, "shulker.color") != null && RenderSettings.get(settings, "color") != null) {
                InertiaLogger.warn("Both 'shulker.color' and 'color' are set in " + ctx + ". Prefer 'shulker.color'.");
            }
            if (RenderSettings.get(settings, "shulker.peek") != null && RenderSettings.get(settings, "peek") != null) {
                InertiaLogger.warn("Both 'shulker.peek' and 'peek' are set in " + ctx + ". Prefer 'shulker.peek'.");
            }
            if (RenderSettings.get(settings, "shulker.ai") != null && RenderSettings.get(settings, "ai") != null) {
                InertiaLogger.warn("Both 'shulker.ai' and 'ai' are set in " + ctx + ". Prefer 'shulker.ai'.");
            }
        }

        Boolean gravity = RenderSettings.getBoolean(settings, "gravity");
        Boolean noGravity = RenderSettings.getBoolean(settings, "no-gravity");
        if (gravity != null && noGravity != null) {
            InertiaLogger.warn("Both 'gravity' and 'no-gravity' are set in " + ctx + ". They affect different pipelines (Bukkit vs network), ensure it's intentional.");
        }
    }

    private static void validateCommonTypes(String ctx, Map<String, Object> settings) {
        validateBoolean(ctx, settings, "silent");
        validateBoolean(ctx, settings, "gravity");
        validateBoolean(ctx, settings, "no-gravity");
        validateBoolean(ctx, settings, "invulnerable");
        validateBoolean(ctx, settings, "glowing");
        validateBoolean(ctx, settings, "collidable");
        validateBoolean(ctx, settings, "persistent");
        validateBoolean(ctx, settings, "custom-name-visible");
        validateString(ctx, settings, "custom-name");
    }

    private static void validateBoat(String ctx, Map<String, Object> settings) {
        validateBoolean(ctx, settings, "boat.chest");
        validateBoolean(ctx, settings, "chest");

        String type = RenderSettings.getString(settings, "boat.type");
        if (type == null) type = RenderSettings.getString(settings, "type");
        if (type == null) return;
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!normalized.isEmpty() && !BOAT_WOODS.contains(normalized)) {
            InertiaLogger.warn("Suspicious boat wood type '" + type + "' in " + ctx + ". Expected one of " + BOAT_WOODS + ".");
        }
    }

    private static void validateShulker(String ctx, Map<String, Object> settings) {
        validateBoolean(ctx, settings, "shulker.ai");
        validateBoolean(ctx, settings, "ai");

        Integer colorId = RenderSettings.getInt(settings, "shulker.color-id");
        if (colorId == null) colorId = RenderSettings.getInt(settings, "color-id");
        if (colorId != null && (colorId < -1 || colorId > 15)) {
            InertiaLogger.warn("Invalid shulker color-id " + colorId + " in " + ctx + ". Expected -1..15.");
        }

        String color = RenderSettings.getString(settings, "shulker.color");
        if (color == null) color = RenderSettings.getString(settings, "color");
        if (color != null && !color.isBlank()) {
            String normalized = color.trim().toUpperCase(Locale.ROOT);
            // Bukkit DyeColor is -ish stable; keep validator permissive.
            if (!normalized.equals("WHITE") && !normalized.equals("ORANGE") && !normalized.equals("MAGENTA") && !normalized.equals("LIGHT_BLUE")
                    && !normalized.equals("YELLOW") && !normalized.equals("LIME") && !normalized.equals("PINK") && !normalized.equals("GRAY")
                    && !normalized.equals("LIGHT_GRAY") && !normalized.equals("CYAN") && !normalized.equals("PURPLE") && !normalized.equals("BLUE")
                    && !normalized.equals("BROWN") && !normalized.equals("GREEN") && !normalized.equals("RED") && !normalized.equals("BLACK")) {
                InertiaLogger.warn("Suspicious shulker color '" + color + "' in " + ctx + ". Expected a DyeColor name or use 'color-id'.");
            }
        }

        Integer peek = RenderSettings.getInt(settings, "shulker.peek");
        if (peek == null) peek = RenderSettings.getInt(settings, "peek");
        if (peek != null && (peek < 0 || peek > 100)) {
            InertiaLogger.warn("Invalid shulker peek " + peek + " in " + ctx + ". Expected 0..100.");
        }
    }

    private static void validateInteraction(String ctx, Map<String, Object> settings) {
        validateBoolean(ctx, settings, "interaction.responsive");
        validateBoolean(ctx, settings, "responsive");

        Double width = RenderSettings.getDouble(settings, "interaction.width");
        if (width == null) width = RenderSettings.getDouble(settings, "width");
        if (width != null && width <= 0.0) {
            InertiaLogger.warn("Invalid interaction width " + width + " in " + ctx + ". Expected > 0.");
        }

        Double height = RenderSettings.getDouble(settings, "interaction.height");
        if (height == null) height = RenderSettings.getDouble(settings, "height");
        if (height != null && height <= 0.0) {
            InertiaLogger.warn("Invalid interaction height " + height + " in " + ctx + ". Expected > 0.");
        }
    }

    private static void validateBoolean(String ctx, Map<String, Object> settings, String key) {
        Object raw = RenderSettings.get(settings, key);
        if (raw == null) return;
        if (raw instanceof Boolean) return;
        if (raw instanceof String s) {
            String normalized = s.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("true") || normalized.equals("false")) return;
        }
        InertiaLogger.warn("Expected boolean for settings key '" + key + "' in " + ctx + ", got: " + raw + " (" + raw.getClass().getSimpleName() + ")");
    }

    private static void validateString(String ctx, Map<String, Object> settings, String key) {
        Object raw = RenderSettings.get(settings, key);
        if (raw == null) return;
        if (raw instanceof String) return;
        InertiaLogger.warn("Expected string for settings key '" + key + "' in " + ctx + ", got: " + raw + " (" + raw.getClass().getSimpleName() + ")");
    }
}

