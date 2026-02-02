package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.rendering.VisualEntity;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftDisplay;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class DisplayEntity implements VisualEntity {
    private final Display display;
    private final boolean isBlockDisplay;
    private final float originalViewRange;
    private final Vector3f originalTrans;
    private final Vector3f originalScale;
    private final Quaternionf originalRightRot;

    public DisplayEntity(Display display) {
        this.display = display;
        this.isBlockDisplay = display instanceof BlockDisplay;
        this.originalViewRange = display.getViewRange();
        this.originalTrans = display.getTransformation().getTranslation();
        this.originalScale = display.getTransformation().getScale();
        this.originalRightRot = display.getTransformation().getRightRotation();
    }

    @Override
    public void update(Location location, Quaternionf rotation, Vector3f center, boolean rotateTranslation) {
        if (!display.isValid()) return;

        // Optimization: Fast path for in-chunk movement
        int oldCX = display.getLocation().getBlockX() >> 4;
        int oldCZ = display.getLocation().getBlockZ() >> 4;
        int newCX = location.getBlockX() >> 4;
        int newCZ = location.getBlockZ() >> 4;

        if (oldCX == newCX && oldCZ == newCZ) {
            net.minecraft.world.entity.Display handle = ((CraftDisplay) display).getHandle();
            handle.moveTo(
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch()
            );
        } else {
            display.teleport(location);
        }

        Vector3f translation;
        if (isBlockDisplay) {
            if (rotateTranslation) translation = center.add(originalTrans).rotate(rotation);
            else translation = center.rotate(rotation).add(originalTrans);
        } else {
            translation = originalTrans;
        }

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
    public PersistentDataContainer getPersistentDataContainer() {
        return display.getPersistentDataContainer();
    }

    @Override
    public boolean isValid() {
        return display.isValid();
    }

    @Override
    public boolean getPersistent() {
        return display.isPersistent();
    }

    @Override
    public void setPersistent(boolean persistent) {
        display.setPersistent(persistent);
    }
}