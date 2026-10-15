package com.sdms.service;

import com.sdms.sharing.SharingConfig;
import com.sdms.sharing.SharingManager;
import com.sdms.util.AppInfo;
import com.sdms.util.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Creates a complete, portable backup of the shared database and its image assets. */
public final class BackupService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");
    private static final AtomicBoolean AUTO_BACKUP_STARTED = new AtomicBoolean();

    private BackupService() {}

    public static String defaultBackupFileName() {
        return "Athlete-Track-Backup_" + STAMP.format(LocalDateTime.now()) + ".atbackup";
    }

    public static void startAutomaticBackupIfDue() {
        if (Boolean.getBoolean("sdms.disableAutoBackup")) return;
        if (!SharingManager.getInstance().isHost() || !AUTO_BACKUP_STARTED.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> {
            Path automaticDir = AppPaths.backupsDir().resolve("automatic");
            Path marker = automaticDir.resolve("last-successful-backup.txt");
            try {
                Files.createDirectories(automaticDir);
                String today = LocalDate.now().toString();
                if (Files.isRegularFile(marker) && today.equals(Files.readString(marker).trim())) return;
                Path destination = automaticDir.resolve(defaultBackupFileName());
                createSharedBackup(destination);
                Files.writeString(marker, today);
                try (var backups = Files.list(automaticDir)) {
                    var old = backups.filter(path -> path.getFileName().toString().endsWith(".atbackup"))
                        .sorted(java.util.Comparator.comparingLong(BackupService::lastModified).reversed())
                        .skip(14).toList();
                    for (Path path : old) Files.deleteIfExists(path);
                }
            } catch (Exception e) {
                try {
                    Files.writeString(AppPaths.logsDir().resolve("backup-error.log"),
                        LocalDateTime.now() + " " + e.getMessage() + System.lineSeparator());
                } catch (IOException ignored) {}
            }
        }, "athlete-track-auto-backup");
        thread.setDaemon(true);
        thread.start();
    }

    public static Path createSharedBackup(Path destination) throws Exception {
        SharingManager sharing = SharingManager.getInstance();
        if (!sharing.isHost()) throw new IllegalStateException("Backups must be created on the main computer.");
        Path absolute = destination.toAbsolutePath().normalize();
        if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
        Path working = Files.createTempDirectory(AppPaths.backupsDir(), "backup-");
        Path databaseScript = working.resolve("database.sql.zip");
        DatabaseService.getInstance().writeBackupScript(databaseScript);

        Path temporaryOutput = Files.createTempFile(
            absolute.getParent() == null ? AppPaths.backupsDir() : absolute.getParent(),
            "athlete-track-", ".part");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporaryOutput))) {
            Properties manifest = new Properties();
            SharingConfig config = sharing.getConfig();
            manifest.setProperty("format", "athlete-track-backup-v1");
            manifest.setProperty("appVersion", AppInfo.version());
            manifest.setProperty("createdAt", LocalDateTime.now().toString());
            manifest.setProperty("serverName", config.getServerName());
            manifest.setProperty("databaseUser", config.getDatabaseUser());
            manifest.setProperty("databasePassword", config.getDatabasePassword());
            zip.putNextEntry(new ZipEntry("manifest.properties"));
            manifest.store(zip, "Keep this backup private; it contains account and connection data.");
            zip.closeEntry();

            addFile(zip, databaseScript, "database.sql.zip");
            Path assets = AppPaths.sharedAssetsDir();
            if (Files.isDirectory(assets)) {
                try (var paths = Files.walk(assets)) {
                    for (Path path : paths.filter(Files::isRegularFile).toList()) {
                        String relative = assets.relativize(path).toString().replace('\\', '/');
                        addFile(zip, path, "assets/" + relative);
                    }
                }
            }
        } catch (Exception e) {
            Files.deleteIfExists(temporaryOutput);
            throw e;
        } finally {
            deleteTree(working);
        }
        Files.move(temporaryOutput, absolute, StandardCopyOption.REPLACE_EXISTING);
        return absolute;
    }

    public static RestoredBackup extractBackup(Path source) throws Exception {
        if (!Files.isRegularFile(source)) throw new IOException("The selected backup file does not exist.");
        Path working = Files.createTempDirectory(AppPaths.backupsDir(), "restore-");
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path destination = working.resolve(entry.getName()).normalize();
                if (!destination.startsWith(working)) throw new IOException("The backup contains an unsafe file path.");
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                zip.closeEntry();
            }
        }
        Path manifestPath = working.resolve("manifest.properties");
        Path databaseScript = working.resolve("database.sql.zip");
        if (!Files.isRegularFile(manifestPath) || !Files.isRegularFile(databaseScript)) {
            deleteTree(working);
            throw new IOException("This is not a valid Athlete Track backup.");
        }
        Properties manifest = new Properties();
        try (InputStream in = Files.newInputStream(manifestPath)) { manifest.load(in); }
        if (!"athlete-track-backup-v1".equals(manifest.getProperty("format"))) {
            deleteTree(working);
            throw new IOException("This backup version is not supported.");
        }
        return new RestoredBackup(working, databaseScript, working.resolve("assets"), manifest);
    }

    private static void addFile(ZipOutputStream zip, Path source, String entryName) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(source, zip);
        zip.closeEntry();
    }

    private static long lastModified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException e) { return 0L; }
    }

    public static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {}
    }

    public record RestoredBackup(Path workingDirectory, Path databaseScript,
                                 Path assetsDirectory, Properties manifest) implements AutoCloseable {
        @Override public void close() { deleteTree(workingDirectory); }
    }
}
