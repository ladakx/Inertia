/*
 * Copyright (c) 2009-2019 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.ladakx.inertia;

import com.jme3.bullet.util.NativeLibrary;
import com.jme3.system.JmeSystem;
import com.jme3.system.Platform;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class to load the JBullet native library
 */
public final class NativeLibraryLoader {

    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(NativeLibraryLoader.class.getName());

    /**
     * The directory to store the natives
     */
    private final File nativesDir;
    /**
     * The directory to store the temporary natives
     */
    private final File nativesTmpDir;

    /**
     * The build type
     */
    private final String buildType = "Release";
    /**
     * The flavor
     */
    private final String flavor = "Sp";
    /**
     * The current version
     */
    private final String currentVersion = NativeLibrary.expectedVersion;

    /**
     * Create a new native library loader
     * @param javaPlugin the plugin
     */
    public NativeLibraryLoader(JavaPlugin javaPlugin) {
        this.nativesDir = new File(javaPlugin.getDataFolder(), ".cache");
        this.nativesTmpDir = new File(System.getProperty("java.io.tmpdir"), "BulletPhysicsNatives");
    }

    /**
     * Initialize the native library
     * @throws IOException if an error occurs
     */
    public void init() throws IOException {
        // Load the library
        File libFile = new File(nativesDir, getFileName());

        // Copy the library
        if (!libFile.exists()) {
            libFile.getParentFile().mkdirs();
            Files.copy(getUrl().openStream(), libFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // Load the library
        try {
            load(libFile);
        } catch (UnsatisfiedLinkError ex) {
            ex.printStackTrace();
            load(tempCopy(libFile));
        }
    }

    /**
     * Get the file name of the library for the current platform and version
     * @return the file name
     */
    public String getFileName() {
        Platform platform = JmeSystem.getPlatform();

        String ext = switch (platform) {
            case Windows32, Windows64, Windows_ARM32, Windows_ARM64 -> ".dll";
            case Android_ARM7, Android_ARM8, Linux32, Linux64, Linux_ARM32, Linux_ARM64 -> ".so";
            case MacOSX32, MacOSX64, MacOSX_ARM64 -> ".dylib";
            default -> throw new RuntimeException("platform = " + platform);
        };
        return "libbulletjme_v" + currentVersion + "_" + platform.name().toLowerCase() + "_" + buildType.toLowerCase() + "_" + flavor.toLowerCase() + ext;
    }

    private boolean isLibraryLoaded(String partialName) {
        try {
            Field field = ClassLoader.class.getDeclaredField("loadedLibraryNames");
            field.setAccessible(true);

            // Получаем список библиотек, загруженных системным загрузчиком
            @SuppressWarnings("unchecked")
            Vector<String> libs = (Vector<String>) field.get(ClassLoader.getSystemClassLoader());

            // Ищем совпадение
            for (String lib : libs) {
                if (lib.contains(partialName)) {
                    return true;
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            // Либо логируем, либо игнорируем
            ex.printStackTrace();
        }
        return false;
    }

    /**
     * Get the URL of the library for the current platform and version
     * @return the URL
     * @throws MalformedURLException if an error occurs
     */
    @SuppressWarnings({"ConstantConditions", "ConditionCoveredByFurtherCondition"})
    public URL getUrl() throws MalformedURLException {
        // Check the build type and flavor
        assert buildType.equals("Debug") || buildType.equals("Release") :
                buildType;
        // Check the flavor
        assert flavor.equals("Sp") || flavor.equals("SpMt")
                || flavor.equals("SpMtQuickprof")
                || flavor.equals("SpQuickprof")
                || flavor.equals("Dp") || flavor.equals("DpMt") : flavor;

        // Get the platform
        Platform platform = JmeSystem.getPlatform();

        // Get the name
        String name = switch (platform) {
            case Windows32, Windows64, Windows_ARM32, Windows_ARM64 -> "bulletjme.dll";
            case Android_ARM7, Android_ARM8, Linux32, Linux64, Linux_ARM32, Linux_ARM64 -> "libbulletjme.so";
            case MacOSX32, MacOSX64, MacOSX_ARM64 -> "libbulletjme.dylib";
            default -> throw new RuntimeException("platform = " + platform);
        };

        // Return the URL
        return new URL("https://github.com/stephengold/Libbulletjme/releases/download/" + currentVersion + "/" + platform + buildType + flavor + "_" + name);
    }

    /**
     * Copy the library to a temporary file
     * @param file the file
     * @return the temporary file
     * @throws IOException if an error occurs
     */
    public File tempCopy(File file) throws IOException {
        nativesTmpDir.mkdirs();
        File res = new File(nativesTmpDir, UUID.randomUUID() + "_" + file.getName());
        Files.copy(file.toPath(), res.toPath());
        res.deleteOnExit();
        return res;
    }

    /**
     * Load the library
     * @param file the file
     * @return true if the library was loaded
     */
    public boolean load(File file) {
        // Get the absolute file name
        String absoluteFilename = file.getAbsolutePath();
        boolean success = false;

        // Check if the file exists
        if (!file.exists()) {
            logger.log(Level.SEVERE, "{0} does not exist", absoluteFilename);
        } else if (!file.canRead()) {
            logger.log(Level.SEVERE, "{0} is not readable", absoluteFilename);
        } else {
            logger.log(Level.INFO, "Loading native library from {0}", absoluteFilename);

            try {System.load(absoluteFilename);
            } catch (UnsatisfiedLinkError ignored) {}

            success = true;
        }

        // Return the success
        return success;
    }
}
