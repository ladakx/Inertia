package com.ladakx.inertia.nativelib;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.ladakx.inertia.InertiaPlugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Утилітарний клас для завантаження та **повної ініціалізації** нативних бібліотек Jolt.
 * Адаптовано на основі робочого коду `MinecraftPhysics.java`.
 */
public final class JoltNatives {

    private boolean initialized = false;

    /**
     * Завантажує та ініціалізує нативні бібліотеки Jolt.
     *
     * @param plugin Екземпляр плагіна, необхідний для доступу до ресурсів JAR.
     * @throws JoltNativeException Якщо не вдалося завантажити/ініціалізувати Jolt.
     */
    public void init(JavaPlugin plugin) throws JoltNativeException {
        if (initialized) {
            InertiaPlugin.logInfo("Jolt natives are already loaded and initialized.");
            return;
        }

        // 1. ЗАВАНТАЖЕННЯ БІБЛІОТЕКИ
        InertiaPlugin.logInfo("Attempting to load Jolt JNI native library file...");
        String nativeResourcePath = getNativeLibraryResourcePath();
        if (nativeResourcePath == null) {
            throw new JoltNativeException("Unsupported OS or architecture. Cannot load Jolt JNI.");
        }
        InertiaPlugin.logInfo("Native library path resolved to: " + nativeResourcePath);

        try (InputStream libraryStream = plugin.getResource(nativeResourcePath)) {
            if (libraryStream == null) {
                InertiaPlugin.logSevere("Could not find the native library inside the JAR at path: " + nativeResourcePath);
                throw new JoltNativeException("Native library not found in JAR: " + nativeResourcePath);
            }

            File tempFile = File.createTempFile("libjoltjni", getLibrarySuffix());
            tempFile.deleteOnExit();
            Files.copy(libraryStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            System.load(tempFile.getAbsolutePath());
            InertiaPlugin.logInfo("Jolt JNI native library file loaded successfully!");

        } catch (IOException e) {
            throw new JoltNativeException(new IOException("Could not extract the Jolt JNI native library", e));
        } catch (UnsatisfiedLinkError e) {
            throw new JoltNativeException(new UnsatisfiedLinkError("Failed to link Jolt JNI. Version mismatch or missing dependencies."));
        } catch (Exception e) {
            throw new JoltNativeException(new Exception("An unexpected error occurred while loading Jolt JNI", e));
        }

        // 2. ІНІЦІАЛІЗАЦІЯ JOLT (Адаптовано з робочого коду `MinecraftPhysics.java`)
        try {
            InertiaPlugin.logInfo("Initializing Jolt native environment...");

            // (1) Запускаємо очищувач пам'яті
            JoltPhysicsObject.startCleaner();
            InertiaPlugin.logInfo("JoltPhysicsObject cleaner started.");

            // (2) Реєструємо алокатор за замовчуванням (malloc/free)
            Jolt.registerDefaultAllocator();
            InertiaPlugin.logInfo("Jolt default allocator registered.");

            // (3) Встановлюємо колбеки для логування та помилок
            Jolt.installDefaultAssertCallback();
            Jolt.installDefaultTraceCallback();
            InertiaPlugin.logInfo("Jolt default callbacks installed.");

            // (4) Створюємо "Фабрику" - КРИТИЧНИЙ КРОК
            if (!Jolt.newFactory()) {
                throw new JoltNativeException("Jolt.newFactory() failed. Could not create Jolt Factory.");
            }
            InertiaPlugin.logInfo("Jolt factory created.");

            // (5) Реєструємо всі фізичні типи
            Jolt.registerTypes();
            InertiaPlugin.logInfo("Jolt types registered.");

            // (6) Логуємо версію для підтвердження
            InertiaPlugin.logInfo("Jolt native environment initialized successfully. Jolt Version: " + Jolt.versionString());
            initialized = true;

        } catch (UnsatisfiedLinkError e) {
            InertiaPlugin.logSevere("CRITICAL: Jolt initialization steps failed AFTER loading library.");
            InertiaPlugin.logSevere("This means the loaded .so file is incompatible or corrupted.");
            throw new JoltNativeException(e);
        } catch (Throwable t) {
            InertiaPlugin.logSevere("CRITICAL: Failed during Jolt static initialization block.");
            throw new JoltNativeException(t);
        }
    }

    private String getNativeLibraryResourcePath() {
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

        String packagePath = "com/github/stephengold";
        return String.format("%s/%s/%s/libjoltjni%s", osName, archName, packagePath, getLibrarySuffix());
    }

    private String getLibrarySuffix() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return ".dll";
        }
        if (os.contains("mac")) {
            return ".dylib";
        }
        return ".so";
    }

    /**
     * Внутрішній клас винятків для помилок ініціалізації Jolt.
     */
    public static class JoltNativeException extends Exception {
        public JoltNativeException(String message) {
            super(message);
        }

        public JoltNativeException(Throwable cause) {
            super("Failed to load or initialize Jolt natives", cause);
        }
    }
}