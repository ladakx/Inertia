package com.ladakx.inertia.nms.v1_21_r2;

import com.github.stephengold.joltjni.AaBox;
import com.ladakx.inertia.nms.v1_21_r2.utils.JoltWrapUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockState;

import java.util.List;
import java.util.stream.Collectors;

public class JoltTools implements com.ladakx.inertia.nms.jolt.JoltTools {

    public JoltTools() {
        // Constructor
    }

    @Override
    public List<AaBox> boundingBoxes(BlockState blockState) {
        BlockBehaviour.BlockStateBase stateHandle = ((CraftBlockState) blockState).getHandle();
        BlockPos blockPosHandle = ((CraftBlock) blockState.getBlock()).getPosition();
        net.minecraft.world.level.Level worldHandle = ((CraftWorld) blockState.getWorld()).getHandle();
        net.minecraft.world.phys.shapes.VoxelShape voxelShape = stateHandle.getCollisionShape(worldHandle, blockPosHandle);

        List<AABB> boundingBoxes = voxelShape.toAabbs();

        return boundingBoxes.stream()
                .map(JoltWrapUtils::convert)
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
    public AaBox boundingBox(BlockState blockState) {
        BlockBehaviour.BlockStateBase stateHandle = ((CraftBlockState) blockState).getHandle();
        net.minecraft.world.phys.shapes.VoxelShape voxelShape = stateHandle.getCollisionShape(((CraftBlock) blockState.getBlock()).getHandle(), ((CraftBlock) blockState.getBlock()).getPosition());
        if (voxelShape.isEmpty()) {
            return null;
        } else {
            return JoltWrapUtils.convert(voxelShape.bounds());
        }
    }

    @Override
    public BlockState createBlockState(Material material) {
        return Bukkit.createBlockData(material).createBlockState();
    }

    @Override
    public BlockState getBlockState(Chunk chunk, int x, int y, int z) {
        CraftChunk craftChunk = (CraftChunk) chunk;
        chunk.getChunkSnapshot();
        return (BlockState) craftChunk.getHandle(ChunkStatus.FULL).getBlockState(x, y, z);
    }

    @Override
    public AaBox getOcclusionShape(Chunk chunk, int x, int y, int z) {
        CraftChunk craftChunk = (CraftChunk) chunk;
        chunk.getChunkSnapshot();
        var b = craftChunk.getHandle(ChunkStatus.FULL).getBlockState(x, y, z);
        return JoltWrapUtils.convert(b.getOcclusionShape().bounds());
    }

    @Override
    public boolean renderFace(World world, Block block, com.ladakx.inertia.utils.Direction face) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        net.minecraft.world.level.block.state.BlockState base = ((CraftBlockState)block.getState()).getHandle();
        net.minecraft.world.level.block.state.BlockState other = level.getBlockState(new BlockPos(block.getX()+face.dx, block.getY()+face.dy, block.getZ()+face.dz));

        return net.minecraft.world.level.block.Block.shouldRenderFace(base, other, DIRECTIONS[face.ordinal()]);
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