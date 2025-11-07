package com.ladakx.inertia.utils.serializers;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BoundingSerializer {

    /**
     * Serialize BoundingBox to string
     * @param path The path to serialize
     * @param cfg The configuration to serialize
     * @return The serialized BoundingBox
     */
    public static BoundingBox serialize(String path, FileConfiguration cfg) {
        Vector3f center = Vector3fSerializer.serialize(path + ".Center", cfg);
        BoundingBox result = new BoundingBox();

        if (cfg.contains(path + ".Max") || cfg.contains(path + ".Min")) {
            Vector3f min = Vector3fSerializer.serialize(path + ".Min", cfg);
            Vector3f max = Vector3fSerializer.serialize(path + ".Max", cfg);
            result = new BoundingBox(center, min, max);
        } else if (cfg.contains(path + ".Height") || cfg.contains(path + ".Width") || cfg.contains(path + ".Length")) {
            float width = (float) cfg.getDouble(path + ".Width", 0.0D);
            float height = (float) cfg.getDouble(path + ".Height", 0.0D);
            float length = (float) cfg.getDouble(path + ".Length", 0.0D);
            result = new BoundingBox(center, width, height, length);
        }
        return result;
    }

    /**
     * Serialize BoundingBox to string Example of serialization:
     * "x=... y=... z=... center=[cx cy cz]"
     * @param input The string to serialize
     * @return The serialized BoundingBox
     */
    public static BoundingBox parseFromString(String input) {
        // Example of parsing x, y, z from the string
        Pattern patternX = Pattern.compile("x=([\\d.+-E]+)");
        Pattern patternY = Pattern.compile("y=([\\d.+-E]+)");
        Pattern patternZ = Pattern.compile("z=([\\d.+-E]+)");
        Pattern patternCenter = Pattern.compile("center=\\[([\\d.+-E]+)\\s+([\\d.+-E]+)\\s+([\\d.+-E]+)\\]");

        float xVal = 0f, yVal = 0f, zVal = 0f;
        Vector3f center = new Vector3f(0, 0, 0);

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
            center = new Vector3f(cx, cy, cz);
        }


        return new BoundingBox(center, xVal, yVal, zVal);
    }

    /**
     * Converts the BoundingBox back to a string format
     * “x=... y=... z=... center=[cx cy cz]”
     * depending on how you want to implement the logic.
     */
    public static String serializeToString(BoundingBox box) {
        float xVal = box.getXExtent();
        float yVal = box.getYExtent();
        float zVal = box.getZExtent();
        Vector3f center = box.getCenter(new Vector3f());

        Vector3f c = center != null ? center : Vector3f.ZERO;
        return String.format("x=%.2f y=%.2f z=%.2f center=[%.2f %.2f %.2f]",
                xVal, yVal, zVal, c.x, c.y, c.z);
    }

    /**
     * Method for (de)serializing a BoundingBox list.
     * Here is one implementation based on lists of strings.
     */
    public static List<BoundingBox> parseListFromStrings(List<String> inputs) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (String input : inputs) {
            boxes.add(parseFromString(input));
        }
        return boxes;
    }

    /**
     * Method for (de)serializing a BoundingBox list.
     * @param boxes The list of BoundingBox
     * @return The serialized list of BoundingBox
     */
    public static List<String> serializeListToStrings(List<BoundingBox> boxes) {
        List<String> results = new ArrayList<>();
        for (BoundingBox box : boxes) {
            results.add(serializeToString(box));
        }
        return results;
    }
}
