package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.rendering.VisualEntity;
import org.bukkit.Location;
import org.bukkit.persistence.PersistentDataContainer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class EmptyVisual implements VisualEntity {
    @Override public void update(Location location, Quaternionf rotation, Vector3f center, boolean rotLocalOff) {}
    @Override public void setVisible(boolean visible) {}
    @Override public void remove() {}
    @Override public void setGlowing(boolean glowing) {}
    @Override public PersistentDataContainer getPersistentDataContainer() { return null; }
    @Override public boolean isValid() { return false; }
    @Override public boolean getPersistent() { return false; }
    @Override public void setPersistent(boolean persistent) {}
}