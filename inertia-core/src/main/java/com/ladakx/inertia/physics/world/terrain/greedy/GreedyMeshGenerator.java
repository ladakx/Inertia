package com.ladakx.inertia.physics.world.terrain.greedy;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.configuration.dto.BlocksConfig;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.physics.world.terrain.DirtyChunkRegion;
import com.ladakx.inertia.infrastructure.nms.jolt.JoltTools;
import com.ladakx.inertia.physics.world.terrain.ChunkSnapshotData;
import com.ladakx.inertia.physics.world.terrain.PhysicsGenerator;
import com.ladakx.inertia.physics.world.terrain.profile.PhysicalProfile;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Material;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

public class GreedyMeshGenerator implements PhysicsGenerator<GreedyMeshData> {
    private final BlocksConfig blocksConfig;
    private final JoltTools joltTools;
    private final WorldsConfig.GreedyMeshingSettings settings;
    private final short[] materialToProfileId;
    private final PhysicalProfile[] profilesById;

    // Используем ThreadLocal для буферов, чтобы не аллоцировать память каждый раз
    private static final ThreadLocal<WorkingBuffers> BUFFERS = ThreadLocal.withInitial(WorkingBuffers::new);
    private static final short EMPTY_PROFILE = 0;
    private static final double FULL_REBUILD_THRESHOLD = 0.40d;

    public GreedyMeshGenerator(BlocksConfig blocksConfig, JoltTools joltTools, WorldsConfig.GreedyMeshingSettings settings) {
        this.blocksConfig = Objects.requireNonNull(blocksConfig, "blocksConfig");
        this.joltTools = Objects.requireNonNull(joltTools, "joltTools");
        this.settings = Objects.requireNonNull(settings, "settings");
        ProfileMappings mappings = buildProfileMappings();
        this.materialToProfileId = mappings.materialToProfileId();
        this.profilesById = mappings.profilesById();
    }

    public short[] materialToProfileId() {
        return materialToProfileId;
    }

    private ProfileMappings buildProfileMappings() {
        Material[] materials = Material.values();
        short[] materialIds = new short[materials.length];
        List<PhysicalProfile> profileList = new ArrayList<>();

        for (Material material : materials) {
            PhysicalProfile profile = resolvePhysicalProfile(material);
            if (profile == null || profile.boundingBoxes().isEmpty()) {
                continue;
            }
            if (profileList.size() >= Short.MAX_VALUE - 1) {
                throw new IllegalStateException("Too many physical profiles for compact short ids");
            }
            short profileId = (short) (profileList.size() + 1);
            materialIds[material.ordinal()] = profileId;
            profileList.add(profile);
        }

        return new ProfileMappings(materialIds, profileList.toArray(new PhysicalProfile[0]));
    }

