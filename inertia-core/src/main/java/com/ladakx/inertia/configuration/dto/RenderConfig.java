package com.ladakx.inertia.configuration.dto;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.MinecraftVersions;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.rendering.config.RenderEntitySettingsValidator;
import com.ladakx.inertia.rendering.config.RenderModelSelector;
import com.ladakx.inertia.rendering.config.RenderModelVariant;
import com.ladakx.inertia.rendering.version.ClientVersionRange;
import com.ladakx.inertia.rendering.config.enums.InertiaBillboard;
import com.ladakx.inertia.rendering.config.enums.InertiaDisplayMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.*;

public final class RenderConfig {

    private final Map<String, RenderModelSelector> selectors;

    public RenderConfig(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        this.selectors = Collections.unmodifiableMap(parse(config));
    }

    private Map<String, RenderModelSelector> parse(FileConfiguration config) {
        Map<String, RenderModelSelector> result = new LinkedHashMap<>();

        for (String modelId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(modelId);
            if (section == null) continue;

            ConfigurationSection variantsSec = section.getConfigurationSection("variants");
            if (variantsSec != null) {
                List<RenderModelVariant> variants = parseVariants(modelId, variantsSec);
                if (variants.isEmpty()) {
                    InertiaLogger.warn("Render model '" + modelId + "' has 'variants' but none were parsed");
                    continue;
                }
                result.put(modelId, () -> variants);
                continue;
            }

            boolean syncPos = section.getBoolean("sync.position", true);
            boolean syncRot = section.getBoolean("sync.rotation", true);

            ConfigurationSection entitiesSec = section.getConfigurationSection("entities");
            if (entitiesSec == null) {
                InertiaLogger.warn("Render model '" + modelId + "' has no 'entities' section");
                continue;
            }

            Map<String, RenderEntityDefinition> entities = new LinkedHashMap<>();

            for (String entityKey : entitiesSec.getKeys(false)) {
                ConfigurationSection eSec = entitiesSec.getConfigurationSection(entityKey);
                if (eSec == null) continue;

                RenderEntityDefinition def = parseEntity(modelId, entityKey, eSec);
                if (def != null) {
                    entities.put(entityKey, def);
                }
            }

            RenderModelDefinition modelDef = new RenderModelDefinition(modelId, syncPos, syncRot, entities);
            if (result.put(modelId, new com.ladakx.inertia.rendering.config.SingleRenderModelSelector(modelDef)) != null) {
                InertiaLogger.warn("Duplicate render model id '" + modelId + "', overriding");
            }
        }
        return result;
    }

    private List<RenderModelVariant> parseVariants(String modelId, ConfigurationSection variantsSec) {
        List<RenderModelVariant> out = new ArrayList<>();
        for (String rangeKey : variantsSec.getKeys(false)) {
            ConfigurationSection variantSec = variantsSec.getConfigurationSection(rangeKey);
            if (variantSec == null) {
                InertiaLogger.warn("Render model '" + modelId + "' variant '" + rangeKey + "' is not a section");
                continue;
            }

            ClientVersionRange range = ClientVersionRange.parse(rangeKey);
            if (range == null) {
                InertiaLogger.warn("Render model '" + modelId + "' has invalid variant range '" + rangeKey + "'");
                continue;
            }

            boolean syncPos = variantSec.getBoolean("sync.position", true);
            boolean syncRot = variantSec.getBoolean("sync.rotation", true);

            ConfigurationSection entitiesSec = variantSec.getConfigurationSection("entities");
            if (entitiesSec == null) {
                InertiaLogger.warn("Render model '" + modelId + "' variant '" + rangeKey + "' has no 'entities' section");
                continue;
            }

            Map<String, RenderEntityDefinition> entities = new LinkedHashMap<>();
            for (String entityKey : entitiesSec.getKeys(false)) {
                ConfigurationSection eSec = entitiesSec.getConfigurationSection(entityKey);
                if (eSec == null) continue;

                RenderEntityDefinition def = parseEntity(modelId + "@" + rangeKey, entityKey, eSec);
                if (def != null) {
                    entities.put(entityKey, def);
                }
            }
            RenderModelDefinition def = new RenderModelDefinition(modelId + "@" + rangeKey, syncPos, syncRot, entities);
            out.add(new RenderModelVariant(range, def));
        }

        out.sort(Comparator.comparingInt(v -> v.clientRange().minProtocol()));
        // Overlap warnings (best-effort)
        for (int i = 0; i < out.size(); i++) {
            for (int j = i + 1; j < out.size(); j++) {
                ClientVersionRange a = out.get(i).clientRange();
                ClientVersionRange b = out.get(j).clientRange();
                if (a == null || b == null) continue;
                boolean overlaps = a.minProtocol() <= b.maxProtocol() && b.minProtocol() <= a.maxProtocol();
                if (overlaps) {
                    InertiaLogger.warn("Render model '" + modelId + "' has overlapping variant ranges: '" + a.raw() + "' and '" + b.raw() + "'");
                }
            }
        }

        return Collections.unmodifiableList(out);
    }

