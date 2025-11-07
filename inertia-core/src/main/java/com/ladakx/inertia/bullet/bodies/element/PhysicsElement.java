package com.ladakx.inertia.bullet.bodies.element;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.ladakx.inertia.utils.block.BlockPos;
import com.ladakx.inertia.utils.bullet.BoundingBoxUtils;

public class PhysicsElement {

    private PhysicsRigidBody rigidBody;

    public PhysicsElement() {
        this.rigidBody = null;
    }

    public PhysicsElement(PhysicsRigidBody rigidBody) {
        this.rigidBody = rigidBody;
    }

    public boolean isNear(BlockPos blockPos) {
        BoundingBox boundingBox1 = rigidBody.boundingBox(new BoundingBox());
        BoundingBox boundingBox2 = blockPos.boundingBox();
        BoundingBoxUtils.inflate(boundingBox2, 0.5f);
        return BoundingBoxUtils.intersects(boundingBox1, boundingBox2);
    }

    public void update() {}

    public CollisionShape getCollisionShape() {
        return rigidBody.getCollisionShape();
    }

    public PhysicsRigidBody getRigidBody() {
        return rigidBody;
    }

    public void setRigidBody(PhysicsRigidBody rigidBody) {
        this.rigidBody = rigidBody;
    }
}