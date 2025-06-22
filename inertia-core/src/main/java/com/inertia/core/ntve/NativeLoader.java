// com/inertia/core/native/NativeLoader.java
package com.inertia.core.ntve;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class NativeLoader {

    public static void load() {
        try {
            // На macOS бібліотека матиме розширення .dylib
            String libraryName = "libinertia.dylib";
            String os = System.getProperty("os.name").toLowerCase();
            String osFolder = "unknown";

            if (os.contains("mac")) {
                osFolder = "darwin";
            } else if (os.contains("win")) {
                osFolder = "windows";
                libraryName = "inertia.dll"; // Назва для Windows
            } else if (os.contains("nix") || os.contains("nux")) {
                osFolder = "linux";
                libraryName = "libinertia.so"; // Назва для Linux
            }

            // Шлях до бібліотеки всередині JAR-файлу
            String libraryPath = "/native/" + osFolder + "/" + libraryName;

            // Отримуємо потік для читання файлу з ресурсів
            InputStream inputStream = NativeLoader.class.getResourceAsStream(libraryPath);
            if (inputStream == null) {
                throw new IllegalStateException("Native library not found for this OS: " + libraryPath);
            }

            // Створюємо тимчасовий файл, куди ми скопіюємо бібліотеку
            Path tempFile = Files.createTempFile("inertia", ".tmp");

            // Копіюємо бібліотеку з JAR на диск
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

            // Завантажуємо бібліотеку в JVM з тимчасового файлу
            System.load(tempFile.toAbsolutePath().toString());

            System.out.println("Successfully loaded Inertia native library!");

        } catch (Exception e) {
            System.err.println("Failed to load Inertia native library!");
            e.printStackTrace();
        }
    }
}
