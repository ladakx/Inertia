/* New file: inertia-core/src/main/java/com/ladakx/inertia/core/ntve/NativeLibraryManager.java */

package com.ladakx.inertia.core.ntve;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.ZipInputStream;

public class NativeLibraryManager {

    // --- ОСНОВНИЙ ПЕРЕМИКАЧ ---
    // false = завантажувати бібліотеку з JAR-файлу (для локальної розробки)
    // true = завантажувати бібліотеку з GitHub Releases (для production)
    private static final boolean LOAD_FROM_GITHUB = false;

    private final Plugin plugin;
    private boolean loaded = false;

    private final String repoOwner = "ladakx";
    private final String repoName = "Inertia";

    public NativeLibraryManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void load() {
        if (loaded) {
            return;
        }

        if (LOAD_FROM_GITHUB) {
            loadFromGitHub();
        } else {
            loadFromJar();
        }

        loaded = true;
    }

    private void loadFromJar() {
        plugin.getLogger().info("Loading native library from JAR...");
        try {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            String libFileName = getLibraryFileName(os);
            String libPath = "/native/" + getOsFolder(os) + "/" + libFileName;

            try (InputStream is = getClass().getResourceAsStream(libPath)) {
                if (is == null) {
                    throw new IllegalStateException("Native library not found in JAR for this OS: " + libPath);
                }

                File tempFile = File.createTempFile("inertia", ".tmp");
                tempFile.deleteOnExit();

                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.load(tempFile.getAbsolutePath());
                plugin.getLogger().info("Successfully loaded native library from JAR: " + libFileName);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("CRITICAL: FAILED TO LOAD INERTIA NATIVE LIBRARY FROM JAR!");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void loadFromGitHub() {
        if (loaded) {
            return;
        }

        try {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

            String assetName = getAssetNameForSystem(os, arch);
            File nativesDir = new File(plugin.getDataFolder(), "natives");
            if (!nativesDir.exists()) {
                nativesDir.mkdirs();
            }

            File libFile = new File(nativesDir, getLibraryFileName(os));

            if (libFile.exists()) {
                System.load(libFile.getAbsolutePath());
                plugin.getLogger().info("Successfully loaded cached native library: " + libFile.getName());
                loaded = true;
                return;
            }

            plugin.getLogger().info("Downloading native library from GitHub Releases for " + os + " " + arch + "...");
            URL downloadUrl = new URL(String.format("https://github.com/%s/%s/releases/latest/download/%s.zip", repoOwner, repoName, assetName));

            HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "InertiaPlugin/1.0");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = connection.getInputStream();
                     ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

                    if (zipInputStream.getNextEntry() != null) {
                        try (FileOutputStream fos = new FileOutputStream(libFile)) {
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = zipInputStream.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }

                        // --- ФІНАЛЬНЕ ВИПРАВЛЕННЯ ---
                        // Надаємо файлу права на виконання. Це критично для Linux та macOS.
                        libFile.setExecutable(true, true);

                        plugin.getLogger().info("Successfully downloaded and extracted native library: " + libFile.getName());
                        System.load(libFile.getAbsolutePath());
                        loaded = true;
                    } else {
                        throw new IllegalStateException("Downloaded zip archive is empty.");
                    }
                }
            } else {
                throw new IllegalStateException("Failed to download native library. Response code: " + responseCode + " for URL: " + downloadUrl);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("CRITICAL: FAILED TO LOAD INERTIA NATIVE LIBRARY!");
            e.printStackTrace();
            throw new RuntimeException("Could not load native library for Inertia.", e);
        }
    }

    // Допоміжні методи
    private String getOsFolder(String os) {
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "darwin";
        return "linux";
    }

    private String getLibraryFileName(String os) {
        if (os.contains("win")) return "inertia_native.dll";
        if (os.contains("mac")) return "libinertia_native.dylib";
        return "libinertia_native.so";
    }

    private String getAssetNameForSystem(String os, String arch) {
        String osName;
        if (os.contains("win")) {
            osName = "windows";
        } else if (os.contains("mac")) {
            osName = "macos";
        } else {
            osName = "linux";
        }

        String archName;
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            archName = "x64";
            if (osName.equals("linux")) {
                archName = "x86_64";
            }
        } else if (arch.equals("aarch64")) {
            archName = "aarch64";
        } else if (arch.equals("arm64")) {
            archName = "arm64";
        }
        else {
            throw new UnsupportedOperationException("Unsupported architecture: " + arch);
        }

        return String.format("inertia-%s-%s", osName, archName);
    }
}