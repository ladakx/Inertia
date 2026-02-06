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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class GreedyMeshGenerator implements PhysicsGenerator<GreedyMeshData> {

    private static final int CHUNK_SIZE = 16;
    private static final int X_MASK = CHUNK_SIZE - 1;
    private static final int Z_SHIFT = 4;
    private static final int Y_SHIFT = 8;
    private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);

    private final BlocksConfig blocksConfig;
    private final JoltTools joltTools;
    private final WorldsConfig.GreedyMeshingSettings settings;

    public GreedyMeshGenerator(BlocksConfig blocksConfig, JoltTools joltTools, WorldsConfig.GreedyMeshingSettings settings) {
        this.blocksConfig = Objects.requireNonNull(blocksConfig, "blocksConfig");
        this.joltTools = Objects.requireNonNull(joltTools, "joltTools");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public GreedyMeshData generate(Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        int minSectionY = joltTools.getMinSectionY(chunk);
        int sectionsCount = joltTools.getSectionsCount(chunk);
        int minHeight = minSectionY << 4;
        int height = sectionsCount << 4;

        int totalCells = CHUNK_SIZE * height * CHUNK_SIZE;
        Workspace workspace = WORKSPACE.get();
        workspace.reset(totalCells);
        int[] profileIndices = workspace.profileIndices;
        BitSet visited = workspace.visited;
        int[] writtenPositions = workspace.writtenPositions;
        int[] simplePositions = workspace.simplePositions;
        List<GreedyMeshShape> shapes = new ArrayList<>();

        java.util.Map<Material, java.util.Optional<PhysicalProfile>> materialCache = new java.util.HashMap<>();
        java.util.Map<PhysicalProfile, Integer> profileIndexCache = new java.util.IdentityHashMap<>();

        for (int sectionIndex = 0; sectionIndex < sectionsCount; sectionIndex++) {
            int sectionY = minSectionY + sectionIndex;
            if (joltTools.hasOnlyAir(chunk, sectionY)) {
                continue;
            }

            int baseY = sectionIndex << 4;
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    for (int y = 0; y < CHUNK_SIZE; y++) {
                        int worldIndexY = baseY + y;
                        Material material = joltTools.getMaterial(chunk, sectionY, x, y, z);

                        if (material == null || material == Material.AIR || material.isAir()) {
                            continue;
                        }

                        Optional<PhysicalProfile> profileOpt = blocksConfig.find(material);

                        if (profileOpt.isEmpty()) {
                            profileOpt = materialCache.computeIfAbsent(material, mat -> {
                                try {
                                    org.bukkit.block.BlockState state = joltTools.createBlockState(mat);
                                    List<AaBox> boxes = joltTools.boundingBoxes(state);

                                    if (boxes != null && !boxes.isEmpty()) {
                                        return Optional.of(new PhysicalProfile(mat.name(), 0.5f, 0.6f, 0.2f, boxes));
                                    }
                                } catch (Exception ignored) {
                                }
                                return Optional.empty();
                            });
                        }

                        if (profileOpt.isPresent() && !profileOpt.get().boundingBoxes().isEmpty()) {
                            PhysicalProfile profile = profileOpt.get();
                            int profileIndex = profileIndexCache.computeIfAbsent(profile, ignored -> workspace.registerProfile(profile));
                            int position = index(x, worldIndexY, z);
                            if (profileIndices[position] == 0) {
                                profileIndices[position] = profileIndex;
                                writtenPositions[workspace.writtenCount++] = position;
                            }
                            if (profile.boundingBoxes().size() > 1) {
                                int worldY = minHeight + worldIndexY;
                                List<SerializedBoundingBox> boxes = toAbsoluteBoxes(
                                        profile.boundingBoxes(), x, worldY, z, x + 1, worldY + 1, z + 1
                                );
                                shapes.add(new GreedyMeshShape(
                                        profile.id(), profile.density(), profile.friction(), profile.restitution(),
                                        boxes, x, worldY, z, x + 1, worldY + 1, z + 1
                                ));
                                workspace.markVisited(position);
                            } else {
                                simplePositions[workspace.simpleCount++] = position;
                            }
                        }
                    }
                }
            }
        }

        // Pass 2: Simple shapes (merge).
        // We iterate only the filled simple positions, skipping the "air" cells that previously required full scans.
        PhysicalProfile[] profileLookup = workspace.profileLookup;
        int simpleCount = workspace.simpleCount;
        for (int i = 0; i < simpleCount; i++) {
            int position = simplePositions[i];
            if (visited.get(position)) {
                continue;
            }

            int profileIndex = profileIndices[position];
            if (profileIndex == 0) {
                continue;
            }

            int x = position & X_MASK;
            int z = (position >> Z_SHIFT) & X_MASK;
            int y = position >> Y_SHIFT;

            PhysicalProfile profile = profileLookup[profileIndex];
            if (profile == null || profile.boundingBoxes().size() != 1) {
                continue;
            }

            int width = expandX(profileIndices, visited, profileIndex, profile, profileLookup, x, y, z);
            int depth = expandZ(profileIndices, visited, profileIndex, profile, profileLookup, x, y, z, width);
            int tall = expandY(profileIndices, visited, profileIndex, profile, profileLookup, x, y, z, width, depth, height);

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
                        workspace.markVisited(index(dx, dy, dz));
                    }
                }
            }
        }

        return new GreedyMeshData(shapes);
    }

    private int expandX(int[] profileIndices, BitSet visited, int targetIndex, PhysicalProfile target,
                        PhysicalProfile[] profileLookup, int x, int y, int z) {
        int width = 1;
        while (x + width < CHUNK_SIZE) {
            int position = index(x + width, y, z);
            if (!canMerge(profileIndices[position], visited.get(position), targetIndex, target, profileLookup)) {
                break;
            }
            width++;
        }
        return width;
    }

    private int expandZ(int[] profileIndices, BitSet visited, int targetIndex, PhysicalProfile target,
                        PhysicalProfile[] profileLookup, int x, int y, int z, int width) {
        int depth = 1;
        while (z + depth < CHUNK_SIZE) {
            boolean matches = true;
            for (int dx = x; dx < x + width; dx++) {
                int position = index(dx, y, z + depth);
                if (!canMerge(profileIndices[position], visited.get(position), targetIndex, target, profileLookup)) {
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

    private int expandY(int[] profileIndices, BitSet visited, int targetIndex, PhysicalProfile target,
                        PhysicalProfile[] profileLookup, int x, int y, int z, int width, int depth, int height) {

        if (!settings.verticalMerging()) {
            return 1;
        }

        int maxTall = settings.maxVerticalSize();
        int tall = 1;

        while (y + tall < height && tall < maxTall) {
            boolean matches = true;
            for (int dx = x; dx < x + width; dx++) {
                for (int dz = z; dz < z + depth; dz++) {
                    int position = index(dx, y + tall, dz);
                    if (!canMerge(profileIndices[position], visited.get(position), targetIndex, target, profileLookup)) {
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

    private boolean canMerge(int profileIndex, boolean visited, int targetIndex, PhysicalProfile target,
                             PhysicalProfile[] profileLookup) {
        if (visited || profileIndex == 0) {
            return false;
        }
        if (profileIndex == targetIndex) {
            return true;
        }
        PhysicalProfile profile = profileLookup[profileIndex];
        return profile != null && profile.id().equals(target.id())
                && Float.compare(profile.density(), target.density()) == 0
                && Float.compare(profile.friction(), target.friction()) == 0
                && Float.compare(profile.restitution(), target.restitution()) == 0;
    }

    private static int index(int x, int y, int z) {
        return x + (z << Z_SHIFT) + (y << Y_SHIFT);
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

    private static final class Workspace {
        private int[] profileIndices;
        private int[] writtenPositions;
        private int[] simplePositions;
        private int[] visitedPositions;
        private PhysicalProfile[] profileLookup;
        private final BitSet visited = new BitSet();
        private int writtenCount;
        private int simpleCount;
        private int visitedCount;
        private int profileCount;

        private void reset(int totalSize) {
            if (profileIndices != null) {
                for (int i = 0; i < writtenCount; i++) {
                    profileIndices[writtenPositions[i]] = 0;
                }
            }
            if (visitedCount > 0) {
                for (int i = 0; i < visitedCount; i++) {
                    visited.clear(visitedPositions[i]);
                }
            }
            writtenCount = 0;
            simpleCount = 0;
            visitedCount = 0;
            profileCount = 0;
            ensureCapacity(totalSize);
        }

        private void ensureCapacity(int totalSize) {
            if (profileIndices == null || profileIndices.length < totalSize) {
                profileIndices = new int[totalSize];
            }
            if (writtenPositions == null || writtenPositions.length < totalSize) {
                writtenPositions = new int[totalSize];
            }
            if (simplePositions == null || simplePositions.length < totalSize) {
                simplePositions = new int[totalSize];
            }
            if (visitedPositions == null || visitedPositions.length < totalSize) {
                visitedPositions = new int[totalSize];
            }
            if (profileLookup == null || profileLookup.length < 64) {
                profileLookup = new PhysicalProfile[64];
            }
        }

        private int registerProfile(PhysicalProfile profile) {
            if (profileCount + 1 >= profileLookup.length) {
                PhysicalProfile[] next = new PhysicalProfile[profileLookup.length * 2];
                System.arraycopy(profileLookup, 0, next, 0, profileLookup.length);
                profileLookup = next;
            }
            profileLookup[++profileCount] = profile;
            return profileCount;
        }

        private void markVisited(int position) {
            if (!visited.get(position)) {
                visited.set(position);
                visitedPositions[visitedCount++] = position;
            }
        }
    }
}
