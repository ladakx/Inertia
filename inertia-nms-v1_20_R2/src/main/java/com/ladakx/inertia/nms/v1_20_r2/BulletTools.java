package com.ladakx.inertia.nms.v1_20_r2;

import com.jme3.bounding.BoundingBox;
import com.ladakx.inertia.nms.bullet.BulletNMSTools;
import com.ladakx.inertia.nms.v1_20_r2.utils.BulletWrapUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.v1_20_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R2.block.CraftBlockState;
import org.bukkit.craftbukkit.v1_20_R2.block.CraftBlockStates;

import java.util.List;
import java.util.stream.Collectors;

public class BulletTools implements BulletNMSTools {

    @Override
    public List<BoundingBox> boundingBoxes(BlockState blockState) {
        BlockBehaviour.BlockStateBase stateHandle = ((CraftBlockState) blockState).getHandle();
        net.minecraft.core.BlockPos blockPosHandle = ((CraftBlock) blockState.getBlock()).getPosition();
        net.minecraft.world.level.Level worldHandle = ((CraftWorld) blockState.getWorld()).getHandle();
        net.minecraft.world.phys.shapes.VoxelShape voxelShape = stateHandle.getCollisionShape(worldHandle, blockPosHandle);

        List<AABB> boundingBoxes = voxelShape.toAabbs();

        return boundingBoxes.stream()
                .map(BulletWrapUtils::convert)
                .collect(Collectors.toList());
    }

    @Override
    public int getBlockId(BlockState blockState) {
        return net.minecraft.world.level.block.Block.getId(((CraftBlockState) blockState).getHandle());
    }

    @Override
    public boolean equalsById(BlockState blockState1, BlockState blockState2) {
        return getBlockId(blockState1) == getBlockId(blockState2);
    }

    @Override
    public BoundingBox boundingBox(BlockState blockState) {
        BlockBehaviour.BlockStateBase stateHandle = ((CraftBlockState) blockState).getHandle();
        net.minecraft.world.phys.shapes.VoxelShape voxelShape = stateHandle.getCollisionShape(((CraftBlock) blockState.getBlock()).getHandle(), ((CraftBlock) blockState.getBlock()).getPosition());
        if (voxelShape.isEmpty()) {
            return null;
        } else {
            return BulletWrapUtils.convert(voxelShape.bounds());
        }
    }

    @Override
    public BlockState createBlockState(Material material) {
        return CraftBlockStates.getBlockState(material, null);
    }

    @Override
    public BlockState getBlockState(Chunk chunk, int x, int y, int z) {
        return null;
    }

    @Override
    public BoundingBox getOcclusionShape(Chunk chunk, int x, int y, int z) {
        return null;
    }

    @Override
    public boolean renderFace(World world, Block block, com.ladakx.inertia.enums.Direction face) {
        return false;
    }

    @Override
    public int getSectionsCount(Chunk chunk) {
        return 0;
    }

    @Override
    public int getMinSectionY(Chunk chunk) {
        return 0;
    }

    @Override
    public int getMaxSectionY(Chunk chunk) {
        return 0;
    }

    @Override
    public boolean hasOnlyAir(Chunk chunk, int numSect) {
        return false;
    }

    @Override
    public short getSectionsNonEmptyBlocks(Chunk chunk, int numSect) {
        return 0;
    }

    @Override
    public boolean isSectionFull(Chunk chunk, int numSect) {
        return false;
    }

    @Override
    public Material getMaterial(Chunk chunk, int numSect, int x, int y, int z) {
        return null;
    }

    @Override
    public BlockState getBlockState(Chunk chunk, int numSect, int x, int y, int z) {
        return null;
    }

    @Override
    public int getBlockSkyLight(Chunk chunk, int x, int y, int z) {
        return 0;
    }

    @Override
    public int getBlockEmittedLight(Chunk chunk, int x, int y, int z) {
        return 0;
    }

    public static Direction[] DIRECTIONS = Direction.values();
}