    private RenderEntityDefinition parseEntity(String modelId, String key, ConfigurationSection section) {
        String typeRaw = section.getString("type");
        if (typeRaw == null) return null;

        RenderEntityDefinition.EntityKind kind;
        try {
            kind = RenderEntityDefinition.EntityKind.valueOf(typeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            InertiaLogger.warn("Invalid entity type: " + typeRaw);
            return null;
        }

        String itemModelKey = section.getString("item-model");

        // Material handling needs care on 1.16.5 if config uses 1.21 block names
        Material blockType = null;
        if (section.isString("block")) {
            String matName = section.getString("block");
            try {
                blockType = Material.matchMaterial(matName);
                if (blockType == null && !matName.isEmpty()) {
                    // Якщо ми на 1.16.5, а в конфігу блок з 1.21 - це не помилка, просто ми його не знайдемо
                    // Можна додати лог або ігнорувати
                }
            } catch (Exception ignored) {}
        }

        InertiaDisplayMode displayMode = InertiaDisplayMode.parse(section.getString("display-mode"));

        Vector localOffset = parseVector(section.getString("local-offset"), new Vector(0, 0, 0));
        Quaternionf localRotation = parseRotation(section.getString("local-rotation"));
        Vector scale = parseVector(section.getString("scale"), new Vector(1, 1, 1));
        Vector translation = parseVector(section.getString("translation"), new Vector(0, 0, 0));

        boolean showWhenActive = section.getBoolean("show-when.active", true);
        boolean showWhenSleeping = section.getBoolean("show-when.sleeping", true);

        TagRules tags = parseTagRules(modelId, key, section);
        if (tags.showStateMask != 0) {
            showWhenActive = (tags.showStateMask & STATE_ACTIVE) != 0;
            showWhenSleeping = (tags.showStateMask & STATE_SLEEPING) != 0;
        }

        // Parse nullable floats/ints safely
        Float viewRange = section.contains("view-range") ? (float) section.getDouble("view-range") : null;
        Float shadowRadius = section.contains("shadow-radius") ? (float) section.getDouble("shadow-radius") : null;
        Float shadowStrength = section.contains("shadow-strength") ? (float) section.getDouble("shadow-strength") : null;
        Integer interpolationDuration = section.contains("interpolation-duration") ? section.getInt("interpolation-duration") : null;
        Integer teleportDuration = section.contains("teleport-duration") ? section.getInt("teleport-duration") : null;

        InertiaBillboard billboard = InertiaBillboard.parse(section.getString("billboard"));

        Integer brightnessBlock = null, brightnessSky = null;
        if (section.contains("brightness.block")) brightnessBlock = section.getInt("brightness.block");
        if (section.contains("brightness.sky")) brightnessSky = section.getInt("brightness.sky");

        boolean rotateTranslation = section.getBoolean("rotate-translation", true);

        Map<String, Object> settings = parseSettings(section.getConfigurationSection("settings"));
        RenderEntitySettingsValidator.validate(modelId, key, kind, settings);

        return new RenderEntityDefinition(
                key, kind, itemModelKey, blockType, displayMode,
                localOffset, localRotation, scale, translation,
                showWhenActive, showWhenSleeping,
                tags.hideWhenActive, tags.hideWhenSleeping,
                tags.showLodMask, tags.hideLodMask,
                rotateTranslation, viewRange, shadowRadius, shadowStrength,
                interpolationDuration, teleportDuration, billboard, brightnessBlock, brightnessSky,
                section.getBoolean("small", false), section.getBoolean("invisible", true),
                section.getBoolean("marker", true), section.getBoolean("base-plate", false),
                section.getBoolean("arms", false),
                settings
        );
    }

    private static final int LOD_NEAR = 0x01;
    private static final int LOD_MID = 0x02;
    private static final int LOD_FAR = 0x04;
    private static final int STATE_ACTIVE = 0x01;
    private static final int STATE_SLEEPING = 0x02;

    private record TagRules(boolean hideWhenActive, boolean hideWhenSleeping, int showStateMask, int showLodMask, int hideLodMask) {
    }

    private TagRules parseTagRules(String modelId, String entityKey, ConfigurationSection section) {
        boolean hideActive = section.getBoolean("hide-when.active", false);
        boolean hideSleeping = section.getBoolean("hide-when.sleeping", false);
        int showLodMask = 0; // 0 means "all"
        int hideLodMask = 0;
        int showStateMask = 0; // 0 means "do not override show-when.*"

        // show-when.lod: [NEAR, MID, FAR]
        if (section.isList("show-when.lod")) {
            List<String> list = section.getStringList("show-when.lod");
            showLodMask |= parseLodList(modelId, entityKey, "show-when.lod", list);
        } else if (section.isString("show-when.lod")) {
            String raw = section.getString("show-when.lod");
            if (raw != null && !raw.isBlank()) {
                showLodMask |= parseLodList(modelId, entityKey, "show-when.lod", List.of(raw));
            }
        }

        // hide-when.lod: [NEAR, MID, FAR] (or LOD_NEAR, LOD_MID, LOD_FAR)
        if (section.isList("hide-when.lod")) {
            List<String> list = section.getStringList("hide-when.lod");
            hideLodMask |= parseLodList(modelId, entityKey, "hide-when.lod", list);
        } else if (section.isString("hide-when.lod")) {
            String raw = section.getString("hide-when.lod");
            if (raw != null && !raw.isBlank()) {
                hideLodMask |= parseLodList(modelId, entityKey, "hide-when.lod", List.of(raw));
            }
        }

        // hide-tags: [ACTIVE, SLEEPING, LOD_FAR]
        if (section.isList("hide-tags")) {
            List<String> tags = section.getStringList("hide-tags");
            for (String raw : tags) {
                if (raw == null) continue;
                String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
                switch (t) {
                    case "ACTIVE" -> hideActive = true;
                    case "SLEEPING" -> hideSleeping = true;
                    case "LOD_NEAR", "NEAR" -> hideLodMask |= LOD_NEAR;
                    case "LOD_MID", "MID" -> hideLodMask |= LOD_MID;
                    case "LOD_FAR", "FAR" -> hideLodMask |= LOD_FAR;
                    default -> InertiaLogger.warn("Unknown hide-tag '" + raw + "' in render model '" + modelId + "', entity '" + entityKey + "'");
                }
            }
        } else if (section.isString("hide-tags")) {
            String raw = section.getString("hide-tags");
            if (raw != null && !raw.isBlank()) {
                // allow single string tag
                String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
                switch (t) {
                    case "ACTIVE" -> hideActive = true;
                    case "SLEEPING" -> hideSleeping = true;
                    case "LOD_NEAR", "NEAR" -> hideLodMask |= LOD_NEAR;
                    case "LOD_MID", "MID" -> hideLodMask |= LOD_MID;
                    case "LOD_FAR", "FAR" -> hideLodMask |= LOD_FAR;
                    default -> InertiaLogger.warn("Unknown hide-tag '" + raw + "' in render model '" + modelId + "', entity '" + entityKey + "'");
                }
            }
        }

        // show-tags: [ACTIVE, SLEEPING, LOD_NEAR] (whitelist)
        if (section.isList("show-tags")) {
            List<String> tags = section.getStringList("show-tags");
            for (String raw : tags) {
                if (raw == null) continue;
                String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
                switch (t) {
                    case "ACTIVE" -> showStateMask |= STATE_ACTIVE;
                    case "SLEEPING" -> showStateMask |= STATE_SLEEPING;
                    case "LOD_NEAR", "NEAR" -> showLodMask |= LOD_NEAR;
                    case "LOD_MID", "MID" -> showLodMask |= LOD_MID;
                    case "LOD_FAR", "FAR" -> showLodMask |= LOD_FAR;
                    default -> InertiaLogger.warn("Unknown show-tag '" + raw + "' in render model '" + modelId + "', entity '" + entityKey + "'");
                }
            }
        } else if (section.isString("show-tags")) {
            String raw = section.getString("show-tags");
            if (raw != null && !raw.isBlank()) {
                String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
                switch (t) {
                    case "ACTIVE" -> showStateMask |= STATE_ACTIVE;
                    case "SLEEPING" -> showStateMask |= STATE_SLEEPING;
                    case "LOD_NEAR", "NEAR" -> showLodMask |= LOD_NEAR;
                    case "LOD_MID", "MID" -> showLodMask |= LOD_MID;
                    case "LOD_FAR", "FAR" -> showLodMask |= LOD_FAR;
                    default -> InertiaLogger.warn("Unknown show-tag '" + raw + "' in render model '" + modelId + "', entity '" + entityKey + "'");
                }
            }
        }

        if (showStateMask != 0) {
            boolean hasShowWhen = section.contains("show-when.active") || section.contains("show-when.sleeping");
            if (hasShowWhen) {
                InertiaLogger.warn("Both 'show-tags' (ACTIVE/SLEEPING) and 'show-when.active/sleeping' are set in render model '" + modelId
                        + "', entity '" + entityKey + "'. 'show-tags' overrides.");
            }
        }

        showLodMask &= 0x07;
        hideLodMask &= 0x07;

        if (showLodMask != 0 && (showLodMask & ~hideLodMask) == 0) {
            InertiaLogger.warn("Render model '" + modelId + "', entity '" + entityKey + "': show-lod-mask is fully hidden by hide-lod-mask");
        }

        return new TagRules(hideActive, hideSleeping, showStateMask, showLodMask, hideLodMask);
    }

    private int parseLodList(String modelId, String entityKey, String key, List<String> list) {
        if (list == null || list.isEmpty()) return 0;
        int mask = 0;
        for (String raw : list) {
            if (raw == null) continue;
            String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            switch (t) {
                case "LOD_NEAR", "NEAR" -> mask |= LOD_NEAR;
                case "LOD_MID", "MID" -> mask |= LOD_MID;
                case "LOD_FAR", "FAR" -> mask |= LOD_FAR;
                default -> InertiaLogger.warn("Unknown lod value '" + raw + "' in '" + key + "' for render model '" + modelId + "', entity '" + entityKey + "'");
            }
        }
        return mask;
    }

    private Map<String, Object> parseSettings(ConfigurationSection section) {
        if (section == null) return Collections.emptyMap();
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            out.put(key, deepConvert(value));
        }
        return out;
    }

