package com.sdms.sharing;

import com.sdms.util.AppPaths;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/** Local-only HTTP service for pairing and shared photo/QR assets. */
public final class PairingServer implements AutoCloseable {

    private static final long MAX_UPLOAD_BYTES = 12L * 1024L * 1024L;
    private static final long ATTEMPT_WINDOW_MS = 5L * 60L * 1000L;
    private static final int MAX_FAILED_ATTEMPTS = 8;

    private final SharingConfig config;
    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private HttpServer server;

    public PairingServer(SharingConfig config) { this.config = config; }

    public void start() throws IOException {
        if (server != null) return;
        server = HttpServer.create(new InetSocketAddress(config.getPairingPort()), 0);
        server.createContext("/pair", this::handlePair);
        server.createContext("/health", this::handleHealth);
        server.createContext("/assets", this::handleAsset);
        server.createContext("/upload", this::handleUpload);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "athlete-track-pairing");
            t.setDaemon(true);
            return t;
        }));
        server.start();
    }

    private void handlePair(HttpExchange exchange) throws IOException {
        if (!isLocalNetwork(exchange)) { send(exchange, 403, "Local network only", "text/plain"); return; }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain"); return;
        }
        String client = exchange.getRemoteAddress().getAddress().getHostAddress();
        AttemptWindow window = attempts.computeIfAbsent(client, ignored -> new AttemptWindow());
        if (window.blocked()) {
            send(exchange, 429, "Try again later", "text/plain"); return;
        }
        String code = query(exchange, "code");
        if (!constantTimeEquals(config.getPairingCode(), code)) {
            window.failed();
            send(exchange, 403, "Incorrect connection code", "text/plain"); return;
        }
        attempts.remove(client);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        Properties p = new Properties();
        p.setProperty("serverName", config.getServerName());
        p.setProperty("databasePort", Integer.toString(config.getDatabasePort()));
        p.setProperty("databaseUser", config.getDatabaseUser());
        p.setProperty("databasePassword", config.getDatabasePassword());
        p.setProperty("assetToken", config.getAssetToken());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        p.store(out, "Athlete Track same-Wi-Fi connection");
        send(exchange, 200, out.toByteArray(), "text/plain; charset=utf-8");
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!isLocalNetwork(exchange)) { send(exchange, 403, "Local network only", "text/plain"); return; }
        if (!authorized(exchange)) { send(exchange, 403, "Forbidden", "text/plain"); return; }
        send(exchange, 200, "Athlete Track host is available", "text/plain");
    }

    private void handleAsset(HttpExchange exchange) throws IOException {
        if (!isLocalNetwork(exchange)) { send(exchange, 403, "Local network only", "text/plain"); return; }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain"); return;
        }
        if (!authorized(exchange)) { send(exchange, 403, "Forbidden", "text/plain"); return; }
        String relative = exchange.getRequestURI().getPath().substring("/assets/".length());
        Path asset = safeAssetPath(relative);
        if (asset == null || !Files.isRegularFile(asset)) {
            send(exchange, 404, "Asset not found", "text/plain"); return;
        }
        String contentType = Files.probeContentType(asset);
        byte[] bytes = Files.readAllBytes(asset);
        exchange.getResponseHeaders().set("Cache-Control", "private, max-age=300");
        send(exchange, 200, bytes, contentType == null ? "application/octet-stream" : contentType);
    }

    private void handleUpload(HttpExchange exchange) throws IOException {
        if (!isLocalNetwork(exchange)) { send(exchange, 403, "Local network only", "text/plain"); return; }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain"); return;
        }
        if (!authorized(exchange)) { send(exchange, 403, "Forbidden", "text/plain"); return; }
        String folder = query(exchange, "folder");
        if (!"photos".equals(folder) && !"qrcodes".equals(folder)) {
            send(exchange, 400, "Invalid asset folder", "text/plain"); return;
        }
        long declared = parseLong(exchange.getRequestHeaders().getFirst("Content-Length"));
        if (declared > MAX_UPLOAD_BYTES) { send(exchange, 413, "File is too large", "text/plain"); return; }
        byte[] body = exchange.getRequestBody().readNBytes((int) MAX_UPLOAD_BYTES + 1);
        if (body.length > MAX_UPLOAD_BYTES) { send(exchange, 413, "File is too large", "text/plain"); return; }

        String requestedName = query(exchange, "name");
        String fileName = UUID.randomUUID() + "-" + safeFileName(requestedName);
        Path folderPath = "photos".equals(folder) ? AppPaths.sharedPhotosDir() : AppPaths.sharedQrCodesDir();
        Path temporary = Files.createTempFile(folderPath, "upload-", ".tmp");
        Files.write(temporary, body);
        Path destination = folderPath.resolve(fileName).normalize();
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        send(exchange, 200, "shared:" + folder + "/" + fileName, "text/plain");
    }

    private boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("X-Athlete-Track-Token");
        String query = query(exchange, "token");
        return constantTimeEquals(config.getAssetToken(), header != null ? header : query);
    }

    private static boolean isLocalNetwork(HttpExchange exchange) {
        var address = exchange.getRemoteAddress().getAddress();
        return address != null && (address.isLoopbackAddress()
            || address.isSiteLocalAddress() || address.isLinkLocalAddress());
    }

    private static Path safeAssetPath(String relative) {
        try {
            String decoded = URLDecoder.decode(relative, StandardCharsets.UTF_8);
            if (!decoded.matches("(photos|qrcodes)/[A-Za-z0-9._-]+")) return null;
            Path root = AppPaths.sharedAssetsDir().toAbsolutePath().normalize();
            Path result = root.resolve(decoded).normalize();
            return result.startsWith(root) ? result : null;
        } catch (Exception e) { return null; }
    }

    private static String query(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return "";
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name)) {
                return pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            }
        }
        return "";
    }

    private static String safeExtension(String fileName) {
        if (fileName == null) return ".bin";
        int dot = fileName.lastIndexOf('.');
        String extension = dot >= 0 ? fileName.substring(dot).toLowerCase() : ".bin";
        return extension.matches("\\.(png|jpg|jpeg|gif|webp|bmp)") ? extension : ".bin";
    }

    private static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "image.bin";
        String cleaned = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.length() > 80) cleaned = cleaned.substring(cleaned.length() - 80);
        if (cleaned.isBlank()) return "image" + safeExtension(fileName);
        return cleaned;
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception e) { return -1; }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        if (a.length == 0 || b.length == 0) return false;
        int diff = a.length ^ b.length;
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) diff |= a[i % a.length] ^ b[i % b.length];
        return diff == 0;
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        send(exchange, status, body.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private static void send(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
    }

    @Override public void close() {
        if (server != null) server.stop(0);
        server = null;
    }

    private static final class AttemptWindow {
        private long started = System.currentTimeMillis();
        private int failures;
        synchronized void failed() {
            resetIfExpired();
            failures++;
        }
        synchronized boolean blocked() {
            resetIfExpired();
            return failures >= MAX_FAILED_ATTEMPTS;
        }
        private void resetIfExpired() {
            if (System.currentTimeMillis() - started > ATTEMPT_WINDOW_MS) {
                started = System.currentTimeMillis();
                failures = 0;
            }
        }
    }
}
