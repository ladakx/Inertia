package com.ladakx.inertia.physics.world.terrain.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstPlane;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.physics.engine.PhysicsLayers;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;

import java.util.Objects;

public class FlatFloorAdapter implements TerrainAdapter {

    private final WorldsConfig.FloorPlaneSettings settings;
    private Integer bodyId;
    private PhysicsWorld world;

    public FlatFloorAdapter(WorldsConfig.FloorPlaneSettings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    @Override
    public void onEnable(PhysicsWorld world) {
        this.world = world;

        try {
            BodyInterface bi = world.getBodyInterface();

            WorldsConfig.FloorBounds bounds = settings.bounds();

            // Вычисляем размеры на основе min/max и origin
            // Origin - это смещение всей системы координат пола
            float originX = bounds.origin().getX();
            float originZ = bounds.origin().getZ();

            float actualMinX = bounds.minX() + originX;
            float actualMaxX = bounds.maxX() + originX;
            float actualMinZ = bounds.minZ() + originZ;
            float actualMaxZ = bounds.maxZ() + originZ;

            float sizeX = Math.abs(actualMaxX - actualMinX) * 0.5f;
            float sizeZ = Math.abs(actualMaxZ - actualMinZ) * 0.5f;

            // BoxShape (или PlaneShape с bounds) требует half-extents
            float halfExtentX = sizeX;
            float halfExtentZ = sizeZ;
            // Для BoxShape толщина задается через Y
            float halfExtentY = settings.ySize() * 0.5f;

            // Центр пола
            double centerX = actualMinX + sizeX;
            double centerZ = actualMinZ + sizeZ;
            double centerY = settings.yLevel() - halfExtentY; // Смещаем вниз, чтобы верхняя грань была на yLevel

            // Создаем BoxShape, так как PlaneShape бесконечен или сложен в настройке bounds в старых версиях Jolt
            // BoxShape надежнее для ограниченного пола
            ConstShape floorShape = new BoxShape(new Vec3(halfExtentX, halfExtentY, halfExtentZ));

            RVec3 position = new RVec3(centerX, centerY, centerZ);

            BodyCreationSettings bcs = new BodyCreationSettings();
            bcs.setPosition(position);
            bcs.setMotionType(EMotionType.Static);
            bcs.setObjectLayer(PhysicsLayers.OBJ_STATIC);
            bcs.setShape(floorShape);
            bcs.setFriction(settings.friction());
            bcs.setRestitution(settings.restitution());

            Body floor = bi.createBody(bcs);
            bi.addBody(floor, EActivation.DontActivate);

            this.bodyId = floor.getId();
            InertiaLogger.info("Flat floor generated at Y=" + settings.yLevel() + " with size " + (sizeX*2) + "x" + (sizeZ*2));

        } catch (Exception e) {
            InertiaLogger.error("Failed to create flat floor", e);
        }
    }

    @Override
    public void onDisable() {
        if (bodyId != null && world != null) {
            BodyInterface bi = world.getBodyInterface();
            bi.removeBody(bodyId);
            bi.destroyBody(bodyId);
            bodyId = null;
        }
    }

    @Override
    public void onChunkLoad(int x, int z) {
        // Для плоского пола чанки не важны
    }

    @Override
    public void onChunkUnload(int x, int z) {
    }
}