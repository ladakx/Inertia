package com.ladakx.inertia.bullet.bodies.terrarian;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import com.ladakx.inertia.bullet.shapes.BlockMeshShape;
import com.ladakx.inertia.utils.block.BlockPos;
import com.ladakx.inertia.bullet.block.BulletBlockSettings;
import com.ladakx.inertia.bullet.block.BulletBlockData;
import org.bukkit.block.BlockState;

/**
 * Represents a block in the physics world.
 * Using for DynamicSimulatin.
 */
public class BlockRigidBody extends PhysicsRigidBody {

    private final BlockPos blockPos;
    private final BlockState state;

    public BlockRigidBody(BlockMeshShape shape, BlockPos blockPos, BlockState blockState, float friction, float restitution) {
        super(shape, massForStatic);
        this.blockPos = blockPos;
        this.state = blockState;

        this.setFriction(friction);
        this.setRestitution(restitution);

        this.setPhysicsLocation(new Vector3f(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
    }

    /**
     * Create a BlockRigidBody from a BulletBlockData.
     * @param bulletBlockData the BulletBlockData to create the BlockRigidBody from.
     * @return the BlockRigidBody.
     */
    public static BlockRigidBody from(BulletBlockData bulletBlockData) {
        final BulletBlockSettings bulletBlockSettings = BulletBlockSettings.getBlockSettings(bulletBlockData.blockState().getType());
        return new BlockRigidBody(bulletBlockData.shape(), new BlockPos(bulletBlockData.blockState().getBlock()), bulletBlockData.blockState(), bulletBlockSettings.friction(), bulletBlockSettings.restitution());
    }

    /**
     * Get the block position.
     * @return the block position.
     */
    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    /**
     * Get the block state.
     * @return the block state.
     */
    public BlockState getBlockState() {
        return this.state;
    }

    /**
     * Get the block data.
     * @param obj the object to compare (may be null, unaffected)
     * @return true if the object is equal to this BlockRigidBody.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BlockRigidBody terrain) {
            return terrain.getBlockPos().equals(this.blockPos) && terrain.getBlockState().equals(this.state);
        }

        return false;
    }
}