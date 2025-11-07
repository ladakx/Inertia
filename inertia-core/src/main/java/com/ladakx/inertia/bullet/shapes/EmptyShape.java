package com.ladakx.inertia.bullet.shapes;

public class EmptyShape extends com.jme3.bullet.collision.shapes.EmptyShape {

    public EmptyShape() {
        super(true);
    }

    public static EmptyShape create() {
        return new EmptyShape();
    }
}
