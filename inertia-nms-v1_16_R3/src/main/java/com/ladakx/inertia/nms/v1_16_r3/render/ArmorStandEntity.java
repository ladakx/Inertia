package com.ladakx.inertia.nms.v1_16_r3.render;

import com.ladakx.inertia.rendering.VisualEntity;
import net.minecraft.server.v1_16_R3.EntityArmorStand;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftArmorStand;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.EulerAngle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.ladakx.inertia.common.utils.RotationUtils.toEulerAngle;

public class ArmorStandEntity implements VisualEntity {
    private final ArmorStand stand;

    public ArmorStandEntity(ArmorStand stand) {
        this.stand = stand;
    }

    @Override
    public void update(Location location, Quaternionf rotation, Vector3f center, boolean rotLocalOff) {
        if (!stand.isValid()) return;

        // Optimization: Use NMS direct setLocation if within the same chunk to avoid heavy Bukkit teleport overhead
        int oldCX = stand.getLocation().getBlockX() >> 4;
        int oldCZ = stand.getLocation().getBlockZ() >> 4;
        int newCX = location.getBlockX() >> 4;
        int newCZ = location.getBlockZ() >> 4;

        if (oldCX == newCX && oldCZ == newCZ) {
            EntityArmorStand handle = ((CraftArmorStand) stand).getHandle();
            // setLocation updates position, bounding box and chunk position references lightly
            handle.setLocation(
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch()
            );
            // The server's entity tracker will detect the position change in the next tick and send packets automatically.
        } else {
            stand.teleport(location);
        }

        EulerAngle angle = toEulerAngle(rotation);
        stand.setHeadPose(angle);
    }

    @Override
    public void setVisible(boolean visible) {
        if (!stand.isValid()) return;
        stand.setInvisible(!visible);
    }

    @Override
    public void remove() {
        if (stand.isValid()) stand.remove();
    }

    @Override
    public boolean isValid() {
        return stand.isValid();
    }

    @Override
    public PersistentDataContainer getPersistentDataContainer() {
        return stand.getPersistentDataContainer();
    }

    @Override
    public void setGlowing(boolean glowing) {
        stand.setGlowing(glowing);
    }

    @Override
    public boolean getPersistent() {
        return stand.isPersistent();
    }

    @Override
    public void setPersistent(boolean persistent) {
        stand.setPersistent(persistent);
    }
}