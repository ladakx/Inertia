package com.ladakx.inertia.jolt.shape;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.utils.serializers.TransformSerializer.JoltTransform;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.utils.mesh.MeshProvider;
import com.ladakx.inertia.utils.serializers.TransformSerializer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Фабрика, яка з текстових описів у конфізі створює Jolt CollisionShape.
 */
public final class JShapeFactory {

    private static volatile MeshProvider meshProvider;
    // Паттерн для поиска пар key=value.
    // Поддерживает: key=value
    private static final Pattern PARAM_PATTERN = Pattern.compile("([a-zA-Z0-9_]+)=([^\\s=]+)");

    private JShapeFactory() {
    }

    public static void setMeshProvider(MeshProvider provider) {
        meshProvider = provider;
    }

    public static Optional<MeshProvider> getMeshProvider() {
        return Optional.ofNullable(meshProvider);
    }

    private static MeshProvider requireMeshProvider() {
        MeshProvider provider = meshProvider;
        if (provider == null) {
            throw new IllegalStateException(
                    "MeshProvider is not set. Required for type=convex_hull."
            );
        }
        return provider;
    }

    public static ShapeRefC createShape(List<String> shapeLines) {
        if (shapeLines == null || shapeLines.isEmpty()) {
            throw new IllegalArgumentException("Shape definition list cannot be empty/null");
        }

        List<ParsedShape> parsed = new ArrayList<>();
        for (String raw : shapeLines) {
            if (raw == null || raw.isBlank()) continue;

            // Очистка от скобок списка, если YAML вернул строковое представление списка
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
            // Возвращаем fallback-форму, чтобы не крашить сервер, но ошибка уже в логе
            InertiaLogger.warn("No valid shapes parsed. Using default Box(0.5).");
            return new BoxShape(new Vec3(0.5f, 0.5f, 0.5f)).toRefC();
        }

        // ---------- 1 шейп ----------
        if (parsed.size() == 1) {
            ParsedShape only = parsed.get(0);
            ConstShape decorated = only.shape();

            // 1) Зсув центру мас
            if (only.hasCenterOfMassOffset()) {
                OffsetCenterOfMassShapeSettings ocomSettings = new OffsetCenterOfMassShapeSettings(only.centerOfMassOffset(), decorated);
                ShapeResult res = ocomSettings.create();
                if (res.hasError()) InertiaLogger.error("Jolt Error (Offset): " + res.getError());
                else decorated = res.get();
            }

            // 2) Локальний transform
            if (only.hasPosition() || only.hasRotation()) {
                RotatedTranslatedShapeSettings rtSettings = new RotatedTranslatedShapeSettings(only.position(), only.rotation(), decorated);
                ShapeResult res = rtSettings.create();
                if (res.hasError()) InertiaLogger.error("Jolt Error (RotTrans): " + res.getError());
                else decorated = res.get();
            }

            return decorated.toRefC();
        }

        // ---------- Кілька шейпів -> StaticCompoundShape ----------
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

    private static ParsedShape parseLine(String line) {
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

    private static ConstShape createRawShape(String type, Map<String, String> kv) {
        // Strict parameter validation
        switch (type) {
            case "box":
                return new BoxShape(new Vec3(
                        getFloat(kv, "x", 0.5f),
                        getFloat(kv, "y", 0.5f),
                        getFloat(kv, "z", 0.5f)
                ));
            case "sphere":
                return new SphereShape(requireFloat(kv, "radius"));
            case "capsule":
                return new CapsuleShape(
                        getFloat(kv, "height", 2.0f) * 0.5f,
                        requireFloat(kv, "radius")
                );
            case "cylinder":
                return new CylinderShape(
                        getFloat(kv, "height", 2.0f) * 0.5f,
                        requireFloat(kv, "radius")
                );
            case "convex_hull":
                String meshId = kv.get("mesh");
                if (meshId == null) throw new IllegalArgumentException("ConvexHull requires 'mesh' parameter");

                if (meshProvider == null) throw new IllegalStateException("MeshProvider not initialized");
                Collection<Vec3> points = meshProvider.loadConvexHullPoints(meshId);

                if (points.isEmpty()) throw new IllegalStateException("Mesh '" + meshId + "' is empty");

                ConvexHullShapeSettings settings = new ConvexHullShapeSettings(points);
                ShapeResult result = settings.create();
                if (result.hasError()) throw new IllegalStateException(result.getError());
                return result.get();

            default:
                throw new IllegalArgumentException("Unknown shape type: " + type);
        }
    }

    // ---------- Парсер параметрів ----------

    private static ConstShape parseBox(Map<String, String> kv) {
        // Дефолтні розміри 0.5, якщо не вказано
        float x = getFloat(kv, "x", 0.5f);
        float y = getFloat(kv, "y", 0.5f);
        float z = getFloat(kv, "z", 0.5f);

        Vec3 halfExtents = new Vec3(x, y, z);
        float convexRadius = getFloat(kv, "convexradius", -1f); // -1 if not set

        return (convexRadius > 0) ? new BoxShape(halfExtents, convexRadius) : new BoxShape(halfExtents);
    }

    private static ConstShape parseSphere(Map<String, String> kv) {
        float radius = getFloat(kv, "radius", 0.5f);
        return new SphereShape(radius);
    }

    private static ConstShape parseCapsule(Map<String, String> kv) {
        float radius = getFloat(kv, "radius", 0.5f);
        float height = getFloat(kv, "height", 2.0f);
        return new CapsuleShape(height * 0.5f, radius);
    }

    private static ConstShape parseCylinder(Map<String, String> kv) {
        float radius = getFloat(kv, "radius", 0.5f);
        float height = getFloat(kv, "height", 2.0f);
        float convexRadius = getFloat(kv, "convexradius", -1f);

        if (convexRadius > 0) {
            return new CylinderShape(height * 0.5f, radius, convexRadius);
        }
        return new CylinderShape(height * 0.5f, radius);
    }

    private static ConstShape parseTaperedCapsule(Map<String, String> kv) {
        float height = getFloat(kv, "height", 2.0f);
        float topRad = getFloat(kv, "topradius", 0.4f);
        float botRad = getFloat(kv, "bottomradius", 0.6f);

        TaperedCapsuleShapeSettings settings = new TaperedCapsuleShapeSettings(height * 0.5f, topRad, botRad);
        return validate(settings.create(), "TaperedCapsule");
    }

    private static ConstShape parseTaperedCylinder(Map<String, String> kv) {
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

    private static ConstShape parseConvexHull(Map<String, String> kv, String originalLine) {
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

    private static ConstShape validate(ShapeResult result, String name) {
        if (result.hasError()) {
            throw new IllegalStateException("Failed to create " + name + ": " + result.getError());
        }
        return result.get();
    }

    private static ComOffset parseCenterOfMassOffset(Map<String, String> kv) {
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
    private static String require(Map<String, String> kv, String key, String context) {
        String val = kv.get(key);
        if (val == null) {
            throw new IllegalArgumentException("Missing required '" + key + "' in: " + context);
        }
        return val;
    }

    /**
     * Отримати float із дефолтним значенням та логом, якщо ключ відсутній (опціонально).
     */
    private static float getFloat(Map<String, String> kv, String key, float def) {
        String val = kv.get(key);
        if (val == null) return def;
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            InertiaLogger.warn("Invalid float for '" + key + "': " + val + ". Using default: " + def);
            return def;
        }
    }

    private static Map<String, String> parseKeyValues(String line) {
        Map<String, String> map = new HashMap<>();
        Matcher matcher = PARAM_PATTERN.matcher(line);
        while (matcher.find()) {
            map.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2));
        }
        return map;
    }

    private static float requireFloat(Map<String, String> kv, String key) {
        String val = kv.get(key);
        if (val == null) throw new IllegalArgumentException("Missing required parameter '" + key + "'");
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter '" + key + "' must be a number, got: " + val);
        }
    }

    private static float parseFloat(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return 0.0f;
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