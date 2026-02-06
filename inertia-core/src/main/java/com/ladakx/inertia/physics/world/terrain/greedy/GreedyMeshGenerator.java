package com.ladakx.inertia.physics.world.terrain.greedy;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.configuration.dto.BlocksConfig;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.infrastructure.nms.jolt.JoltTools;
import com.ladakx.inertia.physics.world.terrain.PhysicsGenerator;
import com.ladakx.inertia.physics.world.terrain.profile.PhysicalProfile;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GreedyMeshGenerator implements PhysicsGenerator<GreedyMeshData> {

    private final BlocksConfig blocksConfig;
    private final JoltTools joltTools;
    private final WorldsConfig.GreedyMeshingSettings settings;
    private final PhysicalProfile[] materialProfiles;
    private static final ThreadLocal<WorkingBuffers> BUFFERS = ThreadLocal.withInitial(WorkingBuffers::new);
    private static final short EMPTY_PROFILE = 0;

    public GreedyMeshGenerator(BlocksConfig blocksConfig, JoltTools joltTools, WorldsConfig.GreedyMeshingSettings settings) {
        this.blocksConfig = Objects.requireNonNull(blocksConfig, "blocksConfig");
        this.joltTools = Objects.requireNonNull(joltTools, "joltTools");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.materialProfiles = buildMaterialProfiles();
    }

    private PhysicalProfile[] buildMaterialProfiles() {
        Material[] materials = Material.values();
        PhysicalProfile[] profiles = new PhysicalProfile[materials.length];
        for (Material material : materials) {
            blocksConfig.find(material).ifPresentOrElse(
                    profile -> profiles[material.ordinal()] = profile,
                    () -> {
                        try {
                            BlockState state = joltTools.createBlockState(material);
                            List<AaBox> boxes = joltTools.boundingBoxes(state);
                            if (boxes != null && !boxes.isEmpty()) {
                                profiles[material.ordinal()] = new PhysicalProfile(material.name(), 0.5f, 0.6f, 0.2f, boxes);
                            }
                        } catch (Exception ignored) {
                        }
                    }
            );
        }
        return profiles;
    }

    @Override
    public GreedyMeshData generate(Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        int minSectionY = joltTools.getMinSectionY(chunk);
        int sectionsCount = joltTools.getSectionsCount(chunk);
        int minHeight = minSectionY << 4;
        int height = sectionsCount << 4;

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

        for (int sectionIndex = 0; sectionIndex < sectionsCount; sectionIndex++) {
            int sectionY = minSectionY + sectionIndex;
            if (joltTools.hasOnlyAir(chunk, sectionY)) {
                continue;
            }

            int baseY = sectionIndex << 4;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        int worldIndexY = baseY + y;
                        Material material = joltTools.getMaterial(chunk, sectionY, x, y, z);

                        if (material == null || material == Material.AIR || material.isAir()) {
                            continue;
                        }

                        PhysicalProfile profile = materialProfiles[material.ordinal()];
                        if (profile != null && !profile.boundingBoxes().isEmpty()) {
                            int index = flattenIndex(x, worldIndexY, z);
                            if (profileIndices[index] == EMPTY_PROFILE) {
                                touchedIndices[touchedCount++] = index;
                            }
                            profileIndices[index] = (short) (material.ordinal() + 1);
                            if (profile.boundingBoxes().size() > 1) {
                                complexIndices[complexCount++] = index;
                            }
                        }
                    }
                }
            }
        }

        // --- Standard Greedy Meshing Logic ---

        // Pass 1: Complex shapes (do not merge)
        for (int i = 0; i < complexCount; i++) {
            int index = complexIndices[i];
            PhysicalProfile profile = profileFromIndex(profileIndices[index]);
            if (profile == null) {
                continue;
            }
            int x = index & 0xF;
            int z = (index >> 4) & 0xF;
            int y = index >> 8;
            int worldY = minHeight + y;
            List<SerializedBoundingBox> boxes = toAbsoluteBoxes(profile.boundingBoxes(), x, worldY, z, x + 1, worldY + 1, z + 1);
            shapes.add(new GreedyMeshShape(
                    profile.id(), profile.density(), profile.friction(), profile.restitution(),
                    boxes, x, worldY, z, x + 1, worldY + 1, z + 1
            ));
            visited[index] = true;
        }

        // Pass 2: Simple shapes (greedy 2D mask per layer)
        @SuppressWarnings("unchecked")
        List<LayerRect>[] layerRects = new List[height];
        for (int y = 0; y < height; y++) {
            Arrays.fill(layerProfiles, EMPTY_PROFILE);
            Arrays.fill(layerVisited, false);

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int index = flattenIndex(x, y, z);
                    if (visited[index]) {
                        continue;
                    }
                    short profileIndex = profileIndices[index];
                    if (profileIndex == EMPTY_PROFILE) {
                        continue;
                    }
                    layerProfiles[layerIndex(x, z)] = profileIndex;
                }
            }

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int maskIndex = layerIndex(x, z);
                    if (layerVisited[maskIndex]) {
                        continue;
                    }
                    short profileIndex = layerProfiles[maskIndex];
                    if (profileIndex == EMPTY_PROFILE) {
                        continue;
                    }
                    PhysicalProfile profile = profileFromIndex(profileIndex);
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

        if (settings.verticalMerging()) {
            int maxTall = settings.maxVerticalSize();
            Map<RectKey, ActiveRect> active = new HashMap<>();
            for (int y = 0; y < height; y++) {
                List<LayerRect> rects = layerRects[y];
                Set<RectKey> touchedKeys = new HashSet<>();
                if (rects != null) {
                    for (LayerRect rect : rects) {
                        RectKey key = new RectKey(rect);
                        ActiveRect current = active.get(key);
                        if (current != null && current.startY + current.height == y && current.height < maxTall) {
                            current.height++;
                        } else {
                            if (current != null) {
                                addMergedShape(shapes, current, minHeight);
                            }
                            active.put(key, new ActiveRect(rect));
                        }
                        touchedKeys.add(key);
                    }
                }

                Iterator<Map.Entry<RectKey, ActiveRect>> iterator = active.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<RectKey, ActiveRect> entry = iterator.next();
                    if (!touchedKeys.contains(entry.getKey())) {
                        addMergedShape(shapes, entry.getValue(), minHeight);
                        iterator.remove();
                    }
                }
            }

            for (ActiveRect remaining : active.values()) {
                addMergedShape(shapes, remaining, minHeight);
            }
        } else {
            for (int y = 0; y < height; y++) {
                List<LayerRect> rects = layerRects[y];
                if (rects == null) {
                    continue;
                }
                for (LayerRect rect : rects) {
                    PhysicalProfile profile = profileFromIndex(rect.profileIndex);
                    if (profile == null) {
                        continue;
                    }
                    int worldY = minHeight + rect.y;
                    int maxX = rect.x + rect.width;
                    int maxZ = rect.z + rect.depth;
                    List<SerializedBoundingBox> boxes = toAbsoluteBoxes(profile.boundingBoxes(), rect.x, worldY, rect.z, maxX, worldY + 1, maxZ);
                    shapes.add(new GreedyMeshShape(
                            profile.id(), profile.density(), profile.friction(), profile.restitution(),
                            boxes, rect.x, worldY, rect.z, maxX, worldY + 1, maxZ
                    ));
                }
            }
        }

        for (int i = 0; i < touchedCount; i++) {
            int index = touchedIndices[i];
            profileIndices[index] = EMPTY_PROFILE;
            visited[index] = false;
        }

        return new GreedyMeshData(shapes);
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
            if (!matches) {
                break;
            }
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
        if (profileIndex == EMPTY_PROFILE) {
            return null;
        }
        return materialProfiles[profileIndex - 1];
    }

    private int flattenIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    private int layerIndex(int x, int z) {
        return (z << 4) | x;
    }

    private void addMergedShape(List<GreedyMeshShape> shapes, ActiveRect rect, int minHeight) {
        PhysicalProfile profile = profileFromIndex(rect.profileIndex);
        if (profile == null) {
            return;
        }
        int worldY = minHeight + rect.startY;
        int maxX = rect.x + rect.width;
        int maxZ = rect.z + rect.depth;
        int maxY = worldY + rect.height;
        List<SerializedBoundingBox> boxes = toAbsoluteBoxes(profile.boundingBoxes(), rect.x, worldY, rect.z, maxX, maxY, maxZ);
        shapes.add(new GreedyMeshShape(
                profile.id(), profile.density(), profile.friction(), profile.restitution(),
                boxes, rect.x, worldY, rect.z, maxX, maxY, maxZ
        ));
    }

    private static final class LayerRect {
        private final int x;
        private final int z;
        private final int width;
        private final int depth;
        private final int y;
        private final short profileIndex;

        private LayerRect(int x, int z, int width, int depth, int y, short profileIndex) {
            this.x = x;
            this.z = z;
            this.width = width;
            this.depth = depth;
            this.y = y;
            this.profileIndex = profileIndex;
        }
    }

    private static final class RectKey {
        private final int x;
        private final int z;
        private final int width;
        private final int depth;
        private final short profileIndex;

        private RectKey(LayerRect rect) {
            this.x = rect.x;
            this.z = rect.z;
            this.width = rect.width;
            this.depth = rect.depth;
            this.profileIndex = rect.profileIndex;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RectKey other)) {
                return false;
            }
            return x == other.x
                    && z == other.z
                    && width == other.width
                    && depth == other.depth
                    && profileIndex == other.profileIndex;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(x);
            result = 31 * result + Integer.hashCode(z);
            result = 31 * result + Integer.hashCode(width);
            result = 31 * result + Integer.hashCode(depth);
            result = 31 * result + Short.hashCode(profileIndex);
            return result;
        }
    }

    private static final class ActiveRect {
        private final int x;
        private final int z;
        private final int width;
        private final int depth;
        private final short profileIndex;
        private final int startY;
        private int height;

        private ActiveRect(LayerRect rect) {
            this.x = rect.x;
            this.z = rect.z;
            this.width = rect.width;
            this.depth = rect.depth;
            this.profileIndex = rect.profileIndex;
            this.startY = rect.y;
            this.height = 1;
        }
    }

    private List<SerializedBoundingBox> toAbsoluteBoxes(List<AaBox> localBoxes,
                                                        int minX,
                                                        int minY,
                                                        int minZ,
                                                        int maxX,
                                                        int maxY,
                                                        int maxZ) {
        int maxBlockX = maxX - 1;
        int maxBlockY = maxY - 1;
        int maxBlockZ = maxZ - 1;

        List<SerializedBoundingBox> boxes = new ArrayList<>(localBoxes.size());
        for (AaBox box : localBoxes) {
            Vec3 localMin = box.getMin();
            Vec3 localMax = box.getMax();
            boxes.add(new SerializedBoundingBox(
                    minX + localMin.getX(),
                    minY + localMin.getY(),
                    minZ + localMin.getZ(),
                    maxBlockX + localMax.getX(),
                    maxBlockY + localMax.getY(),
                    maxBlockZ + localMax.getZ()
            ));
        }
        return boxes;
    }

    private static final class WorkingBuffers {
        private short[] profileIndices = new short[0];
        private boolean[] visited = new boolean[0];
        private int[] touchedIndices = new int[0];
        private int[] complexIndices = new int[0];
        private short[] layerProfiles = new short[0];
        private boolean[] layerVisited = new boolean[0];

        private void ensureCapacity(int height) {
            int total = 16 * height * 16;
            if (profileIndices.length < total) {
                profileIndices = new short[total];
            }
            if (visited.length < total) {
                visited = new boolean[total];
            }
            if (touchedIndices.length < total) {
                touchedIndices = new int[total];
            }
            if (complexIndices.length < total) {
                complexIndices = new int[total];
            }
            if (layerProfiles.length < 256) {
                layerProfiles = new short[256];
            }
            if (layerVisited.length < 256) {
                layerVisited = new boolean[256];
            }
        }
    }
}
