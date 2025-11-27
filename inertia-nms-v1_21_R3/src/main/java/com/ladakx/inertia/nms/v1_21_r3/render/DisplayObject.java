package com.ladakx.inertia.nms.v1_21_r3.render;

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
//        if (current.getTranslation().x == 0.0f && current.getTranslation().y == 0.0f && current.getTranslation().z == 0.0f) {
            Transformation newTrans = new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f).rotate(rotation),
                    rotation,
                    current.getScale(),
                    current.getRightRotation()
            );

            display.setInterpolationDelay(0);
            display.setTransformation(newTrans);

//            return;
//        }
//
//        Transformation newTrans = new Transformation(
//                current.getTranslation(),
//                rotation,
//                current.getScale(),
//                current.getRightRotation()
//        );

//        display.setInterpolationDelay(0);
//        display.setTransformation(newTrans);
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
    public boolean isValid() {
        return display.isValid();
    }
}