package com.ladakx.inertia.physics.engine;

public interface PhysicsLayers {
    // Object Layers (Типи об'єктів)
    int OBJ_MOVING = 0;
    int OBJ_STATIC = 1;
    int NUM_OBJ_LAYERS = 2;

    // Broadphase Layers (Швидка фаза перевірки)
    int BP_MOVING = 0;
    int BP_STATIC = 1;
    int NUM_BP_LAYERS = 1;
}