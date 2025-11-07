package com.ladakx.inertia.bullet.bodies.terrarian;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import com.ladakx.inertia.bullet.shapes.BlockMeshShape;

public class TerrainRigidBody extends PhysicsRigidBody {

    public TerrainRigidBody(BlockMeshShape shape, Vector3f center, float friction, float restitution) {
        super(shape, massForStatic);

        this.setFriction(friction);
        this.setRestitution(restitution);

        this.setPhysicsLocation(center.clone());
    }
}