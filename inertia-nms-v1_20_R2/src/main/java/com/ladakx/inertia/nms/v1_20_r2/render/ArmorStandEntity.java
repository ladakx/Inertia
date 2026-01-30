package com.ladakx.inertia.nms.v1_20_r2.render;

import com.ladakx.inertia.rendering.VisualEntity;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
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
        stand.teleport(location);
        EulerAngle angle = toEulerAngle(rotation);
        stand.setHeadPose(angle);
    }

    @Override
    public void setVisible(boolean visible) {
        if (stand.isValid()) stand.setInvisible(!visible);
    }

    @Override
    public void remove() {
        stand.remove();
    }

    @Override
    public boolean isValid() {
        return stand.isValid();
    }

    @Override
    public void setGlowing(boolean glowing) {
        stand.setGlowing(glowing);
    }
}