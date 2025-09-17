package com.ladakx.inertia.api.shape;

public class CubeShape implements InertiaShape {

    private final double sizeX, sizeY, sizeZ;

    public CubeShape(double sizeX, double sizeY, double sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public double getSizeX() {
        return sizeX;
    }

    public double getSizeY() {
        return sizeY;
    }

    public double getSizeZ() {
        return sizeZ;
    }
}

