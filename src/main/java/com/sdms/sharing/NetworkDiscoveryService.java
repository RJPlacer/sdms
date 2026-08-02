package com.sdms.sharing;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lightweight UDP discovery limited to the local network. */
public final class NetworkDiscoveryService implements AutoCloseable {

    private static final String REQUEST = "ATHLETE_TRACK_DISCOVER_V1";
    private static final String RESPONSE = "ATHLETE_TRACK_HOST_V1";
    private final AtomicBoolean running = new AtomicBoolean();
    private DatagramSocket socket;
    private Thread thread;

    public void startResponder(String serverName, int pairingPort) throws SocketException {
        if (running.get()) return;
        socket = new DatagramSocket(SharingConfig.DEFAULT_DISCOVERY_PORT);
        socket.setBroadcast(true);
        running.set(true);
        thread = new Thread(() -> respondLoop(serverName, pairingPort), "athlete-track-discovery");
        thread.setDaemon(true);
        thread.start();
    }

    private void respondLoop(String serverName, int pairingPort) {
        byte[] buffer = new byte[512];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String request = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                if (!REQUEST.equals(request)) continue;
                String safeName = serverName == null ? "Athlete Track" : serverName.replace("|", " ");
                byte[] response = (RESPONSE + "|" + safeName + "|" + pairingPort)
                    .getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort()));
            } catch (IOException e) {
                if (running.get()) break;
            }
        }
    }

    public static List<HostOffer> discover(int timeoutMillis) throws IOException {
        Map<String, HostOffer> found = new LinkedHashMap<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(Math.max(250, timeoutMillis));
            byte[] request = REQUEST.getBytes(StandardCharsets.UTF_8);
            DatagramPacket broadcast = new DatagramPacket(request, request.length,
                InetAddress.getByName("255.255.255.255"), SharingConfig.DEFAULT_DISCOVERY_PORT);
            socket.send(broadcast);

            long deadline = System.currentTimeMillis() + Math.max(250, timeoutMillis);
            while (System.currentTimeMillis() < deadline) {
                try {
                    byte[] buffer = new byte[512];
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);
                    String text = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
                    String[] parts = text.split("\\|", 3);
                    if (parts.length != 3 || !RESPONSE.equals(parts[0])) continue;
                    int port = Integer.parseInt(parts[2]);
                    String host = response.getAddress().getHostAddress();
                    found.put(host + ":" + port, new HostOffer(parts[1], host, port));
                } catch (SocketTimeoutException done) {
                    break;
                } catch (NumberFormatException ignored) {}
            }
        }
        return new ArrayList<>(found.values());
    }

    @Override public void close() {
        running.set(false);
        if (socket != null) socket.close();
    }
}
