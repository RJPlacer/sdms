package com.sdms.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Centralized writable paths for installed/runtime environments.
 */
public final class AppPaths {

    private static final String APP_NAME = "Athlete Track";
    private static final String LEGACY_APP_NAME = "SDMS";
    private AppPaths() {}

    public static Path appDir() { return ensureDir(resolveAppDir()); }

    public static Path dbPath() { return appDir().resolve("sdms.db"); }

    public static Path dataDir() { return ensureDir(appDir().resolve("data")); }

    public static Path photosDir() { return ensureDir(dataDir().resolve("photos")); }

    public static Path qrCodesDir() { return ensureDir(dataDir().resolve("qrcodes")); }

    public static Path exportsDir() { return ensureDir(dataDir().resolve("exports")); }

    public static Path logsDir() { return ensureDir(appDir().resolve("logs")); }

    public static Path sharingConfigPath() { return appDir().resolve("sharing.properties"); }

    public static Path sharedDatabaseDir() { return ensureDir(appDir().resolve("shared-database")); }

    public static Path sharedAssetsDir() { return ensureDir(appDir().resolve("shared-assets")); }

    public static Path sharedPhotosDir() { return ensureDir(sharedAssetsDir().resolve("photos")); }

    public static Path sharedQrCodesDir() { return ensureDir(sharedAssetsDir().resolve("qrcodes")); }

    public static Path cacheDir() { return ensureDir(appDir().resolve("cache")); }

    public static Path backupsDir() { return ensureDir(appDir().resolve("backups")); }

    private static Path resolveAppDir() {
        String override = System.getProperty("sdms.appDir");
        if (override != null && !override.isBlank()) return Paths.get(override);

        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                Path preferred = Paths.get(localAppData, APP_NAME);
                Path legacy = Paths.get(localAppData, LEGACY_APP_NAME);
                if (Files.exists(preferred)) return preferred;
                if (Files.exists(legacy)) return legacy;
                return preferred;
            }
            return Paths.get(System.getProperty("user.home"), "AppData", "Local", APP_NAME);
        }

        if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", APP_NAME);
        }

        return Paths.get(System.getProperty("user.home"), ".sdms");
    }

    private static Path ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create app directory: " + dir, e);
        }
    }
}
