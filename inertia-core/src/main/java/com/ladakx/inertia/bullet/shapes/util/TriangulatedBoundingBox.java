package com.ladakx.inertia.bullet.shapes.util;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class TriangulatedBoundingBox {

    public static final TriangulatedBoundingBox BLOCK = new TriangulatedBoundingBox(BoundingBox.BLOCK);
    public static final TriangulatedBoundingBox SLAB = new TriangulatedBoundingBox(BoundingBox.SLAB);

    private final Vector3f min;
    private final Vector3f max;
    private final List<Vector3f[]> triangles; // Each element is an array of 3 vertices

    /**
     * Create a new TriangulatedBoundingBox from two corners.
     *
     * @param min The minimum corner (xMin, yMin, zMin).
     * @param max The maximum corner (xMax, yMax, zMax).
     */
    public TriangulatedBoundingBox(Vector3f min, Vector3f max) {
        this.min = min.clone();
        this.max = max.clone();
        this.triangles = createTriangles(this.min, this.max);
    }

    /**
     * New constructor that accepts a BoundingBox.
     * It extracts the min and max corners from the given BoundingBox
     * and uses them to initialize this TriangulatedBoundingBox.
     *
     * @param box an instance of com.jme3.bounding.BoundingBox
     */
    public TriangulatedBoundingBox(BoundingBox box) {
        Vector3f boxMin = box.getMin(null); // Get the min corner
        Vector3f boxMax = box.getMax(null); // Get the max corner

        this.min = boxMin.clone();
        this.max = boxMax.clone();
        this.triangles = createTriangles(this.min, this.max);
    }

    /**
     * (Optional) If you want to pass in triangles directly,
     * you can provide another constructor:
     */
    private TriangulatedBoundingBox(Vector3f min, Vector3f max, List<Vector3f[]> triangles) {
        this.min = min;
        this.max = max;
        this.triangles = triangles;
    }

    public static TriangulatedBoundingBox create(BoundingBox box) {
        return new TriangulatedBoundingBox(box);
    }

    public static List<TriangulatedBoundingBox> create(List<BoundingBox> boxes) {
        List<TriangulatedBoundingBox> triangles = new ArrayList<>();
        for (BoundingBox box : boxes) {
            triangles.add(new TriangulatedBoundingBox(box));
        }
        return triangles;
    }

    /**
     * Returns all the triangle vertex arrays.
     */
    public List<Vector3f[]> getTriangles() {
        return triangles;
    }

    /**
     * Creates 12 triangles (2 for each face of a cube).
     */
    private List<Vector3f[]> createTriangles(Vector3f minVec, Vector3f maxVec) {
        List<Vector3f[]> result = new ArrayList<>(12);

        // Precompute corners for clarity.
        Vector3f c000 = new Vector3f(minVec.x, minVec.y, minVec.z);
        Vector3f c001 = new Vector3f(minVec.x, minVec.y, maxVec.z);
        Vector3f c010 = new Vector3f(minVec.x, maxVec.y, minVec.z);
        Vector3f c011 = new Vector3f(minVec.x, maxVec.y, maxVec.z);
        Vector3f c100 = new Vector3f(maxVec.x, minVec.y, minVec.z);
        Vector3f c101 = new Vector3f(maxVec.x, minVec.y, maxVec.z);
        Vector3f c110 = new Vector3f(maxVec.x, maxVec.y, minVec.z);
        Vector3f c111 = new Vector3f(maxVec.x, maxVec.y, maxVec.z);

        // Each face of the box is split into 2 triangles.

        // Front face (min Z)
        result.add(new Vector3f[]{c000, c100, c110});
        result.add(new Vector3f[]{c000, c110, c010});

        // Back face (max Z)
        result.add(new Vector3f[]{c001, c111, c101});
        result.add(new Vector3f[]{c001, c011, c111});

        // Left face (min X)
        result.add(new Vector3f[]{c000, c010, c011});
        result.add(new Vector3f[]{c000, c011, c001});

        // Right face (max X)
        result.add(new Vector3f[]{c100, c101, c111});
        result.add(new Vector3f[]{c100, c111, c110});

        // Bottom face (min Y)
        result.add(new Vector3f[]{c000, c001, c101});
        result.add(new Vector3f[]{c000, c101, c100});

        // Top face (max Y)
        result.add(new Vector3f[]{c010, c110, c111});
        result.add(new Vector3f[]{c010, c111, c011});

        return result;
    }

    /**
     * This method transforms all triangle vertices using a Quaternion and returns a new TriangulatedBoundingBox.
     */
    public TriangulatedBoundingBox transform(Quaternion rotation) {
        // We'll create a new list of triangles for the transformed data
        List<Vector3f[]> transformedTriangles = new ArrayList<>(triangles.size());

        // Use these to track the new min/max corners
        Vector3f newMin = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f newMax = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

        for (Vector3f[] tri : triangles) {
            Vector3f[] newTri = new Vector3f[3];

            for (int i = 0; i < 3; i++) {
                // Transform each vertex
                Vector3f transformedVertex = transformVertex(tri[i], rotation);

                // Update min and max
                updateMinMax(newMin, newMax, transformedVertex);

                newTri[i] = transformedVertex;
            }
            transformedTriangles.add(newTri);
        }

        // Create a new TriangulatedBoundingBox with the transformed data
        return new TriangulatedBoundingBox(newMin, newMax, transformedTriangles);
    }

    /**
     * Performs the same quaternion-based transformation as seen in the old Triangle class.
     */
    private static Vector3f transformVertex(Vector3f vector, Quaternion rotation) {
        Quaternion quaternion = new Quaternion(rotation);
        // Convert the vector into a quaternion with w = 0
        quaternion.multLocal(new Quaternion(vector.getX(), vector.getY(), vector.getZ(), 0.0F));
        // Conjugate of the original rotation
        Quaternion conjugate = new Quaternion(-rotation.getX(), -rotation.getY(), -rotation.getZ(), rotation.getW());
        quaternion.multLocal(conjugate);

        return new Vector3f(quaternion.getX(), quaternion.getY(), quaternion.getZ());
    }

    /**
     * Helper method to update the bounding corner values.
     */
    private static void updateMinMax(Vector3f min, Vector3f max, Vector3f vertex) {
        min.x = Math.min(min.x, vertex.x);
        min.y = Math.min(min.y, vertex.y);
        min.z = Math.min(min.z, vertex.z);

        max.x = Math.max(max.x, vertex.x);
        max.y = Math.max(max.y, vertex.y);
        max.z = Math.max(max.z, vertex.z);
    }

    public Vector3f getMin() {
        return min;
    }

    public Vector3f getMax() {
        return max;
    }
}