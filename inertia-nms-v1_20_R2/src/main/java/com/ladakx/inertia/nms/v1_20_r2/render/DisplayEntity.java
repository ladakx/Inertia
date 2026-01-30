package com.ladakx.inertia.nms.v1_20_r2.render;

import com.ladakx.inertia.rendering.VisualEntity;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class DisplayEntity implements VisualEntity {

    private final Display display;

    public DisplayEntity(Display display) {
        this.display = display;
    }

    @Override
    public void update(Location location, Quaternionf rotation, Vector3f center, boolean rotLocalOff) {
        if (!display.isValid()) return;
        display.teleport(location);

        Transformation current = display.getTransformation();
        Transformation newTrans = new Transformation(
                current.getTranslation(),
                rotation,
                current.getScale(),
                current.getRightRotation()
        );
        
        display.setTransformation(newTrans);
    }

    @Override
    public void setVisible(boolean visible) {
        if (display.isValid()) {
//            display.setViewRange(visible ? originalRange : 0);
        }
    }

    @Override
    public void remove() {
        if (display.isValid()) {
            display.remove();
        }
    }

    @Override
    public void setGlowing(boolean glowing) {
        display.setGlowing(glowing);
    }

    @Override
    public boolean isValid() {
        return display.isValid();
    }
}