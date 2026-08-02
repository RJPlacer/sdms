package com.sdms.sharing;

import com.sdms.service.DatabaseService;
import com.sdms.service.BackupService;
import com.sdms.util.AppPaths;
import org.h2.tools.Server;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;

/** Coordinates the optional central database and pairing services. */
public final class SharingManager implements AutoCloseable {

    private static final SharingManager INSTANCE = new SharingManager();
    private static final SecureRandom RANDOM = new SecureRandom();

    private SharingConfig config = SharingConfig.load();
    private Server databaseServer;
    private PairingServer pairingServer;
    private NetworkDiscoveryService discoveryService;

    private SharingManager() {}

    public static SharingManager getInstance() { return INSTANCE; }

    public synchronized void initializeAtStartup() throws Exception {
        config = SharingConfig.load();
        if (config.getMode() == SharingConfig.Mode.HOST) startHostInfrastructure();
    }

    public synchronized SharingConfig getConfig() { return config; }
    public synchronized SharingConfig.Mode getMode() { return config.getMode(); }
    public synchronized boolean isShared() { return config.getMode() != SharingConfig.Mode.LOCAL; }
    public synchronized boolean isHost() { return config.getMode() == SharingConfig.Mode.HOST; }
    public synchronized boolean isClient() { return config.getMode() == SharingConfig.Mode.CLIENT; }

    public synchronized String jdbcUrl() {
        if (config.getMode() == SharingConfig.Mode.LOCAL) {
            return "jdbc:sqlite:" + AppPaths.dbPath();
        }
        String host = config.getMode() == SharingConfig.Mode.HOST ? "127.0.0.1" : config.getHost();
        return "jdbc:h2:tcp://" + host + ":" + config.getDatabasePort()
            + "/./athlete-track;MODE=LEGACY;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=YEAR,EVENT,DATE,LEVEL,VALUE,USER";
    }

    public synchronized String databaseUser() {
        return config.getMode() == SharingConfig.Mode.LOCAL ? "" : config.getDatabaseUser();
    }

    public synchronized String databasePassword() {
        return config.getMode() == SharingConfig.Mode.LOCAL ? "" : config.getDatabasePassword();
    }

    public synchronized void enableHost(String serverName) throws Exception {
        if (config.getMode() == SharingConfig.Mode.HOST) return;
        SharingConfig previous = config;
        SharingConfig host = newHostConfig(serverName);

        boolean hasExistingSharedDatabase = Files.exists(
            AppPaths.sharedDatabaseDir().resolve("athlete-track.mv.db"));
        try {
            close();
            config = host;
            startHostInfrastructure();
            DatabaseService.getInstance().reinitialize();
            if (!hasExistingSharedDatabase) {
                DatabaseMigrationService.migrateLocalSqliteToShared(
                    AppPaths.dbPath(), DatabaseService.getInstance().maintenanceConnection());
            }
            host.save();
            BackupService.startAutomaticBackupIfDue();
        } catch (Exception e) {
            close();
            config = previous;
            previous.save();
            DatabaseService.getInstance().reinitialize();
            throw e;
        }
    }

