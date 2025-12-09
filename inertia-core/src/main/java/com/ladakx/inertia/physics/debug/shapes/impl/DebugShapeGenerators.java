package com.ladakx.inertia.physics.debug.shapes.impl;

import com.ladakx.inertia.physics.debug.shapes.DebugShapeGenerator;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class DebugShapeGenerators {

    private DebugShapeGenerators() {}

    public static class SphereGeneratorDebug implements DebugShapeGenerator {
        @Override
        public List<Vector> generatePoints(Location center, double... params) {
            if (params.length < 1) throw new IllegalArgumentException("Radius required");
            double radius = params[0];
            List<Vector> points = new ArrayList<>();
            int r = (int) Math.ceil(radius);
            double rSq = radius * radius;

            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        if (x*x + y*y + z*z <= rSq) points.add(new Vector(x, y, z));
                    }
                }
            }
            return points;
        }
        @Override public String getName() { return "sphere"; }
        @Override public String getUsage() { return "<radius>"; }
        @Override public int getParamCount() { return 1; }
    }

    public static class CubeGeneratorDebug implements DebugShapeGenerator {
        @Override
        public List<Vector> generatePoints(Location center, double... params) {
            if (params.length < 1) throw new IllegalArgumentException("Size required");
            double size = params[0];
            int half = (int) (size / 2);
            List<Vector> points = new ArrayList<>();
            for (int x = -half; x <= half; x++) {
                for (int y = -half; y <= half; y++) {
                    for (int z = -half; z <= half; z++) {
                        points.add(new Vector(x, y, z));
                    }
                }
            }
            return points;
        }
        @Override public String getName() { return "cube"; }
        @Override public String getUsage() { return "<size>"; }
        @Override public int getParamCount() { return 1; }
    }

    public static class CylinderGeneratorDebug implements DebugShapeGenerator {
        @Override
        public List<Vector> generatePoints(Location center, double... params) {
            if (params.length < 2) throw new IllegalArgumentException("Radius and Height required");
            double radius = params[0];
            double height = params[1];
            List<Vector> points = new ArrayList<>();
            int r = (int) Math.ceil(radius);
            int h = (int) Math.ceil(height);
            double rSq = radius * radius;

            for (int y = 0; y < h; y++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (x*x + z*z <= rSq) points.add(new Vector(x, y, z));
                    }
                }
            }
            return points;
        }
        @Override public String getName() { return "cylinder"; }
        @Override public String getUsage() { return "<radius> <height>"; }
        @Override public int getParamCount() { return 2; }
    }

    public static class PyramidGeneratorDebug implements DebugShapeGenerator {
        @Override
        public List<Vector> generatePoints(Location center, double... params) {
            if (params.length < 2) throw new IllegalArgumentException("Base Radius and Height required");
            double radius = params[0];
            double height = params[1];
            List<Vector> points = new ArrayList<>();
            int h = (int) height;
            for (int y = 0; y < h; y++) {
                double currentRadius = radius * (1.0 - (double)y / height);
                int r = (int) currentRadius;
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        points.add(new Vector(x, y, z));
                    }
                }
            }
            return points;
        }
        @Override public String getName() { return "pyramid"; }
        @Override public String getUsage() { return "<base_radius> <height>"; }
        @Override public int getParamCount() { return 2; }
    }
}