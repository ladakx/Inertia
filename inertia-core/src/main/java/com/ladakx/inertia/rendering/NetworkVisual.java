package com.ladakx.inertia.rendering;

import org.bukkit.Location;
import org.joml.Quaternionf;

public interface NetworkVisual {

    int getId();

    /**
     * Creates the packet(s) required to spawn this visual.
     * Returns a single Packet or a BundlePacket.
     * * @param location The current location of the visual
     * @param rotation The current rotation (left_rotation) of the visual
     */
    Object createSpawnPacket(Location location, Quaternionf rotation);

    /**
     * Creates the packet required to destroy this visual.
     */
    Object createDestroyPacket();

    /**
     * Creates the packet required to update the position and rotation.
     */
    Object createTeleportPacket(Location location, Quaternionf rotation);

    /**
     * Creates the packet required to update metadata (flags, state, etc).
     */
    Object createMetadataPacket();

    void setGlowing(boolean glowing);
}