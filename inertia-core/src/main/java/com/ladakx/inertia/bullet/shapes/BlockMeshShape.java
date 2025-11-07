package com.ladakx.inertia.bullet.shapes;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.infos.IndexedMesh;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.utils.block.BlockDataUtils;
import com.ladakx.inertia.bullet.block.BulletBlockSettings;
import com.ladakx.inertia.bullet.shapes.util.TriangulatedBoundingBox;
import com.ladakx.inertia.utils.block.BlockUtils;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Minecraft port shapes from JBullet
 * https://stephengold.github.io/Minie/minie/minie-library-tutorials/shape.html
 */
public class BlockMeshShape extends MeshCollisionShape {

    // ************************************
    // Cache
    private static final ConcurrentMap<ShapeKey, BlockMeshShape> shapeCache = new ConcurrentHashMap<>();
    public static final BlockMeshShape BLOCK = new BlockMeshShape(TriangulatedBoundingBox.BLOCK);
    public static final BlockMeshShape SLAB = new BlockMeshShape(TriangulatedBoundingBox.SLAB);

    // ***************
    // Constructors
    public BlockMeshShape(List<TriangulatedBoundingBox> triangles) {
        super(
                false,
                ((Supplier<IndexedMesh>) () -> {
                    List<Vector3f> allVerts = triangles.stream()
                            .flatMap(tbb -> tbb.getTriangles().stream())
                            .flatMap(Arrays::stream)
                            .toList();

                    final Vector3f[] vertices = allVerts.toArray(new Vector3f[0]);

                    final int[] indices = new int[vertices.length];
                    for (int i = 0; i < vertices.length; i++) {
                        indices[i] = i;
                    }

                    return new IndexedMesh(vertices, indices);
                }).get()
        );
    }

    public BlockMeshShape(TriangulatedBoundingBox triangle) {
        super(
                false,
                ((Supplier<IndexedMesh>) () -> {
                    // Собираем все вершины из одного TriangulatedBoundingBox
                    List<Vector3f> allVerts = triangle.getTriangles().stream()
                            .flatMap(Arrays::stream)
                            .toList();

                    final Vector3f[] vertices = allVerts.toArray(new Vector3f[0]);

                    // Формируем массив индексов
                    final int[] indices = new int[vertices.length];
                    for (int i = 0; i < vertices.length; i++) {
                        indices[i] = i;
                    }

                    // Создаём IndexedMesh из полученных вершин и индексов
                    return new IndexedMesh(vertices, indices);
                }).get()
        );
    }

    public BlockMeshShape(BoundingBox box) {
        this(new TriangulatedBoundingBox(box));
    }

    public BlockMeshShape(Mesh mesh) {
        super(false, convertToIndexedMesh(mesh));
    }

    // ***************
    // Methods
    /**
     * Метод для конвертации Mesh в IndexedMesh
     */
    private static IndexedMesh convertToIndexedMesh(Mesh mesh) {
        // Получение вершин.
        VertexBuffer vbPos = mesh.getBuffer(VertexBuffer.Type.Position);
        if (vbPos == null) {
            throw new IllegalArgumentException("Mesh does not have a Position buffer.");
        }
        FloatBuffer vertexData = (FloatBuffer) vbPos.getDataReadOnly();

        // Получение индексов.
        IndexBuffer ib = mesh.getIndexBuffer();
        int[] indices = new int[ib.size()];
        for (int i = 0; i < ib.size(); i++) {
            indices[i] = ib.get(i);
        }

        // Создание IntBuffer из индексов
        IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices);

        // Возвращаем IndexedMesh
        return new IndexedMesh(vertexData, indexBuffer);
    }

    private static IndexedMesh createIndexedMesh(List<TriangulatedBoundingBox> triangles) {
        return getIndexedMesh(triangles);
    }

    @NotNull
    static IndexedMesh getIndexedMesh(List<TriangulatedBoundingBox> triangles) {
        List<Vector3f> vertices = triangles.stream()
                .flatMap(tbb -> tbb.getTriangles().stream())
                .flatMap(Arrays::stream)
                .toList();
        int[] indices = new int[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            indices[i] = i;
        }
        return new IndexedMesh(vertices.toArray(new Vector3f[0]), indices);
    }

    // ***************
    // Create methods

    public static BlockMeshShape create(BoundingBox box) {
        return new BlockMeshShape(List.of(new TriangulatedBoundingBox(box)));
    }

    public static BlockMeshShape create(Iterable<BoundingBox> boxes) {
        List<TriangulatedBoundingBox> triangles = new ArrayList<>();
        for (BoundingBox box : boxes) {
            triangles.add(new TriangulatedBoundingBox(box));
        }

        return new BlockMeshShape(triangles);
    }

    public static BlockMeshShape create(BlockState block) {
        Material type = block.getType();
        if (BlockUtils.isCollidable(type)) {
            String properties = BlockDataUtils.getBlockStateKey(block);
            BulletBlockSettings settings = BulletBlockSettings.getBlockSettings(type);
            ShapeKey key = new ShapeKey(type, properties);

            if (settings.isFullBlock()) {
                return BlockMeshShape.BLOCK;
            } else if (settings.isSlab()) {
                return BlockMeshShape.SLAB;
            } else {
                return shapeCache.computeIfAbsent(key, k -> {
                    List<BoundingBox> boxList = settings.boxList();

                    // Use the first non-empty list between boxList and boundingBoxes
                    List<BoundingBox> relevantBoxes = !boxList.isEmpty() ? boxList :
                            InertiaPlugin.getBulletNMSTools().boundingBoxes(block);

                    if (!relevantBoxes.isEmpty()) {
                        return relevantBoxes.size() == 1
                                ? BlockMeshShape.create(relevantBoxes.get(0))
                                : BlockMeshShape.create(relevantBoxes);
                    }

                    return BlockMeshShape.create(new BoundingBox());
                });
            }
        }

        return null;
    }

    // ************************************************************
    // Shape key

    /**
     * Ключ для кеширования формы
     * @param type
     * @param properties
     */
    private record ShapeKey(Material type, String properties) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ShapeKey shapeKey)) return false;
            return type == shapeKey.type &&
                    ((properties == null && shapeKey.properties == null) ||
                            (properties != null && properties.equalsIgnoreCase(shapeKey.properties)));
        }
    }
}
