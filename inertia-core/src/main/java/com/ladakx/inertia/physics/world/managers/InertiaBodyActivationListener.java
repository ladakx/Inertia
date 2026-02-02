package com.ladakx.inertia.physics.world.managers;

import com.github.stephengold.joltjni.CustomBodyActivationListener;

public class InertiaBodyActivationListener extends CustomBodyActivationListener {
    private final PhysicsObjectManager objectManager;

    public InertiaBodyActivationListener(PhysicsObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @Override
    public void onBodyActivated(int bodyId, long bodyUserData) {
        objectManager.onBodyActivated(bodyId);
    }

    @Override
    public void onBodyDeactivated(int bodyId, long bodyUserData) {
        objectManager.onBodyDeactivated(bodyId);
    }
}