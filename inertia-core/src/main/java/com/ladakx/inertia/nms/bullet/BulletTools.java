package com.ladakx.inertia.nms.bullet;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.utils.MinecraftVersions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

/**
 * BulletTools class is used to get the BulletNMSTools instance.
 */
public class BulletTools {

    /**
     * Get the BulletNMSTools instance.
     * @return BulletNMSTools instance.
     */
    public static BulletNMSTools get() {
        String version = MinecraftVersions.CURRENT.toProtocolString().toLowerCase(Locale.ROOT);
        String path = "com.ladakx.inertia.nms."+version+".BulletTools";

        BulletNMSTools bulletTools = null;
        
        try {
            Class<?> clazz = Class.forName(path);
            Constructor<?> constructor = clazz.getConstructor();
            bulletTools = (BulletNMSTools) constructor.newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            InertiaPlugin.logSevere("(Inertia) The server version you are using is not supported.");
            e.printStackTrace();
        }

        return bulletTools;
    }
}
