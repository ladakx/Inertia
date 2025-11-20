package com.ladakx.inertia.utils.serializers;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Vec3;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serializer for Jolt AaBox using the same config/string format
 * as the old jME BoundingBox version.
 */
public final class BoundingSerializer {

    private BoundingSerializer() {
        // utility class
    }

    /**
     * Deserialize AaBox from config section.
     *
     * Supports 2 формати:
     *  1) Min/Max вектора:
     *     path.Min  (Vec3)
     *     path.Max  (Vec3)
     *  2) Center + Width/Height/Length (half extents):
     *     path.Center (Vec3)
     *     path.Width
     *     path.Height
     *     path.Length
     *
     * @param path конфіг-шлях до бокса
     * @param cfg  файл конфігурації
     * @return AaBox, побудований за даними
     */
    public static AaBox serialize(String path, FileConfiguration cfg) {
        Vec3 center = Vec3Serializer.serialize(path + ".Center", cfg);
        if (center == null) {
            center = new Vec3(0f, 0f, 0f);
        }

        AaBox result = new AaBox(); // спочатку "invalid", нижче створимо нормальний

        if (cfg.contains(path + ".Max") || cfg.contains(path + ".Min")) {
            // Формат 1: явні Min/Max
            Vec3 min = Vec3Serializer.serialize(path + ".Min", cfg);
            Vec3 max = Vec3Serializer.serialize(path + ".Max", cfg);
            if (min == null) min = new Vec3(0f, 0f, 0f);
            if (max == null) max = new Vec3(0f, 0f, 0f);

            result = new AaBox(min, max);
        } else if (cfg.contains(path + ".Height") || cfg.contains(path + ".Width") || cfg.contains(path + ".Length")) {
            // Формат 2: центр + half extents (width/height/length)
            float xExtent = (float) cfg.getDouble(path + ".Width", 0.0D);
            float yExtent = (float) cfg.getDouble(path + ".Height", 0.0D);
            float zExtent = (float) cfg.getDouble(path + ".Length", 0.0D);

            Vec3 min = new Vec3(
                    center.getX() - xExtent,
                    center.getY() - yExtent,
                    center.getZ() - zExtent
            );
            Vec3 max = new Vec3(
                    center.getX() + xExtent,
                    center.getY() + yExtent,
                    center.getZ() + zExtent
            );

            result = new AaBox(min, max);
        }

        return result;
    }

    /**
     * Parse AaBox from string, наприклад:
     * "x=0.5 y=0.25 z=0.5 center=[0.0 -0.25 0.0]"
     *
     * x/y/z трактуються як half extents, center — центр бокса.
     */
    public static AaBox parseFromString(String input) {
        Pattern patternX = Pattern.compile("x=([\\d.+-E]+)");
        Pattern patternY = Pattern.compile("y=([\\d.+-E]+)");
        Pattern patternZ = Pattern.compile("z=([\\d.+-E]+)");
        Pattern patternCenter = Pattern.compile("center=\\[([\\d.+-E]+)\\s+([\\d.+-E]+)\\s+([\\d.+-E]+)\\]");

        float xVal = 0f, yVal = 0f, zVal = 0f;
        Vec3 center = new Vec3(0f, 0f, 0f);

        Matcher mx = patternX.matcher(input);
        Matcher my = patternY.matcher(input);
        Matcher mz = patternZ.matcher(input);
        Matcher mc = patternCenter.matcher(input);

        if (mx.find()) {
            xVal = Float.parseFloat(mx.group(1));
        }
        if (my.find()) {
            yVal = Float.parseFloat(my.group(1));
        }
        if (mz.find()) {
            zVal = Float.parseFloat(mz.group(1));
        }
        if (mc.find()) {
            float cx = Float.parseFloat(mc.group(1));
            float cy = Float.parseFloat(mc.group(2));
            float cz = Float.parseFloat(mc.group(3));
            center = new Vec3(cx, cy, cz);
        }

        // half extents → min/max
        Vec3 min = new Vec3(
                center.getX() - xVal,
                center.getY() - yVal,
                center.getZ() - zVal
        );
        Vec3 max = new Vec3(
                center.getX() + xVal,
                center.getY() + yVal,
                center.getZ() + zVal
        );

        return new AaBox(min, max);
    }

    /**
     * Конвертація AaBox назад у формат:
     * "x=... y=... z=... center=[cx cy cz]"
     */
    public static String serializeToString(AaBox box) {
        Vec3 min = box.getMin();
        Vec3 max = box.getMax();

        float xExtent = (max.getX() - min.getX()) / 2f;
        float yExtent = (max.getY() - min.getY()) / 2f;
        float zExtent = (max.getZ() - min.getZ()) / 2f;

        float cx = (min.getX() + max.getX()) / 2f;
        float cy = (min.getY() + max.getY()) / 2f;
        float cz = (min.getZ() + max.getZ()) / 2f;

        return String.format(
                "x=%.2f y=%.2f z=%.2f center=[%.2f %.2f %.2f]",
                xExtent, yExtent, zExtent, cx, cy, cz
        );
    }

    /**
     * (De)серіалізація списку AaBox з/в список строк.
     */
    public static List<AaBox> parseListFromStrings(List<String> inputs) {
        List<AaBox> boxes = new ArrayList<>();
        for (String input : inputs) {
            boxes.add(parseFromString(input));
        }
        return boxes;
    }

    public static List<String> serializeListToStrings(Collection<AaBox> boxes) {
        List<String> results = new ArrayList<>();
        for (AaBox box : boxes) {
            results.add(serializeToString(box));
        }
        return results;
    }
}