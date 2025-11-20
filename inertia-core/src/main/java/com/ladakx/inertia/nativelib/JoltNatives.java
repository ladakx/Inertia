package com.ladakx.inertia.nativelib;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.ladakx.inertia.InertiaLogger;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Утилітарний клас для завантаження та **повної ініціалізації** нативних бібліотек Jolt.
 */
public final class JoltNatives {

    private boolean initialized = false;

    /**
     * Завантажує та ініціалізує нативні бібліотеки Jolt.
     *
     * @param plugin Екземпляр плагіна, необхідний для доступу до ресурсів JAR.
     * @param precision Яку версію бібліотеки завантажувати (SP чи DP).
     * @throws JoltNativeException Якщо не вдалося завантажити/ініціалізувати Jolt.
     */
    public void init(JavaPlugin plugin, Precision precision) throws JoltNativeException {
        if (initialized) {
            InertiaLogger.info("Jolt natives are already loaded and initialized.");
            return;
        }

        // 1. ЗАВАНТАЖЕННЯ БІБЛІОТЕКИ
        InertiaLogger.info("Attempting to load Jolt JNI native library (Precision: " + precision + ")...");
        String nativeResourcePath = getNativeLibraryResourcePath(precision);

        if (nativeResourcePath == null) {
            throw new JoltNativeException("Unsupported OS or architecture. Cannot load Jolt JNI.");
        }
        InertiaLogger.info("Native library path resolved to: " + nativeResourcePath);

        try (InputStream libraryStream = plugin.getResource(nativeResourcePath)) {
            if (libraryStream == null) {
                InertiaLogger.error("Could not find the native library inside the JAR at path: " + nativeResourcePath);
                InertiaLogger.error("Ensure you have placed the native files in 'src/main/resources/natives/sp' and 'natives/dp'.");
                throw new JoltNativeException("Native library not found in JAR: " + nativeResourcePath);
            }

            // Використовуємо префікс для унікальності тимчасового файлу
            String tempPrefix = (precision == Precision.DP) ? "libjoltjni-dp-" : "libjoltjni-sp-";
            File tempFile = File.createTempFile(tempPrefix, getLibrarySuffix());
            tempFile.deleteOnExit();
            Files.copy(libraryStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            System.load(tempFile.getAbsolutePath());
            InertiaLogger.error("Jolt JNI native library file loaded successfully! (Path: " + tempFile.getAbsolutePath() + ")");

        } catch (IOException e) {
            throw new JoltNativeException(new IOException("Could not extract the Jolt JNI native library", e));
        } catch (UnsatisfiedLinkError e) {
            throw new JoltNativeException(new UnsatisfiedLinkError("Failed to link Jolt JNI. Version mismatch or missing dependencies."));
        } catch (Exception e) {
            throw new JoltNativeException(new Exception("An unexpected error occurred while loading Jolt JNI", e));
        }

        // 2. ІНІЦІАЛІЗАЦІЯ JOLT (Залишається без змін)
        try {
            InertiaLogger.info("Initializing Jolt native environment...");

            // (1) Запускаємо очищувач пам'яті
            JoltPhysicsObject.startCleaner();
            InertiaLogger.info("JoltPhysicsObject cleaner started.");

            // (2) Реєструємо алокатор за замовчуванням (malloc/free)
            Jolt.registerDefaultAllocator();
            InertiaLogger.info("Jolt default allocator registered.");

            // (3) Встановлюємо колбеки для логування та помилок
            Jolt.installDefaultAssertCallback();
            Jolt.installDefaultTraceCallback();
            InertiaLogger.info("Jolt default callbacks installed.");

            // (4) Створюємо "Фабрику" - КРИТИЧНИЙ КРОК
            if (!Jolt.newFactory()) {
                throw new JoltNativeException("Jolt.newFactory() failed. Could not create Jolt Factory.");
            }
            InertiaLogger.info("Jolt factory created.");

            // (5) Реєструємо всі фізичні типи
            Jolt.registerTypes();
            InertiaLogger.info("Jolt types registered.");

            // (6) Логуємо версію для підтвердження
            InertiaLogger.info("JDolt native environment initialized successfully. Jolt Version: " + Jolt.versionString());
            initialized = true;

        } catch (UnsatisfiedLinkError e) {
            InertiaLogger.error("CRITICAL: Jolt initialization steps failed AFTER loading library.");
            InertiaLogger.error("This means the loaded .so file is incompatible or corrupted.");
            throw new JoltNativeException(e);
        } catch (Throwable t) {
            InertiaLogger.error("CRITICAL: Failed during Jolt static initialization block.");
            throw new JoltNativeException(t);
        }
    }

    /**
     * Отримує шлях до ресурсу бібліотеки на основі ОС, архітектури та ТОЧНОСТІ.
     * * @param precision Вибір SP або DP.
     * @return Внутрішній шлях до ресурсу в JAR.
     */
    private String getNativeLibraryResourcePath(Precision precision) {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String osName;
        String archName;

        if (os.contains("win")) {
            osName = "windows";
            archName = "x86-64"; // Jolt-JNI (станом на 3.5.0) не надає aarch64 для Windows
        } else if (os.contains("mac")) {
            osName = "osx";
            archName = (arch.equals("aarch64")) ? "aarch64" : "x86-64";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            osName = "linux";
            archName = (arch.equals("aarch64")) ? "aarch64" : "x86-64";
        } else {
            return null; // Unsupported OS
        }

        // Визначаємо префікс шляху на основі точності
        String precisionPath = (precision == Precision.DP) ? "dp" : "sp";

        // Будуємо новий, унікальний шлях до ресурсу
        return String.format("natives/%s/%s/%s/libjoltjni%s",
                precisionPath, osName, archName, getLibrarySuffix());
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