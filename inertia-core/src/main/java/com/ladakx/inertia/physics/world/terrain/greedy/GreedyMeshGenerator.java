package com.ladakx.inertia.physics.world.terrain.greedy;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.configuration.dto.BlocksConfig;
import com.ladakx.inertia.physics.world.terrain.PhysicsGenerator;
import com.ladakx.inertia.physics.world.terrain.profile.PhysicalProfile;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class GreedyMeshGenerator implements PhysicsGenerator<GreedyMeshData> {

    private final BlocksConfig blocksConfig;

    public GreedyMeshGenerator(BlocksConfig blocksConfig) {
        this.blocksConfig = Objects.requireNonNull(blocksConfig, "blocksConfig");
    }

    @Override
    public GreedyMeshData generate(ChunkSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        int minHeight = snapshot.getMinHeight();
        int maxHeight = snapshot.getMaxHeight();
        int height = maxHeight - minHeight;

        PhysicalProfile[][][] profiles = new PhysicalProfile[16][height][16];
        boolean[][][] visited = new boolean[16][height][16];
        List<GreedyMeshShape> shapes = new ArrayList<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    int worldY = minHeight + y;
                    Material material = snapshot.getBlockType(x, worldY, z);
                    Optional<PhysicalProfile> profile = blocksConfig.find(material);
                    if (profile.isPresent() && !profile.get().boundingBoxes().isEmpty()) {
                        profiles[x][y][z] = profile.get();
                    }
                }
            }
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    PhysicalProfile profile = profiles[x][y][z];
                    if (profile == null || profile.boundingBoxes().size() <= 1) {
                        continue;
                    }
                    int worldY = minHeight + y;
                    List<SerializedBoundingBox> boxes = toAbsoluteBoxes(profile.boundingBoxes(), x, worldY, z, x + 1, worldY + 1, z + 1);
                    shapes.add(new GreedyMeshShape(
                            profile.id(),
                            profile.density(),
                            profile.friction(),
                            profile.restitution(),
                            boxes,
                            x,
                            worldY,
                            z,
                            x + 1,
                            worldY + 1,
                            z + 1
                    ));
                    visited[x][y][z] = true;
                }
            }
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    PhysicalProfile profile = profiles[x][y][z];
                    if (profile == null || visited[x][y][z] || profile.boundingBoxes().size() != 1) {
                        continue;
                    }

                    int width = expandX(profiles, visited, profile, x, y, z);
                    int depth = expandZ(profiles, visited, profile, x, y, z, width);
                    int tall = expandY(profiles, visited, profile, x, y, z, width, depth);

                    int worldY = minHeight + y;
                    int maxX = x + width;
                    int maxY = worldY + tall;
                    int maxZ = z + depth;

                    List<SerializedBoundingBox> boxes = toAbsoluteBoxes(profile.boundingBoxes(), x, worldY, z, maxX, maxY, maxZ);
                    shapes.add(new GreedyMeshShape(
                            profile.id(),
                            profile.density(),
                            profile.friction(),
                            profile.restitution(),
                            boxes,
                            x,
                            worldY,
                            z,
                            maxX,
                            maxY,
                            maxZ
                    ));

                    for (int dx = x; dx < maxX; dx++) {
                        for (int dz = z; dz < maxZ; dz++) {
                            for (int dy = y; dy < y + tall; dy++) {
                                visited[dx][dy][dz] = true;
                            }
                        }
                    }
                }
            }
        }

        return new GreedyMeshData(shapes);
    }

    private int expandX(PhysicalProfile[][][] profiles, boolean[][][] visited, PhysicalProfile target, int x, int y, int z) {
        int width = 1;
        while (x + width < 16) {
            if (!canMerge(profiles[x + width][y][z], visited[x + width][y][z], target)) {
                break;
            }
            width++;
        }
        return width;
    }

    private int expandZ(PhysicalProfile[][][] profiles, boolean[][][] visited, PhysicalProfile target, int x, int y, int z, int width) {
        int depth = 1;
        while (z + depth < 16) {
            boolean matches = true;
            for (int dx = x; dx < x + width; dx++) {
                if (!canMerge(profiles[dx][y][z + depth], visited[dx][y][z + depth], target)) {
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

    private int expandY(PhysicalProfile[][][] profiles, boolean[][][] visited, PhysicalProfile target,
                        int x, int y, int z, int width, int depth) {
        int heightLimit = profiles[0].length;
        int tall = 1;
        while (y + tall < heightLimit) {
            boolean matches = true;
            for (int dx = x; dx < x + width; dx++) {
                for (int dz = z; dz < z + depth; dz++) {
                    if (!canMerge(profiles[dx][y + tall][dz], visited[dx][y + tall][dz], target)) {
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

    private boolean canMerge(PhysicalProfile profile, boolean visited, PhysicalProfile target) {
        if (visited || profile == null) {
            return false;
        }
        return profile == target || (profile.id().equals(target.id()) && profile.boundingBoxes().equals(target.boundingBoxes()));
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
}
