package com.ladakx.inertia.jolt.shape;

import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.CapsuleShape;
import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.CylinderShape;
import com.github.stephengold.joltjni.OffsetCenterOfMassShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RotatedTranslatedShapeSettings;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.SphereShape;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.TaperedCapsuleShapeSettings;
import com.github.stephengold.joltjni.TaperedCylinderShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.utils.mesh.MeshProvider;
import com.ladakx.inertia.utils.serializers.TransformSerializer;
import com.ladakx.inertia.utils.serializers.TransformSerializer.JoltTransform;

import java.util.*;

/**
 * Фабрика, яка з текстових описів у конфізі створює Jolt CollisionShape.
 *
 * Вхід:
 *   shape:
 *     - "type=box x=0.5 y=0.5 z=0.5"
 *     - "type=sphere radius=0.25"
 *     - "type=capsule radius=0.2 height=1.0"
 *     - "type=cylinder radius=0.2 height=1.0"
 *     - "type=tapered_capsule height=2.0 topRadius=0.3 bottomRadius=0.5"
 *     - "type=tapered_cylinder height=2.0 topRadius=0.4 bottomRadius=0.6"
 *     - "type=convex_hull mesh=models/vehicle_body.obj convexRadius=0.05"
 *     - ...
 *
 * Якщо одна лінія:
 *   - створюється один шейп;
 *   - якщо є px/py/pz або rot/pitch/yaw/roll — шейп загортається
 *     в RotatedTranslatedShape;
 *   - якщо є com / comx / comy / comz — шейп додатково загортається
 *     в OffsetCenterOfMassShape.
 *
 * Якщо кілька ліній:
 *   - створюється StaticCompoundShape;
 *   - кожен підшейп має свій локальний position + rotation (через addShape);

 */
public final class JShapeFactory {

    /**
     * Глобальний провайдер мешів для type=convex_hull.
     * Налаштовується ззовні, наприклад у onEnable плагіна.
     */
    private static volatile MeshProvider meshProvider;

    private JShapeFactory() {
    }

    /**
     * Зареєструвати MeshProvider для завантаження мешів (BlockBench, OBJ тощо).
     */
    public static void setMeshProvider(MeshProvider provider) {
        meshProvider = provider;
    }

    /**
     * Опціонально дістати поточний MeshProvider.
     */
    public static Optional<MeshProvider> getMeshProvider() {
        return Optional.ofNullable(meshProvider);
    }

    /**
     * Внутрішньо: отримати MeshProvider або впасти з помилкою, якщо його не налаштовано.
     */
    private static MeshProvider requireMeshProvider() {
        MeshProvider provider = meshProvider;
        if (provider == null) {
            throw new IllegalStateException(
                    "MeshProvider is not set. " +
                            "It is required for type=convex_hull shapes. " +
                            "Call ShapeConfigFactory.setMeshProvider(...) on startup."
            );
        }
        return provider;
    }

    /**
     * Головний метод: створити шейп(и) з конфігу.
     *
     * @param shapeLines список рядків із конфігурації (getStringList("shape"))
     * @return ShapeRefC (можна використовувати всюди, де очікується ConstShape)
     */
    public static ShapeRefC createShapeFromConfig(List<String> shapeLines) {
        if (shapeLines == null || shapeLines.isEmpty()) {
            throw new IllegalArgumentException("shape list is empty");
        }

        List<ParsedShape> parsed = new ArrayList<>();
        for (String raw : shapeLines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            parsed.add(parseLine(line));
        }

        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("shape list contains only empty/blank lines");
        }