    public synchronized void restoreHostBackup(Path backupFile, String serverName) throws Exception {
        if (config.getMode() != SharingConfig.Mode.LOCAL) {
            throw new IllegalStateException("Disconnect from sharing before restoring a main-computer backup.");
        }
        SharingConfig previous = config;
        Path recovery = AppPaths.backupsDir().resolve("replaced-shared-data-" + System.currentTimeMillis());
        Path databaseDir = AppPaths.sharedDatabaseDir();
        Path assetsDir = AppPaths.sharedAssetsDir();
        DatabaseService.getInstance().closeConnection();
        close();
        Files.createDirectories(recovery);
        moveAside(databaseDir, recovery.resolve("shared-database"));
        moveAside(assetsDir, recovery.resolve("shared-assets"));

        try (BackupService.RestoredBackup restored = BackupService.extractBackup(backupFile)) {
            SharingConfig host = newHostConfig(serverName);
            host.setDatabaseUser(restored.manifest().getProperty("databaseUser", "athlete_track"));
            host.setDatabasePassword(restored.manifest().getProperty("databasePassword", randomSecret(32)));
            config = host;
            startHostInfrastructure();
            try (Connection connection = DriverManager.getConnection(
                    jdbcUrl(host), host.getDatabaseUser(), host.getDatabasePassword());
                 Statement statement = connection.createStatement()) {
                String script = restored.databaseScript().toAbsolutePath().toString().replace("'", "''");
                statement.execute("RUNSCRIPT FROM '" + script + "' COMPRESSION ZIP");
            }
            if (Files.isDirectory(restored.assetsDirectory())) {
                copyTree(restored.assetsDirectory(), AppPaths.sharedAssetsDir());
            }
            host.save();
            DatabaseService.getInstance().reinitialize();
            BackupService.startAutomaticBackupIfDue();
        } catch (Exception e) {
            DatabaseService.getInstance().closeConnection();
            close();
            BackupService.deleteTree(AppPaths.sharedDatabaseDir());
            BackupService.deleteTree(AppPaths.sharedAssetsDir());
            restoreAside(recovery.resolve("shared-database"), AppPaths.sharedDatabaseDir());
            restoreAside(recovery.resolve("shared-assets"), AppPaths.sharedAssetsDir());
            config = previous;
            previous.save();
            DatabaseService.getInstance().reinitialize();
            throw e;
        }
    }

    public synchronized void connectClient(String host, int pairingPort, String code) throws Exception {
        SharingConfig candidate = PairingClient.pair(host, pairingPort, code);
        ensureH2Driver();
        try (Connection ignored = DriverManager.getConnection(
                jdbcUrl(candidate), candidate.getDatabaseUser(), candidate.getDatabasePassword())) {
            // Connection test succeeded.
        }
        SharingConfig previous = config;
        try {
            close();
            config = candidate;
            config.save();
            DatabaseService.getInstance().reinitialize();
        } catch (Exception e) {
            config = previous;
            previous.save();
            DatabaseService.getInstance().reinitialize();
            throw e;
        }
    }

    public synchronized void useLocalDatabase() throws Exception {
        SharingConfig local = new SharingConfig();
        local.setMode(SharingConfig.Mode.LOCAL);
        DatabaseService.getInstance().closeConnection();
        close();
        config = local;
        config.save();
        DatabaseService.getInstance().reinitialize();
    }

    public synchronized boolean testConnection() {
        if (config.getMode() == SharingConfig.Mode.LOCAL) return true;
        DriverManager.setLoginTimeout((int) Duration.ofSeconds(4).toSeconds());
        try {
            ensureH2Driver();
            try (Connection ignored = DriverManager.getConnection(
                    jdbcUrl(), databaseUser(), databasePassword())) {
                return true;
            }
        } catch (Exception e) {
            return false;
        } finally {
            DriverManager.setLoginTimeout(0);
        }
    }

    public synchronized boolean tryRediscoverClient() {
        if (config.getMode() != SharingConfig.Mode.CLIENT) return false;
        try {
            List<HostOffer> offers = NetworkDiscoveryService.discover(1600);
            for (HostOffer offer : offers) {
                if (!config.getServerName().equalsIgnoreCase(offer.name())) continue;
                String previousHost = config.getHost();
                int previousPairingPort = config.getPairingPort();
                config.setHost(offer.host());
                config.setPairingPort(offer.pairingPort());
                if (testConnection()) {
                    config.save();
                    return true;
                }
                config.setHost(previousHost);
                config.setPairingPort(previousPairingPort);
            }
        } catch (Exception ignored) {}
        return false;
    }

    public synchronized String statusText() {
        return switch (config.getMode()) {
            case LOCAL -> "This computer only";
            case HOST -> "Main computer · Sharing on " + primaryLocalAddress();
            case CLIENT -> "Connected to " + config.getServerName();
        };
    }

