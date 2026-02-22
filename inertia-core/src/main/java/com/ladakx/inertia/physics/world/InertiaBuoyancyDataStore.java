package com.ladakx.inertia.physics.world;

import java.util.Arrays;

final class InertiaBuoyancyDataStore {
    private int count;
    private int capacity;
    int[] bodyIds;
    float[] surfaceHeights;
    InertiaFluidType[] fluidTypes;
    float[] areaFractions;
    float[] waterCenterX;
    float[] waterCenterZ;
    float[] flowX;
    float[] flowY;
    float[] flowZ;

    InertiaBuoyancyDataStore(int initialCapacity) {
        allocate(Math.max(1, initialCapacity));
    }

    int getCount() {
        return count;
    }

    void clear() {
        count = 0;
    }

    void add(int bodyId,
             float surfaceHeight,
             InertiaFluidType fluidType,
             float areaFraction,
             float centerX,
             float centerZ,
             float fluidFlowX,
             float fluidFlowY,
             float fluidFlowZ) {
        if (count >= capacity) {
            allocate(capacity << 1);
        }
        bodyIds[count] = bodyId;
        surfaceHeights[count] = surfaceHeight;
        fluidTypes[count] = fluidType;
        areaFractions[count] = areaFraction;
        waterCenterX[count] = centerX;
        waterCenterZ[count] = centerZ;
        flowX[count] = fluidFlowX;
        flowY[count] = fluidFlowY;
        flowZ[count] = fluidFlowZ;
        count++;
    }

    private void allocate(int newCapacity) {
        if (newCapacity <= capacity) {
            return;
        }
        bodyIds = bodyIds == null ? new int[newCapacity] : Arrays.copyOf(bodyIds, newCapacity);
        surfaceHeights = surfaceHeights == null ? new float[newCapacity] : Arrays.copyOf(surfaceHeights, newCapacity);
        fluidTypes = fluidTypes == null ? new InertiaFluidType[newCapacity] : Arrays.copyOf(fluidTypes, newCapacity);
        areaFractions = areaFractions == null ? new float[newCapacity] : Arrays.copyOf(areaFractions, newCapacity);
        waterCenterX = waterCenterX == null ? new float[newCapacity] : Arrays.copyOf(waterCenterX, newCapacity);
        waterCenterZ = waterCenterZ == null ? new float[newCapacity] : Arrays.copyOf(waterCenterZ, newCapacity);
        flowX = flowX == null ? new float[newCapacity] : Arrays.copyOf(flowX, newCapacity);
        flowY = flowY == null ? new float[newCapacity] : Arrays.copyOf(flowY, newCapacity);
        flowZ = flowZ == null ? new float[newCapacity] : Arrays.copyOf(flowZ, newCapacity);
        capacity = newCapacity;
    }
}
