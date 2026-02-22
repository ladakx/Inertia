package com.ladakx.inertia.physics.world.buoyancy;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.RMat44;
import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Objects;

final class BuoyancyBroadPhase {
    private static final int MAX_VERTICAL_SCAN = 16;
    private final World world;

    BuoyancyBroadPhase(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    void findPotentialFluidContacts(Collection<AbstractPhysicsBody> bodies, BuoyancyDataStore outStore) {
        Objects.requireNonNull(bodies, "bodies");
        Objects.requireNonNull(outStore, "outStore");

        for (AbstractPhysicsBody physicsBody : bodies) {
            if (physicsBody == null || !physicsBody.isValid() || physicsBody.getMotionType() == MotionType.STATIC) {
                continue;
            }

            Body body = physicsBody.getBody();
            if (body == null || !body.isActive()) {
                continue;
            }

            AaBox bounds = body.getShape().getWorldSpaceBounds(
                    RMat44.sRotationTranslation(body.getRotation(), body.getPosition()),
                    Vec3.sReplicate(1.0f)
            );

            Vec3 min = bounds.getMin();
            Vec3 max = bounds.getMax();

            int minX = (int) Math.floor(min.getX());
            int maxX = (int) Math.floor(max.getX());
            int minY = (int) Math.floor(min.getY());
            int maxY = (int) Math.floor(max.getY());
            int minZ = (int) Math.floor(min.getZ());
            int maxZ = (int) Math.floor(max.getZ());

            float totalSurfaceHeight = 0.0f;
            float sumX = 0.0f;
            float sumZ = 0.0f;
            int fluidColumns = 0;
            int totalColumns = Math.max(1, (maxX - minX + 1) * (maxZ - minZ + 1));
            FluidType detectedType = null;

            for (int x = minX; x <= maxX; ++x) {
                for (int z = minZ; z <= maxZ; ++z) {
                    float foundHeight = -1.0f;
                    Block top = world.getBlockAt(x, maxY, z);

                    if (isFluid(top.getType())) {
                        foundHeight = findSurfaceUpwards(x, maxY, z);
                        if (detectedType == null) {
                            detectedType = toFluidType(top.getType());
                        }
                    } else {
                        for (int y = maxY - 1; y >= minY; --y) {
                            Block block = world.getBlockAt(x, y, z);
                            if (isFluid(block.getType())) {
                                foundHeight = y + getFluidHeight(block);
                                if (detectedType == null) {
                                    detectedType = toFluidType(block.getType());
                                }
                                break;
                            }
                        }
                    }

                    if (foundHeight >= 0.0f) {
                        totalSurfaceHeight += foundHeight;
                        sumX += x + 0.5f;
                        sumZ += z + 0.5f;
                        fluidColumns++;
                    }
                }
            }

            if (fluidColumns <= 0 || detectedType == null) {
                continue;
            }

            float avgHeight = totalSurfaceHeight / fluidColumns;
            if (avgHeight <= min.getY()) {
                continue;
            }

            float areaFraction = (float) fluidColumns / (float) totalColumns;
            float centerX = sumX / fluidColumns;
            float centerZ = sumZ / fluidColumns;

            Vector3f flow = computeFlow((int) Math.floor(centerX), (int) Math.floor(avgHeight - 0.5f), (int) Math.floor(centerZ));

            outStore.add(
                    body.getId(),
                    avgHeight,
                    detectedType,
                    areaFraction,
                    centerX,
                    centerZ,
                    flow.x,
                    flow.y,
                    flow.z
            );
        }
    }

    private float findSurfaceUpwards(int x, int startY, int z) {
        for (int y = startY + 1; y <= startY + MAX_VERTICAL_SCAN; ++y) {
            Block block = world.getBlockAt(x, y, z);
            if (!isFluid(block.getType())) {
                Block below = world.getBlockAt(x, y - 1, z);
                return (y - 1) + getFluidHeight(below);
            }
        }
        return startY + 1.0f;
    }

    private Vector3f computeFlow(int x, int y, int z) {
        Block center = world.getBlockAt(x, y, z);
        Material centerMaterial = center.getType();
        if (!isFluid(centerMaterial)) {
            return new Vector3f();
        }

        float centerHeight = getFluidHeight(center);
        float dx = 0.0f;
        float dz = 0.0f;

        Block east = center.getRelative(BlockFace.EAST);
        Block west = center.getRelative(BlockFace.WEST);
        Block south = center.getRelative(BlockFace.SOUTH);
        Block north = center.getRelative(BlockFace.NORTH);

        dx += flowDelta(centerMaterial, centerHeight, east);
        dx -= flowDelta(centerMaterial, centerHeight, west);
        dz += flowDelta(centerMaterial, centerHeight, south);
        dz -= flowDelta(centerMaterial, centerHeight, north);

        Vector3f flow = new Vector3f(dx, 0.0f, dz);
        if (flow.lengthSquared() > 1.0e-6f) {
            flow.normalize();
        }
        return flow;
    }

    private float flowDelta(Material centerMaterial, float centerHeight, Block neighbor) {
        if (neighbor.getType() != centerMaterial) {
            return 0.0f;
        }
        return centerHeight - getFluidHeight(neighbor);
    }

    private float getFluidHeight(Block block) {
        if (!(block.getBlockData() instanceof Levelled levelled)) {
            return 1.0f;
        }
        int max = levelled.getMaximumLevel();
        int level = Math.min(max, Math.max(0, levelled.getLevel()));
        return 1.0f - ((float) level / (float) Math.max(1, max));
    }

    private boolean isFluid(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }

    private FluidType toFluidType(Material material) {
        if (material == Material.LAVA) {
            return FluidType.LAVA;
        }
        return FluidType.WATER;
    }
}