        // ---------- 1 шейп ----------
        if (parsed.size() == 1) {
            ParsedShape only = parsed.get(0);

            ConstShape decorated = only.shape();

            // 1) Зсув центру мас (OffsetCenterOfMassShape)
            if (only.hasCenterOfMassOffset()) {
                OffsetCenterOfMassShapeSettings ocomSettings =
                        new OffsetCenterOfMassShapeSettings(
                                only.centerOfMassOffset(),
                                decorated
                        );
                ShapeResult ocomResult = ocomSettings.create();
                if (ocomResult.hasError()) {
                    throw new IllegalStateException(
                            "Failed to create offset-center-of-mass shape: " + ocomResult.getError()
                    );
                }
                decorated = ocomResult.get();
            }

            // 2) Локальний transform (RotatedTranslatedShape)
            if (only.hasPosition() || only.hasRotation()) {
                RotatedTranslatedShapeSettings rtSettings =
                        new RotatedTranslatedShapeSettings(
                                only.position(),
                                only.rotation(),
                                decorated
                        );

                ShapeResult rtResult = rtSettings.create();
                if (rtResult.hasError()) {
                    throw new IllegalStateException(
                            "Failed to create rotated-translated shape: " + rtResult.getError()
                    );
                }

                decorated = rtResult.get();
            }

            // 3) Повертаємо фінальний шейп як ShapeRefC
            return decorated.toRefC();
        }

        // ---------- Кілька шейпів -> StaticCompoundShape ----------
        StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();

        for (ParsedShape p : parsed) {
            ConstShape child = p.shape();

            // За потреби — декоратор OffsetCenterOfMass для кожного підшейпа
            if (p.hasCenterOfMassOffset()) {
                OffsetCenterOfMassShapeSettings ocomSettings =
                        new OffsetCenterOfMassShapeSettings(
                                p.centerOfMassOffset(),
                                child
                        );
                ShapeResult ocomResult = ocomSettings.create();
                if (ocomResult.hasError()) {
                    throw new IllegalStateException(
                            "Failed to create offset-center-of-mass shape for child: " + ocomResult.getError()
                    );
                }
                child = ocomResult.get();
            }

            settings.addShape(
                    p.position(),   // Vec3
                    p.rotation(),   // Quat
                    child           // ConstShape
            );
        }

        ShapeResult result = settings.create();
        if (result.hasError()) {
            throw new IllegalStateException(
                    "Failed to create compound shape: " + result.getError()
            );
        }

