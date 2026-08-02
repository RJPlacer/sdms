package com.sdms.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Direct, account-free QR relay between the desktop and one phone on the local network. */
public final class LocalScannerService implements AutoCloseable {

    private static final int MAX_PAYLOAD_BYTES = 2_048;
    private static final String TOKEN_HEADER = "X-Athlete-Track-Token";

    private final HttpServer server;
    private final String token;
    private final AtomicReference<InetAddress> pairedPhone = new AtomicReference<>();
    private final Consumer<String> scanConsumer;
    private final Runnable pairedConsumer;
    private final String localAddress;

    private LocalScannerService(HttpServer server, String token, String localAddress,
                                Consumer<String> scanConsumer, Runnable pairedConsumer) {
        this.server = server;
        this.token = token;
        this.localAddress = localAddress;
        this.scanConsumer = scanConsumer;
        this.pairedConsumer = pairedConsumer;
    }

    public static LocalScannerService start(Consumer<String> scanConsumer, Runnable pairedConsumer)
            throws IOException {
        String address = findLocalIpv4Address();
        String token = createToken();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        LocalScannerService service = new LocalScannerService(
            server, token, address, scanConsumer, pairedConsumer);
        server.createContext("/athlete-track/pair", service::handlePair);
        server.createContext("/athlete-track/scan", service::handleScan);
        server.setExecutor(Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "local-scanner-server");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return service;
    }

    public URI pairingUri() {
        return URI.create("athletetrack://pair?host=" + localAddress
            + "&port=" + server.getAddress().getPort() + "&token=" + token);
    }

    public String connectionDescription() {
        return localAddress + ":" + server.getAddress().getPort();
    }

    int port() { return server.getAddress().getPort(); }
    String token() { return token; }

    private void handlePair(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "POST required");
                return;
            }
            if (!authorized(exchange)) {
                respond(exchange, 401, "Invalid pairing code");
                return;
            }
            InetAddress phoneAddress = exchange.getRemoteAddress().getAddress();
            InetAddress current = pairedPhone.get();
            if (current != null && !current.equals(phoneAddress)) {
                respond(exchange, 409, "Another phone is already paired");
                return;
            }
            if (pairedPhone.compareAndSet(null, phoneAddress)) pairedConsumer.run();
            respond(exchange, 200, "Athlete Track desktop connected");
        } finally {
            exchange.close();
        }
    }

    private void handleScan(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "POST required");
                return;
            }
            InetAddress paired = pairedPhone.get();
            if (!authorized(exchange) || paired == null
                    || !paired.equals(exchange.getRemoteAddress().getAddress())) {
                respond(exchange, 401, "Phone is not paired");
                return;
            }
            byte[] bytes = exchange.getRequestBody().readNBytes(MAX_PAYLOAD_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_PAYLOAD_BYTES) {
                respond(exchange, 400, "QR payload must be between 1 and 2048 bytes");
                return;
            }
            String payload = new String(bytes, StandardCharsets.UTF_8).trim();
            if (payload.isBlank()) {
                respond(exchange, 400, "QR payload is empty");
                return;
            }
            scanConsumer.accept(payload);
            respond(exchange, 202, "Scan received");
        } finally {
            exchange.close();
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String supplied = exchange.getRequestHeaders().getFirst(TOKEN_HEADER);
        return supplied != null && MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static String createToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String findLocalIpv4Address() throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
            Enumeration<InetAddress> addresses = network.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                    return address.getHostAddress();
                }
            }
        }
        throw new IOException("Connect this computer to Wi-Fi or to the phone's hotspot, then try again.");
    }

    @Override public void close() {
        server.stop(0);
    }
}
