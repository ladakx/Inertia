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
    private final WorldsConfig.WorldSizeSettings sizeSettings;
    private Integer bodyId;
    private PhysicsWorld world;

    public FlatFloorAdapter(WorldsConfig.FloorPlaneSettings settings, WorldsConfig.WorldSizeSettings sizeSettings) {
        this.settings = Objects.requireNonNull(settings);
        this.sizeSettings = Objects.requireNonNull(sizeSettings);
    }

    @Override
    public void onEnable(PhysicsWorld world) {
        this.world = world;
        if (!settings.enabled()) return;

        try {
            BodyInterface bi = world.getBodyInterface();
            Vec3 min = sizeSettings.min();
            Vec3 max = sizeSettings.max();

            float sizeX = Math.abs(max.getX() - min.getX()) * 0.5f;
            float sizeZ = Math.abs(max.getZ() - min.getZ()) * 0.5f;
            float halfExtent = Math.max(sizeX, sizeZ);

            double centerX = min.getX() + sizeX;
            double centerZ = min.getZ() + sizeZ;

            ConstPlane plane = new Plane(Vec3.sAxisY(), 0.0f);
            ConstShape floorShape = new com.github.stephengold.joltjni.PlaneShape(plane, null, halfExtent);

            RVec3 position = new RVec3(centerX, settings.yLevel(), centerZ);

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
            InertiaLogger.info("Flat floor generated at Y=" + settings.yLevel());
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
        // Flat floor is global, chunk events ignored
    }

    @Override
    public void onChunkUnload(int x, int z) {
        // Flat floor is global, chunk events ignored
    }
}