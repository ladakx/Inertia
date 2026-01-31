package com.ladakx.inertia.physics.factory.shape;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.common.serializers.TransformSerializer.JoltTransform;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.mesh.MeshProvider;
import com.ladakx.inertia.common.serializers.TransformSerializer;
import com.ladakx.inertia.common.utils.ConfigUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ladakx.inertia.common.utils.ConfigUtils.parseFloat;

/**
 * Фабрика, яка з текстових описів у конфігу створює Jolt CollisionShape.
 */
public final class JShapeFactory {

    private final MeshProvider meshProvider;
    private static final Pattern PARAM_PATTERN = Pattern.compile("([a-zA-Z0-9_]+)=([^\\s=]+)");

    public JShapeFactory(MeshProvider meshProvider) {
        this.meshProvider = meshProvider;
    }

    public Optional<MeshProvider> getMeshProvider() {
        return Optional.ofNullable(meshProvider);
    }

    private MeshProvider requireMeshProvider() {
        MeshProvider provider = meshProvider;
        if (provider == null) {
            throw new IllegalStateException(
                    "MeshProvider is not set. Required for type=convex_hull."
            );
        }
        return provider;
    }

    public ShapeRefC createShape(List<String> shapeLines) {
        if (shapeLines == null || shapeLines.isEmpty()) {
            throw new IllegalArgumentException("Shape definition list cannot be empty/null");
        }

        List<ParsedShape> parsed = new ArrayList<>();
        for (String raw : shapeLines) {
            if (raw == null || raw.isBlank()) continue;

            String line = raw.trim();
            if (line.startsWith("[") && line.endsWith("]")) {
                line = line.substring(1, line.length() - 1).trim();
            }

            try {
                parsed.add(parseLine(line));
            } catch (Exception e) {
                InertiaLogger.error("Invalid shape definition: '" + line + "'", e);
            }
        }

        if (parsed.isEmpty()) {
            InertiaLogger.warn("No valid shapes parsed. Using default Box(0.5).");
            return new BoxShape(new Vec3(0.5f, 0.5f, 0.5f)).toRefC();
        }

        // ---------- 1 шейп ----------
        if (parsed.size() == 1) {
            ParsedShape only = parsed.get(0);
            ConstShape decorated = only.shape();

            if (only.hasCenterOfMassOffset()) {
                OffsetCenterOfMassShapeSettings ocomSettings = new OffsetCenterOfMassShapeSettings(only.centerOfMassOffset(), decorated);
                ShapeResult res = ocomSettings.create();
                if (res.hasError()) InertiaLogger.error("Jolt Error (Offset): " + res.getError());
                else decorated = res.get();
            }

            if (only.hasPosition() || only.hasRotation()) {
                RotatedTranslatedShapeSettings rtSettings = new RotatedTranslatedShapeSettings(only.position(), only.rotation(), decorated);
                ShapeResult res = rtSettings.create();
                if (res.hasError()) InertiaLogger.error("Jolt Error (RotTrans): " + res.getError());
                else decorated = res.get();
            }

            return decorated.toRefC();
        }

        // ---------- Compound ----------
        StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();
        for (ParsedShape p : parsed) {
            ConstShape child = p.shape();
            if (p.hasCenterOfMassOffset()) {
                OffsetCenterOfMassShapeSettings ocomSettings =
                        new OffsetCenterOfMassShapeSettings(p.centerOfMassOffset(), child);
                ShapeResult res = ocomSettings.create();
                if (!res.hasError()) child = res.get();
            }
            settings.addShape(p.position(), p.rotation(), child);
        }

        ShapeResult result = settings.create();
        if (result.hasError()) {
            throw new IllegalStateException("Jolt Compound Error: " + result.getError());
        }

        return result.get();
    }

    // ---------- Парсер однієї строки ----------

