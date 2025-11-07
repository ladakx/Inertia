package com.jme3.bullet.util;

import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.infos.IndexedMesh;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class Converter {
    private Converter() {
        // приватный конструктор, чтобы класс нельзя было инстанцировать
    }

    /**
     * Преобразовать jME-меш в IndexedMesh (LibBulletJME).
     * Аналог старого convert(Mesh) для jBullet,
     * но теперь создаём объект IndexedMesh c FloatBuffer/IntBuffer.
     *
     * @param mesh jME-меш (должен иметь позиции и треугольные индексы)
     * @return IndexedMesh (не null)
     */
    public static IndexedMesh toIndexedMesh(Mesh mesh) {
        if (mesh == null) {
            throw new IllegalArgumentException("Mesh is null.");
        }

        // 1) Достаём позиционный буфер
        VertexBuffer vbPos = mesh.getBuffer(VertexBuffer.Type.Position);
        if (vbPos == null) {
            throw new IllegalArgumentException("Mesh does not have a Position buffer.");
        }
        // без .getDataReadOnly(), т.к. LibBulletJME конструктор ожидает normal FloatBuffer
        FloatBuffer positionData = (FloatBuffer) vbPos.getData();
        positionData.rewind();

        // 2) Достаём индексный буфер
        IndexBuffer ib = mesh.getIndexBuffer();
        int indexCount = ib.size();
        int[] indexArray = new int[indexCount];
        for (int i = 0; i < indexCount; i++) {
            indexArray[i] = ib.get(i);
        }

        // 3) Создаём IntBuffer
        IntBuffer indexBuf = BufferUtils.createIntBuffer(indexArray);
        indexBuf.rewind();

        // 4) Используем конструктор IndexedMesh(FloatBuffer, IntBuffer)
        IndexedMesh indexedMesh = new IndexedMesh(positionData, indexBuf);

        // PS: при необходимости positionData / indexBuf нужно "reset" (flip/rewind),
        // но createIntBuffer уже сделал flip.
        return indexedMesh;
    }

    /**
     * Преобразовать IndexedMesh (LibBulletJME) обратно в jME-меш (Mesh).
     * Аналог старого convert(IndexedMesh),
     * но теперь опираемся на поля/методы IndexedMesh из LibBulletJME.
     *
     * @param bulletMesh исходный IndexedMesh
     * @return jME Mesh (не null)
     */
    public static Mesh toJmeMesh(IndexedMesh bulletMesh) {
        if (bulletMesh == null) {
            throw new IllegalArgumentException("IndexedMesh is null.");
        }

        // 1) Кол-во вершин и треугольников
        int numVertices = bulletMesh.countVertices();
        int numTriangles = bulletMesh.countTriangles();

        // 2) Получаем копию буфера позиций (unflipped)
        FloatBuffer positionsCopy = bulletMesh.copyVertexPositions();
        positionsCopy.rewind();
        // 3 float на вершину
        if (positionsCopy.limit() != numVertices * 3) {
            throw new RuntimeException("Mismatch in vertex buffer size.");
        }

        // 3) Получаем копию буфера индексов
        IntBuffer indicesCopy = bulletMesh.copyIndices();
        indicesCopy.rewind();
        if (indicesCopy.limit() != numTriangles * 3) {
            throw new RuntimeException("Mismatch in index buffer size.");
        }

        // 4) Создаём jME Mesh и буферы
        Mesh jmeMesh = new Mesh();

        // Индексы: jME по умолчанию любит ShortBuffer, но если слишком много вершин, нужен IntBuffer
        // Для упрощения, можно всегда делать setBuffer(Type.Index, 3, IntBuffer).
        // Но учтите, что на некоторых видеокартах 32-битные индексы не поддержаны.
        // Допустим, используем IntBuffer:
        jmeMesh.setBuffer(VertexBuffer.Type.Index, 3, indicesCopy);

        // Позиции: создаём новый FloatBuffer той же длины
        FloatBuffer posBuffer = BufferUtils.createFloatBuffer(numVertices * 3);
        positionsCopy.rewind();
        posBuffer.put(positionsCopy);
        posBuffer.flip();

        jmeMesh.setBuffer(VertexBuffer.Type.Position, 3, posBuffer);

        // 5) обновляем counts/bounds
        jmeMesh.updateCounts();
        jmeMesh.updateBound();

        return jmeMesh;
    }

    /**
     * Собрать один jME Mesh, содержащий геометрию всех сабмешей
     * данного MeshCollisionShape.
     *
     * @param shape исходная форма (не null)
     * @return jME Mesh (никогда не null)
     */
    public static Mesh toJmeMesh(MeshCollisionShape shape) {
        if (shape == null) {
            throw new IllegalArgumentException("shape is null");
        }

        // 1) Подсчитаем, сколько всего вершин и треугольников во всех submesh
        int numSubmeshes = shape.countSubmeshes();
        int totalVertices = 0;
        int totalTriangles = 0;

        for (int i = 0; i < numSubmeshes; i++) {
            IndexedMesh sub = shape.getSubmesh(i);
            totalVertices += sub.countVertices();
            totalTriangles += sub.countTriangles();
        }

        // 2) Создадим общий IntBuffer для индексов (3 ints на треугольник)
        int totalIndices = totalTriangles * 3;
        IntBuffer indexBuf = BufferUtils.createIntBuffer(totalIndices);

        // 3) Создадим общий FloatBuffer для позиций (3 floats на вершину)
        FloatBuffer posBuf = BufferUtils.createFloatBuffer(totalVertices * 3);

        // 4) Пройдемся по всем submesh, копируя их вершины и индексы.
        //    Для индексов нужно учитывать смещение (vertex offset),
        //    т.к. каждая пачка вершин добавляется дальше в общий буфер.
        int vertexOffset = 0;
        for (int i = 0; i < numSubmeshes; i++) {
            IndexedMesh sub = shape.getSubmesh(i);

            // 4.1) Скопируем вершины
            FloatBuffer subPos = sub.copyVertexPositions(); // unflipped
            subPos.rewind();
            int subVertCount = sub.countVertices();
            // Пишем их в общий posBuf
            posBuf.put(subPos);

            // 4.2) Скопируем индексы
            IntBuffer subIdx = sub.copyIndices();
            subIdx.rewind();
            int subIdxCount = subIdx.capacity(); // = sub.countTriangles() * 3

            for (int idx = 0; idx < subIdxCount; idx++) {
                int oldIndex = subIdx.get();
                // Прибавляем смещение
                indexBuf.put(oldIndex + vertexOffset);
            }

            vertexOffset += subVertCount; // теперь все будущие индексы выше
        }

        // 5) flip-аем большие буфера
        posBuf.flip();
        indexBuf.flip();

        // 6) Создаём jME-меш
        Mesh jmeMesh = new Mesh();

        //    setBuffer(Type.Position, 3, posBuf)
        jmeMesh.setBuffer(VertexBuffer.Type.Position, 3, posBuf);

        //    setBuffer(Type.Index, 3, indexBuf)
        //    (Используем int-индексы: внимание, если totalVertices > 65535)
        jmeMesh.setBuffer(VertexBuffer.Type.Index, 3, indexBuf);

        jmeMesh.updateCounts();
        jmeMesh.updateBound();

        return jmeMesh;
    }
}
