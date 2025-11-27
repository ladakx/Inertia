package com.ladakx.inertia.nms.v1_20_r1.render;

import com.ladakx.inertia.nms.render.runtime.VisualObject;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class DisplayObject implements VisualObject {

    private final Display display;

    public DisplayObject(Display display) {
        this.display = display;
    }

    @Override
    public void update(Location location, Quaternionf rotation) {
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
            //display.setViewRange(visible ? originalRange : 0);
        }
    }

    @Override
    public void remove() {
        if (display.isValid()) {
            display.remove();
        }
    }

    @Override
    public boolean isValid() {
        return display.isValid();
    }
}