    private Object deepConvert(Object value) {
        if (value == null) return null;
        if (value instanceof ConfigurationSection cs) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (String k : cs.getKeys(false)) {
                nested.put(k, deepConvert(cs.get(k)));
            }
            return nested;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object k = e.getKey();
                if (k == null) continue;
                nested.put(String.valueOf(k), deepConvert(e.getValue()));
            }
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object v : list) copy.add(deepConvert(v));
            return copy;
        }
        return value;
    }

    private Vector parseVector(String raw, Vector def) {
        if (raw == null || raw.isBlank()) return def;
        try {
            String[] parts = raw.replace(',', ' ').trim().split("\\s+");
            if (parts.length == 3) {
                return new Vector(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            }
        } catch (NumberFormatException ignored) {}
        return def;
    }

    private Quaternionf parseRotation(String raw) {
        if (raw == null || raw.isBlank()) return new Quaternionf();
        Vector v = parseVector(raw, new Vector(0, 0, 0));
        return new Quaternionf().rotationXYZ((float) v.getX(), (float) v.getY(), (float) v.getZ());
    }

    // Getters...
    public Optional<RenderModelSelector> findSelector(String id) { return Optional.ofNullable(selectors.get(id)); }

    /**
     * Backwards-compatible API: returns the server-selected model (based on server protocol).
     * Use {@link #findSelector(String)} to access all per-client variants.
     */
    public Optional<RenderModelDefinition> find(String id) {
        RenderModelSelector selector = selectors.get(id);
        if (selector == null) return Optional.empty();
        MinecraftVersions.Version current = MinecraftVersions.CURRENT;
        int protocol = current != null ? current.networkProtocol : Integer.MAX_VALUE;
        return Optional.ofNullable(selector.selectModelByProtocol(protocol));
    }

    public RenderModelDefinition require(String id) {
        RenderModelDefinition v = find(id).orElse(null);
        if (v == null) throw new IllegalArgumentException("Unknown model: " + id);
        return v;
    }
}
