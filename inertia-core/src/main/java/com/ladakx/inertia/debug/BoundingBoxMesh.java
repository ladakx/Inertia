package com.ladakx.inertia.debug;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.math.Vector3f;
import com.jme3.util.BufferUtils;
import com.ladakx.inertia.bullet.shapes.util.TriangulatedBoundingBox;

import java.util.List;

public class BoundingBoxMesh {

    /**
     * Converts a TriangulatedBoundingBox into a Mesh for rendering.
     *
     * The process involves extracting triangles from the bounding box,
     * building vertex and index arrays, and then creating and configuring
     * a Mesh using these arrays.
     *
     * @param bbox the TriangulatedBoundingBox to convert
     * @return a Mesh that represents the bounding box for rendering
     */
    public static Mesh createMeshFromBoundingBox(TriangulatedBoundingBox bbox) {
        // Retrieve the list of triangles from the bounding box.
        // Each triangle is represented as an array of 3 vertices (Vector3f).
        List<Vector3f[]> triangles = bbox.getTriangles();

        // Calculate the total number of triangles.
        int numTriangles = triangles.size();

        // Prepare arrays to store vertex positions and index order.
        // Each triangle contributes 3 vertices and 3 indices.
        Vector3f[] vertices = new Vector3f[numTriangles * 3];
        int[] indices = new int[numTriangles * 3];

        int vertIndex = 0;
        // Iterate over every triangle in the list.
        for (int i = 0; i < numTriangles; i++) {
            Vector3f[] tri = triangles.get(i);

            // Store each vertex of the triangle in the vertices array.
            vertices[vertIndex]   = tri[0];
            vertices[vertIndex+1] = tri[1];
            vertices[vertIndex+2] = tri[2];

            // Set the corresponding indices that define the triangle order.
            indices[vertIndex]    = vertIndex;
            indices[vertIndex+1]  = vertIndex + 1;
            indices[vertIndex+2]  = vertIndex + 2;

            // Move to the next set of vertices for the following triangle.
            vertIndex += 3;
        }

        // Create a new mesh instance.
        Mesh mesh = new Mesh();
        // Set the vertex buffer using the vertices array.
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
        // Set the index buffer using the indices array.
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));

        // Update the mesh bounds and counts to ensure it is properly rendered.
        mesh.updateBound();
        mesh.updateCounts();

        // Return the constructed mesh.
        return mesh;
    }
}