    private ParsedShape parseLine(String line) {
        Map<String, String> kv = parseKeyValues(line);

        String type = kv.get("type");
        if (type == null) {
            throw new IllegalArgumentException("Missing required parameter 'type'");
        }

        JoltTransform transform = TransformSerializer.fromKeyValueMap(kv);
        ComOffset com = parseCenterOfMassOffset(kv);
        ConstShape shape = createRawShape(type.toLowerCase(Locale.ROOT), kv);

        return new ParsedShape(
                shape,
                transform.position(),
                transform.rotation(),
                transform.hasPosition(),
                transform.hasRotation(),
                com.offset(),
                com.hasOffset()
        );
    }

    private ConstShape createRawShape(String type, Map<String, String> kv) {
        switch (type) {
            case "box":
                return parseBox(kv);
            case "sphere":
                return parseSphere(kv);
            case "capsule":
                return parseCapsule(kv);
            case "cylinder":
                return parseCylinder(kv);
            case "tapered_capsule":
                return parseTaperedCapsule(kv);
            case "tapered_cylinder":
                return parseTaperedCylinder(kv);
            case "convex_hull":
                String meshId = kv.get("mesh");
                if (meshId == null) throw new IllegalArgumentException("ConvexHull requires 'mesh' parameter");

                Collection<Vec3> points = meshProvider.loadConvexHullPoints(meshId);

                if (points == null || points.isEmpty()) {
                    throw new IllegalStateException("Mesh '" + meshId + "' is empty or not loaded");
                }

                float cRad = getFloat(kv, "convexradius", -1f);
                ConvexHullShapeSettings settings = (cRad > 0)
                        ? new ConvexHullShapeSettings(points, cRad)
                        : new ConvexHullShapeSettings(points);

                return validate(settings.create(), "ConvexHull");

            default:
                throw new IllegalArgumentException("Unknown shape type: " + type);
        }
    }

    // ---------- Парсер параметрів ----------
    private ConstShape parseBox(Map<String, String> kv) {
        float x = getFloat(kv, "x", 0.5f);
        float y = getFloat(kv, "y", 0.5f);
        float z = getFloat(kv, "z", 0.5f);
        Vec3 halfExtents = new Vec3(x, y, z);
        float convexRadius = getFloat(kv, "convexradius", -1f);
        return (convexRadius > 0) ? new BoxShape(halfExtents, convexRadius) : new BoxShape(halfExtents);
    }

    private ConstShape parseSphere(Map<String, String> kv) {
        return new SphereShape(getFloat(kv, "radius", 0.5f));
    }

    private ConstShape parseCapsule(Map<String, String> kv) {
        return new CapsuleShape(getFloat(kv, "height", 2.0f) * 0.5f, getFloat(kv, "radius", 0.5f));
    }

    private ConstShape parseCylinder(Map<String, String> kv) {
        float radius = getFloat(kv, "radius", 0.5f);
        float height = getFloat(kv, "height", 2.0f);
        float convexRadius = getFloat(kv, "convexradius", -1f);

        if (convexRadius > 0) {
            return new CylinderShape(height * 0.5f, radius, convexRadius);
        }
        return new CylinderShape(height * 0.5f, radius);
    }

    private ConstShape parseTaperedCapsule(Map<String, String> kv) {
        float height = getFloat(kv, "height", 2.0f);
        float topRad = getFloat(kv, "topradius", 0.4f);
        float botRad = getFloat(kv, "bottomradius", 0.6f);

        TaperedCapsuleShapeSettings settings = new TaperedCapsuleShapeSettings(height * 0.5f, topRad, botRad);
        return validate(settings.create(), "TaperedCapsule");
    }