    public synchronized String hostAddress() {
        return config.getMode() == SharingConfig.Mode.HOST ? primaryLocalAddress() : config.getHost();
    }

    private void startHostInfrastructure() throws Exception {
        if (databaseServer != null && databaseServer.isRunning(false)) return;
        ensureH2Driver();
        String baseDir = AppPaths.sharedDatabaseDir().toAbsolutePath().toString();
        Path databaseFile = AppPaths.sharedDatabaseDir().resolve("athlete-track.mv.db");
        if (!Files.exists(databaseFile)) {
            String embeddedUrl = "jdbc:h2:file:"
                + AppPaths.sharedDatabaseDir().resolve("athlete-track").toAbsolutePath()
                + ";MODE=LEGACY;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=YEAR,EVENT,DATE,LEVEL,VALUE,USER";
            try (Connection ignored = DriverManager.getConnection(
                    embeddedUrl, config.getDatabaseUser(), config.getDatabasePassword())) {
                // Create the database locally before exposing the existing database through TCP.
            }
        }
        databaseServer = Server.createTcpServer(
            "-tcpPort", Integer.toString(config.getDatabasePort()),
            "-tcpAllowOthers", "-baseDir", baseDir).start();
        pairingServer = new PairingServer(config);
        pairingServer.start();
        discoveryService = new NetworkDiscoveryService();
        discoveryService.startResponder(config.getServerName(), config.getPairingPort());
    }

    private static void ensureH2Driver() throws ClassNotFoundException {
        // The packaged application combines dependencies into one JAR, so load
        // H2 directly rather than relying on merged JDBC service metadata.
        Class.forName("org.h2.Driver");
    }

    private static SharingConfig newHostConfig(String serverName) {
        SharingConfig host = new SharingConfig();
        host.setMode(SharingConfig.Mode.HOST);
        host.setServerName(serverName == null || serverName.isBlank()
            ? "Athlete Track Main Computer" : serverName.trim());
        host.setHost(primaryLocalAddress());
        host.setDatabaseUser("athlete_track");
        host.setDatabasePassword(randomSecret(32));
        host.setAssetToken(randomSecret(32));
        host.setPairingCode(String.format("%06d", 100000 + RANDOM.nextInt(900000)));
        return host;
    }

    private static void moveAside(Path source, Path destination) throws Exception {
        if (!Files.exists(source)) return;
        boolean hasFiles;
        try (var children = Files.list(source)) { hasFiles = children.findAny().isPresent(); }
        if (!hasFiles) return;
        Files.createDirectories(destination.getParent());
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void restoreAside(Path source, Path destination) throws Exception {
        if (!Files.exists(source)) return;
        BackupService.deleteTree(destination);
        Files.createDirectories(destination.getParent());
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyTree(Path source, Path destination) throws Exception {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path).toString()).normalize();
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String jdbcUrl(SharingConfig candidate) {
        return "jdbc:h2:tcp://" + candidate.getHost() + ":" + candidate.getDatabasePort()
            + "/./athlete-track;MODE=LEGACY;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=YEAR,EVENT,DATE,LEVEL,VALUE,USER";
    }

    private static String randomSecret(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static String primaryLocalAddress() {
        try (java.net.DatagramSocket route = new java.net.DatagramSocket()) {
            route.connect(java.net.InetAddress.getByName("8.8.8.8"), 53);
            java.net.InetAddress address = route.getLocalAddress();
            if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                return address.getHostAddress();
            }
        } catch (Exception ignored) {}
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                Enumeration<java.net.InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    @Override public synchronized void close() {
        if (discoveryService != null) discoveryService.close();
        if (pairingServer != null) pairingServer.close();
        if (databaseServer != null) databaseServer.stop();
        discoveryService = null;
        pairingServer = null;
        databaseServer = null;
    }
}
