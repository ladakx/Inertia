package com.ladakx.inertia.infrastructure.nativelib;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.ladakx.inertia.common.logging.InertiaLogger;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class LibraryLoader {

    private static final String GITHUB_BASE_URL = "https://github.com/ladakx/jolt-jni/releases/download/";
    private static final String RELEASE_TAG = "3.6.0";
    private static final String STATE_FILE_NAME = ".native-loader-state";

    private boolean initialized = false;

    public void init(JavaPlugin plugin, Precision precision, NativeLibrarySettings settings) throws JoltNativeException {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(precision, "precision");
        Objects.requireNonNull(settings, "settings");

        if (initialized) {
            InertiaLogger.info("Jolt natives are already loaded.");
            return;
        }

        final List<String> candidates = resolveFileNames(precision, settings);
        final File dataFolder = plugin.getDataFolder();
        final File nativesDir = new File(dataFolder, "natives");

        if (!nativesDir.exists() && !nativesDir.mkdirs()) {
            throw new JoltNativeException("Failed to create directory: " + nativesDir.getAbsolutePath());
        }

        final String loaderState = buildLoaderState(precision, settings);
        cleanupNativesIfStateChanged(nativesDir, loaderState);

        final List<Throwable> errors = new ArrayList<>();
        for (String candidate : candidates) {
            final File nativeLibFile = new File(nativesDir, candidate);
            try {
                ensureLibraryPresent(nativeLibFile, candidate);
                System.load(nativeLibFile.getAbsolutePath());
                InertiaLogger.info("Native library loaded successfully: " + candidate);
                initializeJoltRuntime();
                persistLoaderState(nativesDir, loaderState);
                initialized = true;
                return;
            } catch (Throwable throwable) {
                errors.add(throwable);
                InertiaLogger.warn("Failed to load native candidate: " + candidate + ". Trying next candidate if available.");
            }
        }

        JoltNativeException exception = new JoltNativeException("Unable to load any native library candidate: " + String.join(", ", candidates));
        for (Throwable error : errors) {
            exception.addSuppressed(error);
        }
        throw exception;
    }

    private void ensureLibraryPresent(File nativeLibFile, String fileName) throws IOException {
        if (nativeLibFile.exists()) {
            InertiaLogger.info("Found local native library: " + nativeLibFile.getAbsolutePath());
            return;
        }

        InertiaLogger.info("Native library not found locally. Downloading from GitHub...");
        InertiaLogger.info("Target: " + fileName);

        String downloadUrl = GITHUB_BASE_URL + RELEASE_TAG + "/" + fileName;
        downloadFile(downloadUrl, nativeLibFile);
        InertiaLogger.info("Download complete: " + nativeLibFile.getAbsolutePath());
    }

    private String buildLoaderState(Precision precision, NativeLibrarySettings settings) {
        return "release=" + RELEASE_TAG
                + ";precision=" + precision.name()
                + ";preferWindowsAvx2=" + settings.preferWindowsAvx2()
                + ";preferLinuxFma=" + settings.preferLinuxFma()
                + ";allowLegacyCpuFallback=" + settings.allowLegacyCpuFallback();
    }

    private void cleanupNativesIfStateChanged(File nativesDir, String loaderState) throws IOException, JoltNativeException {
        final File stateFile = new File(nativesDir, STATE_FILE_NAME);
        if (!stateFile.exists()) {
            return;
        }

        final String previousState = Files.readString(stateFile.toPath(), StandardCharsets.UTF_8);
        if (previousState.equals(loaderState)) {
            return;
        }

        final File[] files = nativesDir.listFiles();
        if (files == null) {
            throw new JoltNativeException("Failed to list native directory for cleanup: " + nativesDir.getAbsolutePath());
        }

        for (File file : files) {
            final String fileName = file.getName();
            if (!file.isFile()) {
                continue;
            }
            if (!fileName.startsWith("jolt-jni-") && !fileName.startsWith("libjolt-jni-")) {
                continue;
            }
            if (!file.delete()) {
                throw new JoltNativeException("Failed to delete stale native file: " + file.getAbsolutePath());
            }
        }
    }

    private void persistLoaderState(File nativesDir, String loaderState) throws IOException {
        final File stateFile = new File(nativesDir, STATE_FILE_NAME);
        Files.writeString(stateFile.toPath(), loaderState, StandardCharsets.UTF_8);
    }

    private List<String> resolveFileNames(Precision precision, NativeLibrarySettings settings) throws JoltNativeException {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        final String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        final String precisionSuffix = precision == Precision.DP ? "Dp" : "Sp";
        final List<String> candidates = new ArrayList<>();

        if (os.contains("win")) {
            if (settings.preferWindowsAvx2()) {
                candidates.add("jolt-jni-Windows64_avx2-" + precisionSuffix + ".dll");
            }
            if (!settings.preferWindowsAvx2() || settings.allowLegacyCpuFallback()) {
                candidates.add("jolt-jni-Windows64-" + precisionSuffix + ".dll");
            }
            return deduplicate(candidates);
        }

        if (os.contains("nix") || os.contains("nux") || os.contains("aix") || os.contains("linux")) {
            if (isLinuxX64(arch)) {
                if (settings.preferLinuxFma()) {
                    candidates.add("libjolt-jni-Linux64_fma-" + precisionSuffix + ".so");
                }
                if (!settings.preferLinuxFma() || settings.allowLegacyCpuFallback()) {
                    candidates.add("libjolt-jni-Linux64-" + precisionSuffix + ".so");
                }
                return deduplicate(candidates);
            }

            if (isLinuxArm32(arch)) {
                candidates.add("libjolt-jni-Linux_ARM32hf-" + precisionSuffix + ".so");
                return candidates;
            }

            if (isLinuxArm64(arch)) {
                candidates.add("libjolt-jni-Linux_ARM64-" + precisionSuffix + ".so");
                return candidates;
            }

            if (isLinuxLoongArch64(arch)) {
                candidates.add("libjolt-jni-Linux_LoongArch64-" + precisionSuffix + ".so");
                return candidates;
            }

            throw new JoltNativeException("Unsupported Linux architecture: " + arch);
        }

        if (os.contains("mac")) {
            if (arch.equals("aarch64") || arch.contains("arm64")) {
                candidates.add("libjolt-jni-MacOSX_ARM64-" + precisionSuffix + ".dylib");
            } else {
                candidates.add("libjolt-jni-MacOSX64-" + precisionSuffix + ".dylib");
            }
            return candidates;
        }

        throw new JoltNativeException("Unsupported OS: " + os);
    }

    private List<String> deduplicate(List<String> source) {
        List<String> unique = new ArrayList<>();
        for (String entry : source) {
            if (!unique.contains(entry)) {
                unique.add(entry);
            }
        }
        return unique;
    }

    private boolean isLinuxX64(String arch) {
        return arch.equals("x86_64") || arch.equals("amd64") || arch.equals("x64");
    }

    private boolean isLinuxArm32(String arch) {
        return arch.equals("arm") || arch.equals("armv7l") || arch.equals("arm32") || arch.equals("armhf");
    }

    private boolean isLinuxArm64(String arch) {
        return arch.equals("aarch64") || arch.equals("arm64");
    }

    private boolean isLinuxLoongArch64(String arch) {
        return arch.equals("loongarch64");
    }

    private void downloadFile(String fileUrl, File destination) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

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

    public static final class NativeLibrarySettings {
        private final boolean preferWindowsAvx2;
        private final boolean preferLinuxFma;
        private final boolean allowLegacyCpuFallback;

        public NativeLibrarySettings(boolean preferWindowsAvx2, boolean preferLinuxFma, boolean allowLegacyCpuFallback) {
            this.preferWindowsAvx2 = preferWindowsAvx2;
            this.preferLinuxFma = preferLinuxFma;
            this.allowLegacyCpuFallback = allowLegacyCpuFallback;
        }

        public boolean preferWindowsAvx2() {
            return preferWindowsAvx2;
        }

        public boolean preferLinuxFma() {
            return preferLinuxFma;
        }

        public boolean allowLegacyCpuFallback() {
            return allowLegacyCpuFallback;
        }
    }
}
