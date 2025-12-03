package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.nms.render.runtime.VisualObject;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class DisplayObject implements VisualObject {

    private final Display display;

    private final float originalViewRange;

    private final Vector3f originalTrans;
    private final Vector3f originalScale;
    private final Quaternionf originalRightRot;

    public DisplayObject(Display display) {
        this.display = display;
        this.originalViewRange = display.getViewRange();
        this.originalTrans = display.getTransformation().getTranslation();
        this.originalScale = display.getTransformation().getScale();
        this.originalRightRot = display.getTransformation().getRightRotation();
    }

    @Override
    public void update(Location location, Quaternionf rotation, Vector3f center, boolean rotateTranslation) {
        if (!display.isValid()) return;

        display.teleport(location);

        Vector3f translation;
        if (rotateTranslation) translation = center.add(originalTrans).rotate(rotation);
        else translation = center.rotate(rotation).add(originalTrans);

        Transformation newTrans = new Transformation(
                translation,
                rotation,
                originalScale,
                originalRightRot
        );

        display.setInterpolationDelay(0);
        display.setTransformation(newTrans);
    }

    @Override
    public void setVisible(boolean visible) {
        if (display.isValid()) {
            display.setViewRange(visible ? originalViewRange : 0);
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