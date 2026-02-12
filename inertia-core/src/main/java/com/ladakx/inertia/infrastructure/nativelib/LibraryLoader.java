package com.ladakx.inertia.infrastructure.nativelib;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.ladakx.inertia.common.logging.InertiaLogger;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Утилітарний клас для завантаження нативних бібліотек Jolt з GitHub Releases.
 */
public final class LibraryLoader {

    private static final String GITHUB_BASE_URL = "https://github.com/ladakx/jolt-jni/releases/download/";
    private static final String VERSION_TAG = "3.6.0";

    private boolean initialized = false;

    /**
     * Завантажує бібліотеку з GitHub (або локального кешу) та ініціалізує Jolt.
     */
    public void init(JavaPlugin plugin, Precision precision) throws JoltNativeException {
        if (initialized) {
            InertiaLogger.info("Jolt natives are already loaded.");
            return;
        }

        try {
            // 1. Визначення імені файлу та URL
            String fileName = resolveFileName(precision);
            File dataFolder = plugin.getDataFolder();
            File nativesDir = new File(dataFolder, "natives");
            File nativeLibFile = new File(nativesDir, fileName);

            // 2. Перевірка наявності або завантаження
            if (!nativeLibFile.exists()) {
                InertiaLogger.info("Native library not found locally. Downloading from GitHub...");
                InertiaLogger.info("Target: " + fileName);

                if (!nativesDir.exists() && !nativesDir.mkdirs()) {
                    throw new IOException("Failed to create directory: " + nativesDir.getAbsolutePath());
                }

                String downloadUrl = GITHUB_BASE_URL + VERSION_TAG + "/" + fileName;
                downloadFile(downloadUrl, nativeLibFile);

                InertiaLogger.info("Download complete: " + nativeLibFile.getAbsolutePath());
            } else {
                InertiaLogger.info("Found local native library: " + nativeLibFile.getAbsolutePath());
            }

            // 3. Завантаження бібліотеки в пам'ять (System.load)
            try {
                System.load(nativeLibFile.getAbsolutePath());
                InertiaLogger.info("Native library loaded successfully.");
            } catch (UnsatisfiedLinkError e) {
                // Спроба видалити пошкоджений файл, щоб наступного разу завантажити заново
                InertiaLogger.error("Failed to load library. It might be corrupted. Deleting file...");
                nativeLibFile.delete();
                throw e;
            }

            // 4. Ініціалізація Jolt (стандартна процедура)
            initializeJoltRuntime();

            initialized = true;

        } catch (Exception e) {
            throw new JoltNativeException(e);
        }
    }

    /**
     * Формує назву файлу на основі списку активів у вашому релізі.
     * Логіка складна через різницю в іменуванні для Mac та Win/Linux.
     */
    private String resolveFileName(Precision precision) throws JoltNativeException {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String precString = (precision == Precision.DP) ? "dp" : "sp";

        // Windows
        if (os.contains("win")) {
            // Формат: jolt-jni-windows-{dp|sp}-release.dll
            // Примітка: Ми беремо 'release' версію, не debug
            return String.format("jolt-jni-windows-%s-release.dll", precString);
        }

        // Linux
        else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            // Формат: libjolt-jni-linux-{dp|sp}-release.so
            return String.format("libjolt-jni-linux-%s-release.so", precString);
        }

        // MacOS
        else if (os.contains("mac")) {
            // Формат: libjolt-jni-macos-{arch}-{dp|sp}.dylib
            // У вашому списку немає суфікса "-release" для Mac
            String macArch = arch.equals("aarch64") || arch.contains("arm") ? "arm64" : "intel";
            return String.format("libjolt-jni-macos-%s-%s.dylib", macArch, precString);
        }

        else {
            throw new JoltNativeException("Unsupported OS: " + os);
        }
    }

    /**
     * Завантажує файл за URL і зберігає його на диск.
     */
    private void downloadFile(String fileUrl, File destination) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        // Обробка редіректів (GitHub Release часто робить редірект на AWS)
        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
            String newUrl = connection.getHeaderField("Location");
            connection = (HttpURLConnection) new URL(newUrl).openConnection();
        }

        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream fileOutputStream = new FileOutputStream(destination)) {

            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
    }

    private void initializeJoltRuntime() {
        InertiaLogger.info("Initializing Jolt objects...");
        JoltPhysicsObject.startCleaner();
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();

        if (!Jolt.newFactory()) {
            throw new RuntimeException("Jolt.newFactory() failed.");
        }
        Jolt.registerTypes();
        InertiaLogger.info("Jolt initialized! Version: " + Jolt.versionString());
    }

    public static class JoltNativeException extends Exception {
        public JoltNativeException(String message) { super(message); }
        public JoltNativeException(Throwable cause) { super(cause); }
    }
}