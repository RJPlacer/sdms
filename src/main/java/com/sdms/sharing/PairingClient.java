package com.sdms.sharing;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

/** Retrieves connection details after the user enters the host's pairing code. */
public final class PairingClient {
    private PairingClient() {}

    public static SharingConfig pair(String host, int pairingPort, String code) throws Exception {
        String cleanHost = host == null ? "" : host.trim();
        if (!cleanHost.matches("[A-Za-z0-9._:-]{1,255}")) {
            throw new IllegalArgumentException("Enter a valid computer name or IP address.");
        }
        String url = "http://" + cleanHost + ":" + pairingPort + "/pair?code="
            + URLEncoder.encode(code == null ? "" : code.trim(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(6)).GET().build();
        HttpResponse<byte[]> response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build()
            .send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 403) throw new IllegalArgumentException("The connection code is incorrect.");
        if (response.statusCode() != 200) throw new IllegalStateException("The main computer did not accept the connection.");

        Properties p = new Properties();
        p.load(new ByteArrayInputStream(response.body()));
        SharingConfig config = new SharingConfig();
        config.setMode(SharingConfig.Mode.CLIENT);
        config.setServerName(p.getProperty("serverName", "Athlete Track Main Computer"));
        config.setHost(cleanHost);
        config.setDatabasePort(Integer.parseInt(p.getProperty("databasePort", "9123")));
        config.setPairingPort(pairingPort);
        config.setDatabaseUser(p.getProperty("databaseUser"));
        config.setDatabasePassword(p.getProperty("databasePassword"));
        config.setAssetToken(p.getProperty("assetToken"));
        return config;
    }
}
