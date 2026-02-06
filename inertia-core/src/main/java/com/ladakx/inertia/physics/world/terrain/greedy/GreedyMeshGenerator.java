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
import java.util.List;
import java.util.Objects;

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
        int[] simpleIndices = buffers.simpleIndices;
        int[] complexIndices = buffers.complexIndices;
        int touchedCount = 0;
        int simpleCount = 0;
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
                            } else {
                                simpleIndices[simpleCount++] = index;
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

        // Pass 2: Simple shapes (merge)
        for (int i = 0; i < simpleCount; i++) {
            int index = simpleIndices[i];
            if (visited[index]) {
                continue;
            }
            short profileIndex = profileIndices[index];
            PhysicalProfile profile = profileFromIndex(profileIndex);
            if (profile == null) {
                continue;
            }

            int x = index & 0xF;
            int z = (index >> 4) & 0xF;
            int y = index >> 8;

            int width = expandX(profileIndices, visited, profileIndex, profile, x, y, z);
            int depth = expandZ(profileIndices, visited, profileIndex, profile, x, y, z, width);
            int tall = expandY(profileIndices, visited, profileIndex, profile, x, y, z, width, depth, height);

            int worldY = minHeight + y;
            int maxX = x + width;
            int maxY = worldY + tall;
            int maxZ = z + depth;

            List<SerializedBoundingBox> boxes = toAbsoluteBoxes(profile.boundingBoxes(), x, worldY, z, maxX, maxY, maxZ);

            shapes.add(new GreedyMeshShape(
                    profile.id(), profile.density(), profile.friction(), profile.restitution(),
                    boxes, x, worldY, z, maxX, maxY, maxZ
            ));

            for (int dx = x; dx < maxX; dx++) {
                for (int dz = z; dz < maxZ; dz++) {
                    for (int dy = y; dy < y + tall; dy++) {
                        visited[flattenIndex(dx, dy, dz)] = true;
                    }
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

    private int expandX(short[] profiles, boolean[] visited, short targetIndex, PhysicalProfile target,
                        int x, int y, int z) {
        int width = 1;
        while (x + width < 16) {
            int index = flattenIndex(x + width, y, z);
            if (!canMerge(profiles, visited, index, targetIndex, target)) {
                break;
            }
            width++;
        }
        return width;
    }

    private int expandZ(short[] profiles, boolean[] visited, short targetIndex, PhysicalProfile target,
                        int x, int y, int z, int width) {
        int depth = 1;
        while (z + depth < 16) {
            boolean matches = true;
            for (int dx = x; dx < x + width; dx++) {
                int index = flattenIndex(dx, y, z + depth);
                if (!canMerge(profiles, visited, index, targetIndex, target)) {
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

    private int expandY(short[] profiles, boolean[] visited, short targetIndex, PhysicalProfile target,
                        int x, int y, int z, int width, int depth, int heightLimit) {

        if (!settings.verticalMerging()) {
            return 1;
        }

        int maxTall = settings.maxVerticalSize();
        int tall = 1;

        while (y + tall < heightLimit && tall < maxTall) {
            boolean matches = true;
            for (int dx = x; dx < x + width; dx++) {
                for (int dz = z; dz < z + depth; dz++) {
                    int index = flattenIndex(dx, y + tall, dz);
                    if (!canMerge(profiles, visited, index, targetIndex, target)) {
                        matches = false;
                        break;
                    }
                }
                if (!matches) {
                    break;
                }
            }
            if (!matches) {
                break;
            }
            tall++;
        }
        return tall;
    }

    private boolean canMerge(short[] profiles, boolean[] visited, int index, short targetIndex, PhysicalProfile target) {
        if (visited[index]) {
            return false;
        }
        short profileIndex = profiles[index];
        if (profileIndex == EMPTY_PROFILE) {
            return false;
        }
        if (profileIndex == targetIndex) {
            return true;
        }
        PhysicalProfile profile = profileFromIndex(profileIndex);
        if (profile == null) {
            return false;
        }
        return profile == target || (profile.id().equals(target.id())
                && Float.compare(profile.density(), target.density()) == 0
                && Float.compare(profile.friction(), target.friction()) == 0
                && Float.compare(profile.restitution(), target.restitution()) == 0);
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
        private int[] simpleIndices = new int[0];
        private int[] complexIndices = new int[0];

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
            if (simpleIndices.length < total) {
                simpleIndices = new int[total];
            }
            if (complexIndices.length < total) {
                complexIndices = new int[total];
            }
        }
    }
}