    private PhysicalProfile resolvePhysicalProfile(Material material) {
        return blocksConfig.find(material).orElseGet(() -> {
            try {
                if (material.isBlock() && !material.isAir()) {
                    BlockState state = joltTools.createBlockState(material);
                    List<AaBox> boxes = joltTools.boundingBoxes(state);
                    if (boxes != null && !boxes.isEmpty()) {
                        return new PhysicalProfile(material.name(), 0.5f, 0.6f, 0.2f, boxes);
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        });
    }

    @Override
    public GreedyMeshData generate(ChunkSnapshotData snapshot) {
        return generate(snapshot, null);
    }

    @Override
    public GreedyMeshData generate(ChunkSnapshotData snapshot, DirtyChunkRegion dirtyRegion) {
        Objects.requireNonNull(snapshot, "snapshot");
        int minSectionY = snapshot.minSectionY();
        int sectionsCount = snapshot.sectionsCount();
        int minHeight = minSectionY << 4;
        int height = sectionsCount << 4;
        GenerationRange range = resolveGenerationRange(snapshot, dirtyRegion);

        WorkingBuffers buffers = BUFFERS.get();
        buffers.ensureCapacity(height);

        short[] profileIndices = buffers.profileIndices;
        boolean[] visited = buffers.visited;
        int[] touchedIndices = buffers.touchedIndices;
        int[] complexIndices = buffers.complexIndices;
        short[] layerProfiles = buffers.layerProfiles;
        boolean[] layerVisited = buffers.layerVisited;

        int touchedCount = 0;
        int complexCount = 0;

        List<GreedyMeshShape> shapes = new ArrayList<>();
        Set<Integer> touchedSections = range.fullRebuild() ? Set.of() : collectTouchedSections(range, minSectionY);

        // 1. Заполнение воксельной сетки
        for (int sectionIndex = range.startSectionIndex(); sectionIndex <= range.endSectionIndex(); sectionIndex++) {
            int sectionY = minSectionY + sectionIndex;
            if (!snapshot.sectionHasBlocks(sectionIndex)) {
                continue;
            }
            int baseY = sectionIndex << 4;

            for (int y = 0; y < 16; y++) {
                int worldIndexY = baseY + y;
                if (worldIndexY < range.startLocalY() || worldIndexY > range.endLocalY()) {
                    continue;
                }
                for (int z = 0; z < 16; z++) {
                    if (z < range.minLocalZ() || z > range.maxLocalZ()) {
                        continue;
                    }
                    for (int x = 0; x < 16; x++) {
                        if (x < range.minLocalX() || x > range.maxLocalX()) {
                            continue;
                        }
                        short profileId = snapshot.getProfileId(x, worldIndexY, z);
                        if (profileId == EMPTY_PROFILE) {
                            continue;
                        }

                        PhysicalProfile profile = profileFromIndex(profileId);
                        if (profile != null && !profile.boundingBoxes().isEmpty()) {
                            int index = flattenIndex(x, worldIndexY, z);

                            if (profileIndices[index] == EMPTY_PROFILE) {
                                touchedIndices[touchedCount++] = index;
                            }
                            profileIndices[index] = profileId;

                            // Сложные формы (заборы, ступени) обрабатываем отдельно, без мержинга
                            if (profile.boundingBoxes().size() > 1) {
                                complexIndices[complexCount++] = index;
                            }
                        }
                    }
                }
            }
        }

        // 2. Обработка сложных форм (без Greedy Mesh, просто конвертация)
        for (int i = 0; i < complexCount; i++) {
            int index = complexIndices[i];
            PhysicalProfile profile = profileFromIndex(profileIndices[index]);
            if (profile == null) continue;

            int x = index & 0xF;
            int z = (index >> 4) & 0xF;
            int y = index >> 8;
            int worldY = minHeight + y;

            float[] triangles = toTriangles(profile.boundingBoxes(), x, worldY, z);

            shapes.add(new GreedyMeshShape(
                    profile.id(), profile.density(), profile.friction(), profile.restitution(),
                    triangles, x, worldY, z, x + 1, worldY + 1, z + 1
            ));
            visited[index] = true;
        }

        // 3. Greedy Meshing для простых форм (полных блоков)
        // Используем массив списков для хранения прямоугольников каждого слоя
        @SuppressWarnings("unchecked")
        List<LayerRect>[] layerRects = new List[height];

        // 3.1 Послойный мержинг (Horizontal merging)
        for (int y = range.startLocalY(); y <= range.endLocalY(); y++) {
            // Очистка буферов слоя
            Arrays.fill(layerProfiles, EMPTY_PROFILE);
            Arrays.fill(layerVisited, false);

            // Копируем данные слоя из 3D массива
            boolean layerEmpty = true;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = flattenIndex(x, y, z);
                    if (visited[index]) continue; // Уже обработано как сложная форма

                    short profileIndex = profileIndices[index];
                    if (profileIndex != EMPTY_PROFILE) {
                        layerProfiles[layerIndex(x, z)] = profileIndex;
                        layerEmpty = false;
                    }
                }
            }

            if (layerEmpty) continue;

            // Жадный алгоритм по X и Z
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int maskIndex = layerIndex(x, z);
                    if (layerVisited[maskIndex]) continue;

                    short profileIndex = layerProfiles[maskIndex];
                    if (profileIndex == EMPTY_PROFILE) continue;

                    PhysicalProfile profile = profileFromIndex(profileIndex);
                    // Мержим только полные блоки (с 1 коробкой)
                    if (profile == null || profile.boundingBoxes().size() != 1) {
                        layerVisited[maskIndex] = true;
                        continue;
                    }

                    int width = expandLayerX(layerProfiles, layerVisited, profileIndex, x, z);
                    int depth = expandLayerZ(layerProfiles, layerVisited, profileIndex, x, z, width);

                    markLayerVisited(layerVisited, x, z, width, depth);

                    if (layerRects[y] == null) {
                        layerRects[y] = new ArrayList<>();
                    }
                    layerRects[y].add(new LayerRect(x, z, width, depth, y, profileIndex));
                }
            }
        }

