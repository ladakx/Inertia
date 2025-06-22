/* Original project path: inertia-core/src/main/java/com/ladakx/inertia/core/ntve/NativeLoader.java */

package com.ladakx.inertia.core.ntve;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class NativeLoader {

    private static boolean loaded = false;
    // THIS IS THE FIX: The library name must match the one in CMakeLists.txt
    private static final String LIB_NAME = "inertia_native";

    public static synchronized void load() {
        if (loaded) {
            return;
        }

        try {
            final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            String extension;
            String osFolder;

            if (os.contains("win")) {
                osFolder = "windows";
                extension = ".dll";
            } else if (os.contains("mac")) {
                osFolder = "darwin";
                extension = ".dylib";
            } else {
                osFolder = "linux";
                extension = ".so";
            }

            String libFileName = "lib" + LIB_NAME + extension;
            String libPath = "/native/" + osFolder + "/" + libFileName;

            try (InputStream is = NativeLoader.class.getResourceAsStream(libPath)) {
                if (is == null) {
                    throw new IllegalStateException("Native library not found for this OS: " + libPath);
                }

                Path tempFile = Files.createTempFile(LIB_NAME, extension);
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                System.load(tempFile.toAbsolutePath().toString());
                loaded = true;
            }

        } catch (Exception e) {
            System.err.println("Failed to load Inertia native library!");
            e.printStackTrace();
            throw new RuntimeException("Could not load native library", e);
        }
    }
}