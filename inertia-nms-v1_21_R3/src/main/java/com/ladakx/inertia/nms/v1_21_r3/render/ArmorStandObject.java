package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.nms.render.runtime.VisualObject;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.ladakx.inertia.utils.RotationUtils.toEulerAngle;

public class ArmorStandObject implements VisualObject {
    private final ArmorStand stand;

    public ArmorStandObject(ArmorStand stand) {
        this.stand = stand;
    }

    @Override
    public void update(Location location, Quaternionf rotation) {
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