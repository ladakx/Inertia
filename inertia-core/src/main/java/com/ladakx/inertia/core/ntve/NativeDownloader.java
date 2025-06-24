/* Original project path: inertia-core/src/main/java/com/ladakx/inertia/core/ntve/NativeDownloader.java */

package com.ladakx.inertia.core.ntve;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.zip.ZipInputStream;

public class NativeDownloader {

    private final Plugin plugin;
    private final String repoOwner = "ladakx";
    private final String repoName = "Inertia";

    private boolean loaded = false;

    public NativeDownloader(Plugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void load() {
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

    private String getAssetNameForSystem(String os, String arch) {
        // ... (цей метод вже правильний і залишається без змін)
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

    private String getLibraryFileName(String os) {
        if (os.contains("win")) {
            return "inertia_native.dll";
        } else if (os.contains("mac")) {
            return "libinertia_native.dylib";
        } else {
            return "libinertia_native.so";
        }
    }
}