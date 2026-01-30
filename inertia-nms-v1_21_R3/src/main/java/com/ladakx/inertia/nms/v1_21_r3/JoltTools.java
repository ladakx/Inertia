package com.ladakx.inertia.nms.v1_21_r3;

import com.github.stephengold.joltjni.AaBox;
import com.ladakx.inertia.nms.v1_21_r3.utils.JoltWrapUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockState;

import java.lang.reflect.Field;
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
        var b = craftChunk.getHandle(ChunkStatus.FULL);
        var v = b.getBlockState(x, y, z);
        return null;
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
        CraftChunk craftChunk = (CraftChunk) chunk;
        ChunkAccess ch = craftChunk.getHandle(ChunkStatus.FULL);
        return ch.getSectionsCount();
    }

    @Override
    public int getMinSectionY(Chunk chunk) {
        CraftChunk craftChunk = (CraftChunk) chunk;
        ChunkAccess ch = craftChunk.getHandle(ChunkStatus.FULL);

        return ch.getMinSectionY();
    }

    @Override
    public int getMaxSectionY(Chunk chunk) {
        CraftChunk craftChunk = (CraftChunk) chunk;
        ChunkAccess ch = craftChunk.getHandle(ChunkStatus.FULL);

        return ch.getMinSectionY();
    }

    @Override
    public boolean hasOnlyAir(Chunk chunk, int numSect) {
        CraftChunk craftChunk = (CraftChunk) chunk;
        ChunkAccess ch = craftChunk.getHandle(ChunkStatus.FULL);

        return ch.getSection(numSect).hasOnlyAir();
    }

    @Override
    public boolean isSectionFull(Chunk chunk, int numSect) {
        return getSectionsNonEmptyBlocks(chunk, numSect) == 4096;
    }

    @Override
    public short getSectionsNonEmptyBlocks(Chunk chunk, int numSect) {
        var craftChunk = (CraftChunk) chunk;
        var chunkAccess = craftChunk.getHandle(ChunkStatus.FULL);
        var section = chunkAccess.getSection(numSect);

        try {
            Field field = section.getClass().getDeclaredField("nonEmptyBlockCount");
            field.setAccessible(true);
            return ((Short)field.get(section));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            org.bukkit.Bukkit.getServer().getLogger().log(java.util.logging.Level.SEVERE, "Failed to reflect nonEmptyBlockCount in NMS v1_21_R3", e);
            return 0;
        }
    }

    @Override
    public Material getMaterial(Chunk chunk, int numSect, int x, int y, int z) {
        var craftChunk = (CraftChunk) chunk;
        var chunkAccess = craftChunk.getHandle(ChunkStatus.FULL);
        var section = chunkAccess.getSection(numSect);

        return section.getStates().get(x,y,z).getBukkitMaterial();
    }

    @Override
    public BlockState getBlockState(Chunk chunk, int numSect, int x, int y, int z) {
        var craftChunk = (CraftChunk) chunk;
        var chunkAccess = craftChunk.getHandle(ChunkStatus.FULL);
        var section = chunkAccess.getSection(numSect);

        return (BlockState) section.getStates().get(x,y,z);
    }

    @Override
    public int getBlockSkyLight(Chunk chunk, int x, int y, int z) {
        var craftChunk = (CraftChunk) chunk;
        var chunkSnap = chunk.getChunkSnapshot();

        return chunkSnap.getBlockSkyLight(x-(craftChunk.getX()<<4), y, z-(craftChunk.getZ()<<4));
    }

    @Override
    public int getBlockEmittedLight(Chunk chunk, int x, int y, int z) {
        var craftChunk = (CraftChunk) chunk;
        var chunkSnap = chunk.getChunkSnapshot();

        return chunkSnap.getBlockEmittedLight(x-(craftChunk.getX()<<4), y, z-(craftChunk.getZ()<<4));
    }

    public static Direction[] DIRECTIONS = Direction.values();
}