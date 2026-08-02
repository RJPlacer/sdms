package com.sdms.sharing;

import com.sdms.util.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;

/** Persisted connection settings for local, host, or client operation. */
public final class SharingConfig {

    public enum Mode { LOCAL, HOST, CLIENT }

    public static final int DEFAULT_DATABASE_PORT = 9123;
    public static final int DEFAULT_PAIRING_PORT = 9124;
    public static final int DEFAULT_DISCOVERY_PORT = 9125;

    private Mode mode = Mode.LOCAL;
    private String serverName = "Athlete Track Main Computer";
    private String host = "127.0.0.1";
    private int databasePort = DEFAULT_DATABASE_PORT;
    private int pairingPort = DEFAULT_PAIRING_PORT;
    private String databaseUser = "athlete_track";
    private String databasePassword = "";
    private String assetToken = "";
    private String pairingCode = "";

    public static SharingConfig load() {
        SharingConfig config = new SharingConfig();
        if (!Files.isRegularFile(AppPaths.sharingConfigPath())) return config;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(AppPaths.sharingConfigPath())) {
            p.load(in);
            config.mode = parseMode(p.getProperty("mode"));
            config.serverName = p.getProperty("serverName", config.serverName);
            config.host = p.getProperty("host", config.host);
            config.databasePort = parsePort(p.getProperty("databasePort"), DEFAULT_DATABASE_PORT);
            config.pairingPort = parsePort(p.getProperty("pairingPort"), DEFAULT_PAIRING_PORT);
            config.databaseUser = p.getProperty("databaseUser", config.databaseUser);
            config.databasePassword = p.getProperty("databasePassword", "");
            config.assetToken = p.getProperty("assetToken", "");
            config.pairingCode = p.getProperty("pairingCode", "");
        } catch (IOException ignored) {
            return new SharingConfig();
        }
        return config;
    }

    public void save() throws IOException {
        Properties p = new Properties();
        p.setProperty("mode", mode.name());
        p.setProperty("serverName", value(serverName));
        p.setProperty("host", value(host));
        p.setProperty("databasePort", Integer.toString(databasePort));
        p.setProperty("pairingPort", Integer.toString(pairingPort));
        p.setProperty("databaseUser", value(databaseUser));
        p.setProperty("databasePassword", value(databasePassword));
        p.setProperty("assetToken", value(assetToken));
        p.setProperty("pairingCode", value(pairingCode));
        try (OutputStream out = Files.newOutputStream(AppPaths.sharingConfigPath())) {
            p.store(out, "Athlete Track multi-PC sharing settings");
        }
    }

    private static Mode parseMode(String value) {
        try { return Mode.valueOf(value == null ? "LOCAL" : value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return Mode.LOCAL; }
    }

    private static int parsePort(String value, int fallback) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1024 && port <= 65535 ? port : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String value(String value) { return value == null ? "" : value; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode == null ? Mode.LOCAL : mode; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getDatabasePort() { return databasePort; }
    public void setDatabasePort(int databasePort) { this.databasePort = databasePort; }
    public int getPairingPort() { return pairingPort; }
    public void setPairingPort(int pairingPort) { this.pairingPort = pairingPort; }
    public String getDatabaseUser() { return databaseUser; }
    public void setDatabaseUser(String databaseUser) { this.databaseUser = databaseUser; }
    public String getDatabasePassword() { return databasePassword; }
    public void setDatabasePassword(String databasePassword) { this.databasePassword = databasePassword; }
    public String getAssetToken() { return assetToken; }
    public void setAssetToken(String assetToken) { this.assetToken = assetToken; }
    public String getPairingCode() { return pairingCode; }
    public void setPairingCode(String pairingCode) { this.pairingCode = pairingCode; }
}