        // 3.2 Вертикальный мержинг (Vertical merging) и генерация меша
        if (settings.verticalMerging()) {
            int maxTall = settings.maxVerticalSize();
            Long2ObjectMap<ActiveRect> active = buffers.activeRects;
            LongSet touchedKeys = buffers.touchedKeys;
            active.clear();

            for (int y = range.startLocalY(); y <= range.endLocalY(); y++) {
                List<LayerRect> rects = layerRects[y];
                touchedKeys.clear();

                if (rects != null) {
                    for (LayerRect rect : rects) {
                        long key = packRectKey(rect);
                        ActiveRect current = active.get(key);

                        // Пробуем расширить вверх
                        if (current != null
                                && current.startY + current.height == y
                                && current.height < maxTall
                                && (current.startY >> 4) == (y >> 4)) {
                            current.height++;
                        } else {
                            // Не удалось расширить - сбрасываем старый
                            if (current != null) {
                                addMeshShape(shapes, current, minHeight);
                            }
                            // Начинаем новый
                            active.put(key, new ActiveRect(rect));
                        }
                        touchedKeys.add(key);
                    }
                }

                // Сбрасываем те, что прервались (встретили воздух или другой блок)
                Iterator<Long2ObjectMap.Entry<ActiveRect>> iterator = active.long2ObjectEntrySet().iterator();
                while (iterator.hasNext()) {
                    Long2ObjectMap.Entry<ActiveRect> entry = iterator.next();
                    if (!touchedKeys.contains(entry.getLongKey())) {
                        addMeshShape(shapes, entry.getValue(), minHeight);
                        iterator.remove();
                    }
                }
            }
            // Сбрасываем остатки
            for (ActiveRect remaining : active.values()) {
                addMeshShape(shapes, remaining, minHeight);
            }
        } else {
            // Без вертикального мержинга
            for (int y = range.startLocalY(); y <= range.endLocalY(); y++) {
                List<LayerRect> rects = layerRects[y];
                if (rects == null) continue;

                for (LayerRect rect : rects) {
                    ActiveRect wrapper = new ActiveRect(rect);
                    addMeshShape(shapes, wrapper, minHeight);
                }
            }
        }

        // Очистка глобальных буферов для реюза
        for (int i = 0; i < touchedCount; i++) {
            int index = touchedIndices[i];
            profileIndices[index] = EMPTY_PROFILE;
            visited[index] = false;
        }

