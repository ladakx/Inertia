package com.ladakx.inertia.physics.world.managers;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.physics.engine.PhysicsLayers;
import com.ladakx.inertia.physics.world.PhysicsWorld;

import java.util.ArrayList;
import java.util.List;

public class WorldBoundaryManager {

    private final PhysicsWorld physicsWorld;
    private final WorldsConfig.WorldSizeSettings settings;
    private final List<Integer> boundaryBodyIds = new ArrayList<>();

    public WorldBoundaryManager(PhysicsWorld physicsWorld, WorldsConfig.WorldSizeSettings settings) {
        this.physicsWorld = physicsWorld;
        this.settings = settings;
    }

    public void createBoundaries() {
        if (!settings.createWalls()) {
            return;
        }

        InertiaLogger.info("Creating world boundaries (Walls) for " + physicsWorld.getBukkitWorld().getName() + "...");

        Vec3 min = settings.localMin();
        Vec3 max = settings.localMax();
        Vec3 heightSize = settings.heightSize();

        // Calculate center and extents based on X/Z, using huge Y
        float sizeX = max.getX() - min.getX();
        float sizeZ = max.getZ() - min.getZ();

        float heightMin = heightSize.getX();
        float heightMax = heightSize.getZ();

        // Wall height (extend far up and down relative to origin)
        float wallCenterY = 0.0f; // Centered at origin Y

        float centerX = min.getX() + sizeX * 0.5f;
        float centerZ = min.getZ() + sizeZ * 0.5f;

        float halfX = sizeX * 0.5f;
        float halfZ = sizeZ * 0.5f;
        float halfY = heightMax * 0.5f;
        float thickness = 1.0f;

        // Min X Wall
        createWall(
                new Vec3(min.getX() - thickness * 0.5f, wallCenterY, centerZ),
                new Vec3(thickness * 0.5f, halfY, halfZ)
        );

        // Max X Wall
        createWall(
                new Vec3(max.getX() + thickness * 0.5f, wallCenterY, centerZ),
                new Vec3(thickness * 0.5f, halfY, halfZ)
        );

        // Min Z Wall
        createWall(
                new Vec3(centerX, wallCenterY, min.getZ() - thickness * 0.5f),
                new Vec3(halfX, halfY, thickness * 0.5f)
        );

        // Max Z Wall
        createWall(
                new Vec3(centerX, wallCenterY, max.getZ() + thickness * 0.5f),
                new Vec3(halfX, halfY, thickness * 0.5f)
        );

        // Floor
        createWall(
                new Vec3(centerX, heightMin - thickness * 0.5f, centerZ),
                new Vec3(halfX, thickness * 0.5f, halfZ)
        );

        // Ceiling
        createWall(
                new Vec3(centerX, heightMax + thickness * 0.5f, centerZ),
                new Vec3(halfX, thickness * 0.5f, halfZ)
        );
    }

    private void createWall(Vec3 position, Vec3 halfExtents) {
        BodyInterface bi = physicsWorld.getBodyInterface();
        BoxShape shape = new BoxShape(halfExtents);

        BodyCreationSettings bcs = new BodyCreationSettings();
        bcs.setPosition(new RVec3(position.getX(), position.getY(), position.getZ()));
        bcs.setShape(shape);
        bcs.setMotionType(EMotionType.Static);
        bcs.setObjectLayer(PhysicsLayers.OBJ_STATIC);
        bcs.setFriction(0.0f); // Slippery walls
        bcs.setRestitution(0.5f); // Bouncy walls

        Body body = bi.createBody(bcs);
        bi.addBody(body, EActivation.DontActivate);
        boundaryBodyIds.add(body.getId());
    }

    public void destroyBoundaries() {
        if (boundaryBodyIds.isEmpty()) return;

        BodyInterface bi = physicsWorld.getBodyInterface();
        for (int id : boundaryBodyIds) {
            bi.removeBody(id);
            bi.destroyBody(id);
        }
        boundaryBodyIds.clear();
    }

    /**
     * Checks if a point (in Jolt Local Space) is within the configured horizontal world bounds.
     * Ignores Y for "inside" check unless explicitly desired, but standard is infinite column.
     */
    public boolean isInside(RVec3 point) {
        Vec3 min = settings.localMin();
        Vec3 max = settings.localMax();

        return point.xx() >= min.getX() && point.xx() <= max.getX() &&
                point.zz() >= min.getZ() && point.zz() <= max.getZ();
    }

    public boolean isAABBInside(com.github.stephengold.joltjni.AaBox aabb) {
        com.github.stephengold.joltjni.Vec3 min = settings.localMin();
        com.github.stephengold.joltjni.Vec3 max = settings.localMax();
        com.github.stephengold.joltjni.Vec3 boxMin = aabb.getMin();
        com.github.stephengold.joltjni.Vec3 boxMax = aabb.getMax();

        // Проверяем, что все координаты коробки находятся внутри границ мира
        return boxMin.getX() >= min.getX() && boxMax.getX() <= max.getX() &&
                boxMin.getY() >= min.getY() && boxMax.getY() <= max.getY() &&
                boxMin.getZ() >= min.getZ() && boxMax.getZ() <= max.getZ();
    }

    /**
     * Checks if a point (in Jolt Local Space) is below the minimum Y threshold configured in Min/Max.
     */
    public boolean isBelowBottom(RVec3 point) {
        return point.yy() < settings.localMin().getY();
    }
}