package com.ladakx.inertia.core.nativelib;

import com.ladakx.inertia.core.InertiaPluginLogger;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Handles the extraction and loading of the Jolt JNI native library.
 * This approach manually extracts the library from the plugin JAR to a temporary file,
 * which is the most reliable way to load native libraries in a Bukkit/Paper environment.
 */
public final class JoltNatives {

    private static boolean loaded = false;

    /**
     * Attempts to load the Jolt native library for the current operating system.
     *
     * @param plugin The JavaPlugin instance, used to access JAR resources.
     * @return {@code true} if the library was successfully loaded, {@code false} otherwise.
     */
    public static boolean load(JavaPlugin plugin) {
        if (loaded) {
            return true;
        }

        InertiaPluginLogger.info("Attempting to load Jolt JNI native library...");

        String nativeResourcePath = getNativeLibraryResourcePath();
        if (nativeResourcePath == null) {
            InertiaPluginLogger.severe("Unsupported OS or architecture. Cannot load Jolt JNI.");
            return false;
        }

        try (InputStream libraryStream = plugin.getResource(nativeResourcePath)) {
            if (libraryStream == null) {
                InertiaPluginLogger.severe("Could not find the native library inside the JAR at path: " + nativeResourcePath);
                InertiaPluginLogger.severe("This is a critical error. The plugin JAR may be corrupted or built incorrectly.");
                return false;
            }

            File tempFile = File.createTempFile("libjoltjni", getLibrarySuffix());
            tempFile.deleteOnExit();

            Files.copy(libraryStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            System.load(tempFile.getAbsolutePath());
            loaded = true;

            InertiaPluginLogger.info("Jolt JNI native library loaded successfully!");
            return true;

        } catch (IOException e) {
            InertiaPluginLogger.severe("Could not extract the Jolt JNI native library: " + e.getMessage());
            e.printStackTrace();
        } catch (UnsatisfiedLinkError e) {
            InertiaPluginLogger.severe("Failed to link the Jolt JNI native library. This might be due to a version mismatch or missing dependencies.");
            InertiaPluginLogger.severe("Error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            InertiaPluginLogger.severe("An unexpected error occurred while loading the Jolt JNI native library: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Determines the correct resource path to the native library within the JAR.
     * This path is specific to how jolt-jni v3.1.0+ structures its native JARs.
     *
     * @return The resource path, or {@code null} if unsupported.
     */
    private static String getNativeLibraryResourcePath() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String osName;
        String archName = "x86-64"; // Default for AMD64/x86_64

        if (os.contains("win")) {
            osName = "windows";
        } else if (os.contains("mac")) {
            osName = "osx";
            if (arch.equals("aarch64")) {
                archName = "aarch64";
            }
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            osName = "linux";
            if (arch.equals("aarch64")) {
                archName = "aarch64";
            }
        } else {
            return null; // Unsupported OS
        }

        // This path is based on the internal structure of the native JARs for v3.1.0
        // Example: osx/aarch64/com/github/stephengold/libjoltjni.dylib
        String packagePath = "com/github/stephengold";
        return String.format("%s/%s/%s/libjoltjni%s", osName, archName, packagePath, getLibrarySuffix());
    }

    /**
     * Gets the file suffix for native libraries on the current OS.
     *
     * @return The library file suffix (e.g., ".dll", ".so", ".dylib").
     */
    private static String getLibrarySuffix() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return ".dll";
        }
        if (os.contains("mac")) {
            return ".dylib";
        }
        return ".so";
    }
}

