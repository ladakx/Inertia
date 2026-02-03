package com.ladakx.inertia.physics.world.terrain.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
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
            RVec3 origin = world.getOrigin();

            // Calculate floor parameters relative to World Origin
            WorldsConfig.FloorBounds bounds = settings.bounds();
            float originX = bounds.origin().getX();
            float originZ = bounds.origin().getZ();

            float actualMinX = bounds.minX() + originX;
            float actualMaxX = bounds.maxX() + originX;
            float actualMinZ = bounds.minZ() + originZ;
            float actualMaxZ = bounds.maxZ() + originZ;

            float sizeX = Math.abs(actualMaxX - actualMinX) * 0.5f;
            float sizeZ = Math.abs(actualMaxZ - actualMinZ) * 0.5f;

            float halfExtentX = sizeX;
            float halfExtentZ = sizeZ;
            float halfExtentY = settings.ySize() * 0.5f;

            double centerX = actualMinX + sizeX;
            double centerZ = actualMinZ + sizeZ;
            double centerY = settings.yLevel() - halfExtentY;

            // Apply Origin offset
            double joltX = centerX - origin.xx();
            double joltY = centerY - origin.yy();
            double joltZ = centerZ - origin.zz();

            ConstShape floorShape = new BoxShape(new Vec3(halfExtentX, halfExtentY, halfExtentZ));
            RVec3 position = new RVec3(joltX, joltY, joltZ);

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
            world.registerSystemStaticBody(this.bodyId);

            InertiaLogger.info("Flat floor generated at Local Y=" + joltY + " (World Y=" + settings.yLevel() + ") with size " + (sizeX*2) + "x" + (sizeZ*2));

        } catch (Exception e) {
            InertiaLogger.error("Failed to create flat floor", e);
        }
    }

    @Override
    public void onDisable() {
        if (bodyId != null && world != null) {
            BodyInterface bi = world.getBodyInterface();
            world.unregisterSystemStaticBody(bodyId);
            bi.removeBody(bodyId);
            bi.destroyBody(bodyId);
            bodyId = null;
        }
    }

    @Override
    public void onChunkLoad(int x, int z) {
    }

    @Override
    public void onChunkUnload(int x, int z) {
    }
}
