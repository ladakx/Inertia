package com.ladakx.inertia.bullet.generator.rayon;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.terrarian.ITerrainGenerator;
import com.ladakx.inertia.bullet.bodies.element.PhysicsElement;
import com.ladakx.inertia.bullet.cache.CacheManager;
import com.ladakx.inertia.utils.block.BlockUtils;
import com.ladakx.inertia.utils.block.BlockPos;
import com.ladakx.inertia.nms.bullet.BulletNMSTools;
import com.ladakx.inertia.bullet.bodies.terrarian.BlockRigidBody;
import com.ladakx.inertia.bullet.space.MinecraftSpace;
import com.ladakx.inertia.bullet.cache.block.BlockCache;
import com.ladakx.inertia.bullet.block.BulletBlockData;
import com.ladakx.inertia.utils.bullet.BetweenClosedUtils;
import com.ladakx.inertia.utils.bullet.BoundingBoxUtils;
import org.bukkit.block.BlockState;

import java.util.*;

/**
 * Used for loading blocks into the simulation so that rigid bodies can interact with them.
 *
 * @see MinecraftSpace
 */
public class RayonGenerator implements ITerrainGenerator {

    // ************************************
    // Final Fields
    private final BulletNMSTools nmsTools;
    private final MinecraftSpace space;

    // ************************************
    // Cache
    private final BlockCache cache;

    // ************************************
    // Fields
    private final float inflate;

    public RayonGenerator(MinecraftSpace space) {
        this.nmsTools = InertiaPlugin.getBulletNMSTools();
        this.space = space;

        this.inflate = InertiaPlugin.getPConfig().SIMULATION.SETTINGS.Rayon.inflate;

        CacheManager manager = InertiaPlugin.getBulletManager().getCacheManager();
        this.cache = manager.getGuavaBlockCacheFactory().create(space);
    }

    @Override
    public void step() {
        final HashSet<BlockRigidBody> keep = new HashSet<>();
        List<BlockPos> betweenClosed = new ArrayList<>();

        // For each terrain that is loaded and active, we add it to the betweenClosed list
        for (PhysicsElement element : space.getPhysicsElements()) {
            if (element.getRigidBody().isActive()) {
                BoundingBox box = element.getRigidBody().boundingBox(new BoundingBox());
                BoundingBoxUtils.inflate(box, inflate);
                BetweenClosedUtils.betweenClosed(box, betweenClosed);
            }
        }

        // For each block position, we check if the block is rendered and if it is, we add it to the simulation
        for (BlockPos blockPos : betweenClosed) {
            BulletBlockData bulletBlockData = cache.getBlockData(blockPos);
            if (bulletBlockData == null) continue;

            if (bulletBlockData.shape() != null) {
                if (!BlockUtils.isRendered(bulletBlockData.blockState())) continue;
                BlockRigidBody terrain = space.getTerrainObjectAt(blockPos);

                if (terrain == null) {
                    // Add the terrain if it does not exist
                    BlockRigidBody newTerrain = BlockRigidBody.from(bulletBlockData);
                    space.addCollisionObject(newTerrain);
                    keep.add(newTerrain);
                } else {
                    // If the terrain is not the same, we update it
                    if (!nmsTools.equalsById(bulletBlockData.blockState(), terrain.getBlockState())) {
                            space.removeCollisionObject(terrain);
                            BlockRigidBody newTerrain = BlockRigidBody.from(bulletBlockData);
                            space.addCollisionObject(newTerrain);
                            keep.add(newTerrain);
                    } else {
//                        // If the terrain is the same, we keep it
                        keep.add(terrain);
                    }
                }
            }
        }

        // Delete all terrains that are not in keep
        for (Map.Entry<BlockPos, BlockRigidBody> entry : space.getTerrainMap().entrySet()) {
            BlockPos blockPos = entry.getKey();
            BlockRigidBody terrain = entry.getValue();
            if (!keep.contains(terrain)) {
                space.removeTerrainObjectAt(blockPos);
            }
        }
    }

    private void optimizeInflate(PhysicsElement element, Set<BlockPos> betweenClosed) {
        BoundingBox box = element.getRigidBody().boundingBox(new BoundingBox());
        Vector3f velocity = element.getRigidBody().getLinearVelocity(new Vector3f());
        float speed = velocity.length();

        //if (speed < 0.01f) {
        //    BoundingBoxUtils.inflate(box, 0.0f, 0.5f, 0.0f);
        //} else {
        //    BoundingBoxUtils.inflate(box, inflate * speed);
        //}

        BoundingBoxUtils.inflate(box, inflate);
        BetweenClosedUtils.betweenClosed(box, betweenClosed);
    }

    @Override
    public void refresh(BlockPos blockPos, BlockState blockState) {
        cache.refreshBlockData(blockPos, blockState);
    }
}