    private ConstShape parseTaperedCylinder(Map<String, String> kv) {
        float height = getFloat(kv, "height", 2.0f);
        float topRad = getFloat(kv, "topradius", 0.4f);
        float botRad = getFloat(kv, "bottomradius", 0.6f);
        float cRad   = getFloat(kv, "convexradius", -1f);

        TaperedCylinderShapeSettings settings;
        if (cRad > 0) {
            settings = new TaperedCylinderShapeSettings(height * 0.5f, topRad, botRad, cRad);
        } else {
            settings = new TaperedCylinderShapeSettings(height * 0.5f, topRad, botRad);
        }
        return validate(settings.create(), "TaperedCylinder");
    }

    private ConstShape parseConvexHull(Map<String, String> kv, String originalLine) {
        // Тут ми все ще вимагаємо mesh, бо без нього неможливо створити Hull
        String meshId = require(kv, "mesh", originalLine);
        MeshProvider provider = requireMeshProvider();
        Collection<Vec3> points = provider.loadConvexHullPoints(meshId);

        if (points == null || points.isEmpty()) {
            throw new IllegalStateException("MeshProvider returned no points for mesh='" + meshId + "'");
        }

        float cRad = getFloat(kv, "convexradius", -1f);
        ConvexHullShapeSettings settings = (cRad > 0)
                ? new ConvexHullShapeSettings(points, cRad)
                : new ConvexHullShapeSettings(points);

        return validate(settings.create(), "ConvexHull");
    }

    // ---------- Helpers ----------

    private ConstShape validate(ShapeResult result, String name) {
        if (result.hasError()) {
            throw new IllegalStateException("Failed to create " + name + ": " + result.getError());
        }
        return result.get();
    }

    private ComOffset parseCenterOfMassOffset(Map<String, String> kv) {
        String comStr = kv.get("com");
        if (comStr != null && !comStr.isBlank()) {
            String[] parts = comStr.replace(',', ' ').trim().split("\\s+");
            if (parts.length == 3) {
                return new ComOffset(new Vec3(parseFloat(parts[0]), parseFloat(parts[1]), parseFloat(parts[2])), true);
            }
        }
        float x = getFloat(kv, "comx", 0f);
        float y = getFloat(kv, "comy", 0f);
        float z = getFloat(kv, "comz", 0f);

        // Вважаємо, що оффсет є, якщо хоча б одна компонента != 0 або явно задана (для спрощення перевіряємо наявність ключів у майбутньому, але тут просто повертаємо нуль)
        boolean hasAny = kv.containsKey("comx") || kv.containsKey("comy") || kv.containsKey("comz");
        return new ComOffset(new Vec3(x, y, z), hasAny);
    }

    /**
     * Обов'язковий параметр. Кидає помилку, якщо немає.
     */
    private String require(Map<String, String> kv, String key, String context) {
        String val = kv.get(key);
        if (val == null) {
            throw new IllegalArgumentException("Missing required '" + key + "' in: " + context);
        }
        return val;
    }

    /**
     * Отримати float із дефолтним значенням та логом, якщо ключ відсутній (опціонально).
     */
    private float getFloat(Map<String, String> kv, String key, float def) {
        String val = kv.get(key);
        if (val == null) return def;
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            InertiaLogger.warn("Invalid float for '" + key + "': " + val + ". Using default: " + def);
            return def;
        }
    }

    private Map<String, String> parseKeyValues(String line) {
        Map<String, String> map = new HashMap<>();
        Matcher matcher = PARAM_PATTERN.matcher(line);
        while (matcher.find()) {
            map.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2));
        }
        return map;
    }

    private float requireFloat(Map<String, String> kv, String key) {
        String val = kv.get(key);
        if (val == null) throw new IllegalArgumentException("Missing required parameter '" + key + "'");
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter '" + key + "' must be a number, got: " + val);
        }
    }

    private record ParsedShape(
            ConstShape shape,
            Vec3 position,
            Quat rotation,
            boolean hasPosition,
            boolean hasRotation,
            Vec3 centerOfMassOffset,
            boolean hasCenterOfMassOffset
    ) {}

    private record ComOffset(Vec3 offset, boolean hasOffset) {}
}