        return new GreedyMeshData(shapes, range.fullRebuild(), touchedSections);
    }

    private GenerationRange resolveGenerationRange(ChunkSnapshotData snapshot, DirtyChunkRegion dirtyRegion) {
        int sectionsCount = snapshot.sectionsCount();
        int chunkMinSectionY = snapshot.minSectionY();
        int startSectionIndex = 0;
        int endSectionIndex = sectionsCount - 1;
        int minLocalX = 0;
        int maxLocalX = 15;
        int minLocalZ = 0;
        int maxLocalZ = 15;
        boolean fullRebuild = true;

        if (dirtyRegion != null) {
            DirtyChunkRegion expanded = dirtyRegion.expanded(1);
            int startSectionY = Math.max(chunkMinSectionY, expanded.minSectionY());
            int endSectionY = Math.min(chunkMinSectionY + sectionsCount - 1, expanded.maxSectionY());
            startSectionIndex = Math.max(0, startSectionY - chunkMinSectionY);
            endSectionIndex = Math.min(sectionsCount - 1, endSectionY - chunkMinSectionY);
            minLocalX = expanded.minX();
            maxLocalX = expanded.maxX();
            minLocalZ = expanded.minZ();
            maxLocalZ = expanded.maxZ();
            fullRebuild = shouldFallbackToFull(snapshot, dirtyRegion);
            if (fullRebuild) {
                startSectionIndex = 0;
                endSectionIndex = sectionsCount - 1;
                minLocalX = 0;
                maxLocalX = 15;
                minLocalZ = 0;
                maxLocalZ = 15;
            }
        }

        int startLocalY = startSectionIndex << 4;
        int endLocalY = ((endSectionIndex + 1) << 4) - 1;
        return new GenerationRange(startSectionIndex, endSectionIndex, startLocalY, endLocalY, minLocalX, maxLocalX, minLocalZ, maxLocalZ, fullRebuild);
    }

    private boolean shouldFallbackToFull(ChunkSnapshotData snapshot, DirtyChunkRegion dirtyRegion) {
        int chunkMinSectionY = snapshot.minSectionY();
        int chunkMaxSectionY = chunkMinSectionY + snapshot.sectionsCount() - 1;
        int startSectionY = Math.max(chunkMinSectionY, dirtyRegion.minSectionY());
        int endSectionY = Math.min(chunkMaxSectionY, dirtyRegion.maxSectionY());

        for (int sectionY = startSectionY; sectionY <= endSectionY; sectionY++) {
            int sectionMinY = sectionY << 4;
            int sectionMaxY = sectionMinY + 15;
            int minY = Math.max(sectionMinY, dirtyRegion.minY());
            int maxY = Math.min(sectionMaxY, dirtyRegion.maxY());
            if (minY > maxY) {
                continue;
            }
            int width = dirtyRegion.maxX() - dirtyRegion.minX() + 1;
            int depth = dirtyRegion.maxZ() - dirtyRegion.minZ() + 1;
            int sectionHeight = maxY - minY + 1;
            int dirtyBlocks = width * depth * sectionHeight;
            if (dirtyBlocks > (int) Math.floor(4096 * FULL_REBUILD_THRESHOLD)) {
                return true;
            }
        }
        return false;
    }

    private Set<Integer> collectTouchedSections(GenerationRange range, int minSectionY) {
        Set<Integer> touched = new HashSet<>();
        for (int sectionIndex = range.startSectionIndex(); sectionIndex <= range.endSectionIndex(); sectionIndex++) {
            touched.add(minSectionY + sectionIndex);
        }
        return touched;
    }

    // --- Helpers ---

    private void addMeshShape(List<GreedyMeshShape> shapes, ActiveRect rect, int minHeight) {
        PhysicalProfile profile = profileFromIndex(rect.profileIndex);
        if (profile == null) return;

        int worldY = minHeight + rect.startY;
        int maxX = rect.x + rect.width;
        int maxY = worldY + rect.height;
        int maxZ = rect.z + rect.depth;

        // Генерируем треугольники для этого объема
        float[] triangles = generateBoxTriangles(
                profile.boundingBoxes().get(0), // Для мержинга берем первую коробку (она должна быть одна)
                rect.x, worldY, rect.z,
                rect.width, rect.height, rect.depth
        );

        shapes.add(new GreedyMeshShape(
                profile.id(), profile.density(), profile.friction(), profile.restitution(),
                triangles, rect.x, worldY, rect.z, maxX, maxY, maxZ
        ));
    }

    private float[] toTriangles(List<AaBox> boxes, int offsetX, int offsetY, int offsetZ) {
        // Каждая коробка = 12 треугольников * 3 вершины * 3 координаты = 108 флоатов
        float[] vertices = new float[boxes.size() * 108];
        int ptr = 0;

        for (AaBox box : boxes) {
            float[] boxTris = generateBoxTriangles(box, offsetX, offsetY, offsetZ, 1, 1, 1);
            System.arraycopy(boxTris, 0, vertices, ptr, boxTris.length);
            ptr += boxTris.length;
        }
        return vertices;
    }

    /**
     * Генерирует треугольники (12 шт) для коробки.
     * Координаты вершин = box.min/max + смещение чанка + размер merged блока.
     */
    private float[] generateBoxTriangles(AaBox baseBox, int x, int y, int z, int w, int h, int d) {
        Vec3 min = baseBox.getMin();
        Vec3 max = baseBox.getMax();

        // Размеры единичного блока внутри профиля (обычно 0..1)
        // Но так как мы мержим, мы растягиваем эти границы

        // Базовые границы коробки относительно 0,0,0 блока
        float bMinX = min.getX(); float bMinY = min.getY(); float bMinZ = min.getZ();
        float bMaxX = max.getX(); float bMaxY = max.getY(); float bMaxZ = max.getZ();

        // Мы не растягиваем текстуру, мы растягиваем сам объем.
        // Если это merged блок 2x1x1, то он занимает x..x+2.
        // НО! AaBox внутри профиля может быть неполным (напр. slab).
        // Для Greedy Meshing мы предполагаем, что мержим только ПОЛНЫЕ блоки,
        // либо блоки с одинаковым bbox.
        // Если мы мержим 2 блока шириной, то minX берется от первого, maxX от последнего.

        // Абсолютные координаты
        float x1 = x + bMinX;
        float y1 = y + bMinY;
        float z1 = z + bMinZ;

        // width-1 добавляется потому что bMaxX уже включает ширину 1 блока.
        float x2 = x + (w - 1) + bMaxX;
        float y2 = y + (h - 1) + bMaxY;
        float z2 = z + (d - 1) + bMaxZ;

        return new float[] {
                // Front (Z+)
                x1, y1, z2,  x2, y1, z2,  x2, y2, z2,
                x1, y1, z2,  x2, y2, z2,  x1, y2, z2,
                // Back (Z-)
                x1, y1, z1,  x1, y2, z1,  x2, y2, z1,
                x1, y1, z1,  x2, y2, z1,  x2, y1, z1,
                // Right (X+)
                x2, y1, z1,  x2, y2, z1,  x2, y2, z2,
                x2, y1, z1,  x2, y2, z2,  x2, y1, z2,
                // Left (X-)
                x1, y1, z1,  x1, y1, z2,  x1, y2, z2,
                x1, y1, z1,  x1, y2, z2,  x1, y2, z1,
                // Top (Y+)
                x1, y2, z1,  x1, y2, z2,  x2, y2, z2,
                x1, y2, z1,  x2, y2, z2,  x2, y2, z1,
                // Bottom (Y-)
                x1, y1, z1,  x2, y1, z1,  x2, y1, z2,
                x1, y1, z1,  x2, y1, z2,  x1, y1, z2
        };
    }

    private int expandLayerX(short[] profiles, boolean[] visited, short targetIndex, int x, int z) {
        int width = 1;
        while (x + width < 16) {
            int maskIndex = layerIndex(x + width, z);
            if (visited[maskIndex] || profiles[maskIndex] != targetIndex) {
                break;
            }
            width++;
        }
        return width;
    }

    private int expandLayerZ(short[] profiles, boolean[] visited, short targetIndex, int x, int z, int width) {
        int depth = 1;
        while (z + depth < 16) {
            boolean matches = true;
            for (int dx = x; dx < x + width; dx++) {
                int maskIndex = layerIndex(dx, z + depth);
                if (visited[maskIndex] || profiles[maskIndex] != targetIndex) {
                    matches = false;
                    break;
                }
            }
            if (!matches) break;
            depth++;
        }
        return depth;
    }

    private void markLayerVisited(boolean[] visited, int x, int z, int width, int depth) {
        for (int dz = z; dz < z + depth; dz++) {
            for (int dx = x; dx < x + width; dx++) {
                visited[layerIndex(dx, dz)] = true;
            }
        }
    }

    private PhysicalProfile profileFromIndex(short profileIndex) {
        if (profileIndex == EMPTY_PROFILE) return null;
        int index = profileIndex - 1;
        if (index < 0 || index >= profilesById.length) {
            return null;
        }
        return profilesById[index];
    }

    private int flattenIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    private int layerIndex(int x, int z) {
        return (z << 4) | x;
    }

    private long packRectKey(LayerRect rect) {
        long key = rect.x & 0xFL;
        key |= (rect.z & 0xFL) << 4;
        key |= (rect.width & 0x1FL) << 8;
        key |= (rect.depth & 0x1FL) << 13;
        key |= (rect.profileIndex & 0xFFFFL) << 18;
        return key;
    }

    // --- Inner Classes ---

    private static final class LayerRect {
        final int x, z, width, depth, y;
        final short profileIndex;
        LayerRect(int x, int z, int width, int depth, int y, short profileIndex) {
            this.x = x; this.z = z; this.width = width; this.depth = depth; this.y = y;
            this.profileIndex = profileIndex;
        }
    }

    private static final class ActiveRect {
        final int x, z, width, depth;
        final short profileIndex;
        final int startY;
        int height;
        ActiveRect(LayerRect rect) {
            this.x = rect.x; this.z = rect.z;
            this.width = rect.width; this.depth = rect.depth;
            this.profileIndex = rect.profileIndex;
            this.startY = rect.y;
            this.height = 1;
        }
    }

    private static final class WorkingBuffers {
        short[] profileIndices = new short[0];
        boolean[] visited = new boolean[0];
        int[] touchedIndices = new int[0];
        int[] complexIndices = new int[0];
        short[] layerProfiles = new short[256];
        boolean[] layerVisited = new boolean[256];
        Long2ObjectMap<ActiveRect> activeRects = new Long2ObjectOpenHashMap<>();
        LongSet touchedKeys = new LongOpenHashSet();

        void ensureCapacity(int height) {
            int total = 16 * height * 16;
            if (profileIndices.length < total) {
                profileIndices = new short[total];
                visited = new boolean[total];
                touchedIndices = new int[total];
                complexIndices = new int[total];
            }
        }
    }

    private record ProfileMappings(short[] materialToProfileId, PhysicalProfile[] profilesById) {
    }

    private record GenerationRange(int startSectionIndex,
                                   int endSectionIndex,
                                   int startLocalY,
                                   int endLocalY,
                                   int minLocalX,
                                   int maxLocalX,
                                   int minLocalZ,
                                   int maxLocalZ,
                                   boolean fullRebuild) {
    }
}
