package com.ladakx.inertia.common;

import org.bukkit.block.Block;

public enum Direction {
    // Define the six possible directions with their corresponding offsets.
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    // The x, y, and z offsets for the direction.
    public final int dx, dy, dz;

    // Constructor for the Direction enum, setting the offsets.
    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    /**
     * Converts a string value to a corresponding Direction.
     *
     * @param str the string representation of the direction (e.g., "up", "down")
     * @return the matching Direction object, or null if no match is found
     */
    public static Direction fromString(String str) {
        switch (str.toLowerCase()) {
            case "up": return UP;
            case "down": return DOWN;
            case "north": return NORTH;
            case "south": return SOUTH;
            case "west": return WEST;
            case "east": return EAST;
            default: return null;
        }
    }

    /**
     * Converts a BlockFace to a corresponding Direction.
     *
     * @param face the BlockFace object from Bukkit
     * @return the matching Direction, or null if no match exists
     */
    public static Direction fromBlockFace(org.bukkit.block.BlockFace face) {
        switch (face) {
            case UP: return UP;
            case DOWN: return DOWN;
            case NORTH: return NORTH;
            case SOUTH: return SOUTH;
            case WEST: return WEST;
            case EAST: return EAST;
            default: return null;
        }
    }

    /**
     * Converts this Direction to its corresponding Bukkit BlockFace.
     *
     * @return the BlockFace representation of this Direction
     */
    public org.bukkit.block.BlockFace toBlockFace() {
        switch (this) {
            case UP: return org.bukkit.block.BlockFace.UP;
            case DOWN: return org.bukkit.block.BlockFace.DOWN;
            case NORTH: return org.bukkit.block.BlockFace.NORTH;
            case SOUTH: return org.bukkit.block.BlockFace.SOUTH;
            case WEST: return org.bukkit.block.BlockFace.WEST;
            case EAST: return org.bukkit.block.BlockFace.EAST;
            default: return null;
        }
    }

    /**
     * Returns an array of horizontal directions (North, South, West, East).
     *
     * @return array containing only horizontal directions.
     */
    public Direction[] getHorizontal() {
        return new Direction[] {NORTH, SOUTH, WEST, EAST};
    }

    /**
     * Returns an array of vertical directions (Up and Down).
     *
     * @return array containing only vertical directions.
     */
    public Direction[] getVertical() {
        return new Direction[] {UP, DOWN};
    }

    /**
     * Returns the opposite direction of the current one.
     *
     * @return the opposite Direction.
     */
    public Direction opposite() {
        switch (this) {
            case UP: return DOWN;
            case DOWN: return UP;
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case WEST: return EAST;
            case EAST: return WEST;
            default: return this;
        }
    }

    /**
     * Rotates the current direction 90 degrees around the Y axis.
     *
     * @return the Direction after a single Y-axis rotation (yaw rotation).
     */
    public Direction rotateY() {
        switch (this) {
            // Vertical directions remain unchanged.
            case UP: return UP;
            case DOWN: return DOWN;
            // For horizontal directions, a 90 degree rotation.
            case NORTH: return WEST;
            case SOUTH: return EAST;
            case WEST: return SOUTH;
            case EAST: return NORTH;
            default: return this;
        }
    }

    /**
     * Rotates the current direction 90 degrees around the X axis.
     *
     * @return the Direction after a single X-axis rotation (pitch rotation).
     */
    public Direction rotateX() {
        // For some directions, rotation on X axis is ambiguous.
        switch (this) {
            // When facing UP or NORTH, remain UP.
            case UP, NORTH: return UP;
            // When facing DOWN or SOUTH, remain DOWN.
            case DOWN, SOUTH: return DOWN;
            // Horizontal directions West and East remain the same.
            case WEST: return WEST;
            case EAST: return EAST;
            default: return this;
        }
    }

    /**
     * Rotates the current direction 90 degrees around the Z axis.
     *
     * @return the Direction after a single Z-axis rotation.
     */
    public Direction rotateZ() {
        // Vertical and some horizontal directions remain unchanged.
        switch (this) {
            case UP: return UP;
            case DOWN: return DOWN;
            case NORTH: return NORTH;
            case SOUTH: return SOUTH;
            // Swap WEST and EAST after Z-axis rotation.
            case WEST: return EAST;
            case EAST: return WEST;
            default: return this;
        }
    }

    /**
     * Rotates the current direction around the Y axis a specified number of times.
     *
     * @param times the number of 90-degree rotations to perform.
     * @return the final Direction after rotations.
     */
    public Direction rotateY(int times) {
        Direction dir = this;
        for (int i = 0; i < times; i++) {
            dir = dir.rotateY();
        }
        return dir;
    }

    /**
     * Rotates the current direction around the X axis a specified number of times.
     *
     * @param times the number of 90-degree rotations to perform.
     * @return the final Direction after rotations.
     */
    public Direction rotateX(int times) {
        Direction dir = this;
        for (int i = 0; i < times; i++) {
            dir = dir.rotateX();
        }
        return dir;
    }

    /**
     * Rotates the current direction around the Z axis a specified number of times.
     *
     * @param times the number of 90-degree rotations to perform.
     * @return the final Direction after rotations.
     */
    public Direction rotateZ(int times) {
        Direction dir = this;
        for (int i = 0; i < times; i++) {
            dir = dir.rotateZ();
        }
        return dir;
    }

    /**
     * Returns the Block adjacent to a given block in the direction of this Direction.
     *
     * @param block the reference block from which to get a relative neighbor.
     * @return the relative Block in the current Direction.
     */
    public Block getRelative(Block block) {
        return block.getWorld().getBlockAt(block.getX() + dx,
                block.getY() + dy,
                block.getZ() + dz);
    }
}