        return result.get();
    }

    // ---------- Парсер однієї строки ----------

    private static ParsedShape parseLine(String line) {
        Map<String, String> kv = parseKeyValues(line);

        String rawType = require(kv, "type", line);
        String type = rawType.toLowerCase(Locale.ROOT);

        // Позиція + ротація (через спільний утил)
        JoltTransform transform = TransformSerializer.fromKeyValueMap(kv);
        // Зсув центру мас
        ComOffset com = parseCenterOfMassOffset(kv);

        ConstShape shape = switch (type) {
            case "box"              -> parseBox(kv, line);
            case "sphere"           -> parseSphere(kv, line);
            case "capsule"          -> parseCapsule(kv, line);
            case "cylinder"         -> parseCylinder(kv, line);
            case "tapered_capsule"  -> parseTaperedCapsule(kv, line);
            case "tapered_cylinder" -> parseTaperedCylinder(kv, line);
            case "convex_hull"      -> parseConvexHull(kv, line);
            default -> throw new IllegalArgumentException(
                    "Unknown shape type '" + rawType + "' in line: " + line
            );
        };

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

    // ---------- Парсер зсуву центру мас (OffsetCenterOfMassShape) ----------

    /**
     * Формати:
     *   - com="x y z"
     *   - com="x,y,z"
     *   - comx=... comy=... comz=...
     *
     * Усі значення - float, у локальній системі координат шейпа.
     */
    private static ComOffset parseCenterOfMassOffset(Map<String, String> kv) {
        String comStr = kv.get("com");
        String comxStr = kv.get("comx");
        String comyStr = kv.get("comy");
        String comzStr = kv.get("comz");

        boolean hasVector = comStr != null && !comStr.isBlank();
        boolean hasComponents =
                (comxStr != null) || (comyStr != null) || (comzStr != null);

        if (!hasVector && !hasComponents) {
            return new ComOffset(new Vec3(0f, 0f, 0f), false);
        }

        if (hasVector) {
            // com="x y z" або "x,y,z"
            String cleaned = comStr.replace(',', ' ');
            String[] parts = cleaned.trim().split("\\s+");
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                        "Invalid 'com' value, expected 3 components, got: " + comStr
                );
            }
            float x = parseFloat(parts[0]);
            float y = parseFloat(parts[1]);
            float z = parseFloat(parts[2]);
            return new ComOffset(new Vec3(x, y, z), true);
        } else {
            float x = parseFloat(comxStr != null ? comxStr : "0");
            float y = parseFloat(comyStr != null ? comyStr : "0");
            float z = parseFloat(comzStr != null ? comzStr : "0");
            return new ComOffset(new Vec3(x, y, z), true);
        }
    }

    // ---------- Парсер конкретних шейпів ----------

    private static ConstShape parseBox(Map<String, String> kv, String line) {
        float x = parseFloat(require(kv, "x", line));
        float y = parseFloat(require(kv, "y", line));
        float z = parseFloat(require(kv, "z", line));

        Vec3 halfExtents = new Vec3(x, y, z);

        String convexRadiusStr = kv.get("convexradius");
        if (convexRadiusStr != null) {
            float convexRadius = parseFloat(convexRadiusStr);
            return new BoxShape(halfExtents, convexRadius);
        } else {
            return new BoxShape(halfExtents);
        }
    }

    private static ConstShape parseSphere(Map<String, String> kv, String line) {
        float radius = parseFloat(require(kv, "radius", line));
        return new SphereShape(radius);
    }

    private static ConstShape parseCapsule(Map<String, String> kv, String line) {
        float radius = parseFloat(require(kv, "radius", line));
        float height = parseFloat(require(kv, "height", line));

        // В конфигах height – це повна висота "циліндричної" частини.
        float halfHeight = height * 0.5f;

        return new CapsuleShape(halfHeight, radius);
    }

    private static ConstShape parseCylinder(Map<String, String> kv, String line) {
        float radius = parseFloat(require(kv, "radius", line));
        float height = parseFloat(require(kv, "height", line));
        float halfHeight = height * 0.5f;

        String convexRadiusStr = kv.get("convexradius");
        if (convexRadiusStr != null) {
            float convexRadius = parseFloat(convexRadiusStr);
            return new CylinderShape(halfHeight, radius, convexRadius);
        } else {
            return new CylinderShape(halfHeight, radius);
        }
    }

    /**
     * TaperedCapsuleShape:
     *   height      -> повна висота (ми ділимо на 2 для halfHeight),
     *   topRadius   -> радіус верхньої "сфери",
     *   bottomRadius-> радіус нижньої.
     *
     * У Jolt-JNI це робиться через TaperedCapsuleShapeSettings(halfHeight, topRadius, bottomRadius)
     * і далі settings.create() [2].
     */
    private static ConstShape parseTaperedCapsule(Map<String, String> kv, String line) {
        float height       = parseFloat(require(kv, "height", line));
        float halfHeight   = height * 0.5f;
        float topRadius    = parseFloat(require(kv, "topradius", line));
        float bottomRadius = parseFloat(require(kv, "bottomradius", line));

        TaperedCapsuleShapeSettings settings =
                new TaperedCapsuleShapeSettings(halfHeight, topRadius, bottomRadius);

        ShapeResult result = settings.create();
        if (result.hasError()) {
            throw new IllegalStateException(
                    "Failed to create TaperedCapsuleShape: " + result.getError()
            );
        }
        return result.get();
    }

    /**
     * TaperedCylinderShape:
     *   height      -> повна висота (halfHeight = height/2),
     *   topRadius   -> радіус зверху,
     *   bottomRadius-> радіус знизу,
     *   convexRadius (опційно) -> товщина "margin"/convex-radius [2].
     */
    private static ConstShape parseTaperedCylinder(Map<String, String> kv, String line) {
        float height       = parseFloat(require(kv, "height", line));
        float halfHeight   = height * 0.5f;
        float topRadius    = parseFloat(require(kv, "topradius", line));
        float bottomRadius = parseFloat(require(kv, "bottomradius", line));

        String convexRadiusStr = kv.get("convexradius");
        TaperedCylinderShapeSettings settings;
        if (convexRadiusStr != null) {
            float convexRadius = parseFloat(convexRadiusStr);
            settings = new TaperedCylinderShapeSettings(
                    halfHeight, topRadius, bottomRadius, convexRadius
            );
        } else {
            settings = new TaperedCylinderShapeSettings(
                    halfHeight, topRadius, bottomRadius
            );
        }

        ShapeResult result = settings.create();
        if (result.hasError()) {
            throw new IllegalStateException(
                    "Failed to create TaperedCylinderShape: " + result.getError()
            );
        }
        return result.get();
    }

    /**
     * ConvexHullShape:
     *   - очікує mesh=<id>, який MeshProvider потім конвертує в список вершин;
     *   - convexRadius (опційно) -> maxConvexRadius для ConvexHullShapeSettings [2].
     *
     * Це дозволяє, наприклад, експортувати модель із BlockBench у OBJ/JSON
     * і в MeshProvider перетворити її в Collection<Vec3>, яку ми тут просто
     * підсунемо ConvexHullShapeSettings(points).create().
     */
    private static ConstShape parseConvexHull(Map<String, String> kv, String line) {
        String meshId = require(kv, "mesh", line);

        MeshProvider provider = requireMeshProvider();
        Collection<Vec3> points = provider.loadConvexHullPoints(meshId);

        if (points == null || points.isEmpty()) {
            throw new IllegalStateException(
                    "MeshProvider returned no points for mesh='" + meshId + "'"
            );
        }

        String convexRadiusStr = kv.get("convexradius");

        ConvexHullShapeSettings settings;
        if (convexRadiusStr != null) {
            float maxConvexRadius = parseFloat(convexRadiusStr);
            settings = new ConvexHullShapeSettings(points, maxConvexRadius);
        } else {
            // Використає Jolt.cDefaultConvexRadius за замовчуванням [2]
            settings = new ConvexHullShapeSettings(points);
        }

        ShapeResult result = settings.create();
        if (result.hasError()) {
            throw new IllegalStateException(
                    "Failed to create ConvexHullShape: " + result.getError()
            );
        }
        return result.get();
    }

    // ---------- Утиліти парсингу ----------

    /**
     * Розібрати рядок виду "key=value key2=123" у Map.
     */
    private static Map<String, String> parseKeyValues(String line) {
        Map<String, String> result = new HashMap<>();

        for (String token : line.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            String[] parts = token.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            String key = parts[0].trim().toLowerCase(Locale.ROOT);
            String value = parts[1].trim();

            if (!key.isEmpty() && !value.isEmpty()) {
                result.put(key, value);
            }
        }

        return result;
    }

    private static String require(Map<String, String> kv, String key, String line) {
        String value = kv.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing '" + key + "' in shape definition: " + line
            );
        }
        return value;
    }

    private static float parseFloat(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid float value '" + text + "' in shape definition",
                    e
            );
        }
    }

    // ---------- DTO-шки ----------

    private record ParsedShape(
            ConstShape shape,
            Vec3 position,
            Quat rotation,
            boolean hasPosition,
            boolean hasRotation,
            Vec3 centerOfMassOffset,
            boolean hasCenterOfMassOffset
    ) {
    }

    private record ComOffset(
            Vec3 offset,
            boolean hasOffset
    ) {
    }
}