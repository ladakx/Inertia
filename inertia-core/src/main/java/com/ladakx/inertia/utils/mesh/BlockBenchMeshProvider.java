package com.ladakx.inertia.utils.mesh;

import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.InertiaLogger;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MeshProvider, який завантажує вершини з Wavefront OBJ файлів,
 * експортованих із BlockBench (або будь-якого іншого редактора).
 *
 * Формат:
 *   - беруться лише рядки, що починаються з "v ":
 *       v x y z
 *   - усе інше ("vn", "vt", "f", "#", ...) ігнорується.
 *
 * Використання в конфізі:
 *   shape:
 *     - "type=convex_hull mesh=models/car_body.obj convexRadius=0.05"
 *
 * Порядок пошуку файлу:
 *   1) resources всередині JAR (plugin.getResource(meshId))
 *   2) файл у data-folder: new File(plugin.getDataFolder(), meshId)
 */
public class BlockBenchMeshProvider implements MeshProvider {

    private final Plugin plugin;

    /**
     * Кеш вершин по meshId, щоб не парсити файли щоразу.
     */
    private final Map<String, Collection<Vec3>> cache = new ConcurrentHashMap<>();

    public BlockBenchMeshProvider(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Collection<Vec3> loadConvexHullPoints(String meshId) {
        // Спочатку пробуємо взяти з кеша
        Collection<Vec3> cached = cache.get(meshId);
        if (cached != null) {
            return cached;
        }

        List<Vec3> result = new ArrayList<>();

        try (InputStream in = openMeshStream(meshId)) {
            if (in == null) {
                throw new FileNotFoundException("Mesh resource/file not found: " + meshId);
            }
            parseObjVertices(in, result);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load mesh '" + meshId + "': " + e.getMessage(), e
            );
        }

        if (result.isEmpty()) {
            InertiaLogger.warn(
                    "Mesh '" + meshId + "' contains no vertices (no 'v x y z' lines)."
            );
        }

        // Фіксуємо як unmodifiable, щоб не міняли ззовні
        Collection<Vec3> unmodifiable = Collections.unmodifiableCollection(result);
        cache.put(meshId, unmodifiable);
        return unmodifiable;
    }

    /**
     * Відкрити потік для меша:
     *   1) plugin.getResource(meshId) — ресурс із JAR (наприклад, "models/car.obj")
     *   2) файл у data-folder (plugins/YourPlugin/meshId)
     */
    private InputStream openMeshStream(String meshId) throws IOException {
        // 1) resources всередині JAR
        InputStream resourceStream = plugin.getResource(meshId);
        if (resourceStream != null) {
            return resourceStream;
        }

        // 2) файл в data-folder
        File file = new File(plugin.getDataFolder(), meshId);
        if (file.isFile() && file.exists()) {
            return new FileInputStream(file);
        }

        return null;
    }

    /**
     * Розпарсити вершини з OBJ-стріму.
     * Беремо лише рядки виду:
     *   v x y z
     */
    private void parseObjVertices(InputStream in, List<Vec3> outVertices) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Пропускаємо коментарі та порожні строки
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Нас цікавлять лише вершини: "v x y z"
                if (line.startsWith("v ")) {
                    parseObjVertexLine(line, outVertices);
                }
                // Усе інше ("vt", "vn", "f", "o", "g"...) опускаємо
            }
        }
    }

    /**
     * Розпарсити один рядок "v x y z" і додати вершину до списку.
     */
    private void parseObjVertexLine(String line, List<Vec3> outVertices) {
        // Відтинаємо початкове "v" і сплітимо по пробілах
        // Приклад:
        //   "v 1.0 2.0 3.0"
        //   => ["v","1.0","2.0","3.0"]
        String[] parts = line.split("\\s+");
        if (parts.length < 4) {
            InertiaLogger.warn("Invalid OBJ vertex line (expected 'v x y z'): '" + line + "'");
            return;
        }

        try {
            float x = Float.parseFloat(parts[1]);
            float y = Float.parseFloat(parts[2]);
            float z = Float.parseFloat(parts[3]);
            outVertices.add(new Vec3(x, y, z));
        } catch (NumberFormatException e) {
            InertiaLogger.warn("Failed to parse OBJ vertex line: '" + line + "'. Error: " + e.getMessage());
        }
    }
}