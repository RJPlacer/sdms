package com.sdms.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Short-lived Internet relay used by an Android phone to send decoded QR text to the desktop. */
public final class ScannerRelayService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private ScannerRelayService() {}

    public static RelaySession createSession() throws IOException, InterruptedException {
        if (!OnlineVerificationService.isConfigured()) {
            throw new IOException("Online verification is not configured on this computer.");
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OnlineVerificationService.configuredBaseUrl() + "/api/scanner/sessions"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + OnlineVerificationService.configuredApiKey())
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        requireSuccess(response, "Could not create a phone scanner session");

        String[] lines = response.body().split("\\R", 4);
        if (lines.length != 4) throw new IOException("The scanner service returned an invalid response.");
        try {
            return new RelaySession(lines[0], lines[1], URI.create(lines[2]), Instant.parse(lines[3]));
        } catch (RuntimeException error) {
            throw new IOException("The scanner service returned invalid session details.", error);
        }
    }

    public static PollResult poll(RelaySession session) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(eventsUri(session))
            .timeout(Duration.ofSeconds(10))
            .header("X-Desktop-Secret", session.desktopSecret())
            .GET()
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 204) {
            boolean paired = response.headers().firstValue("X-Scanner-Paired")
                .map(Boolean::parseBoolean).orElse(false);
            return new PollResult(Optional.empty(), paired);
        }
        requireSuccess(response, "Phone scanner connection was lost");
        return new PollResult(Optional.of(response.body()), true);
    }

    public static void disconnect(RelaySession session) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(eventsUri(session))
            .timeout(Duration.ofSeconds(10))
            .header("X-Desktop-Secret", session.desktopSecret())
            .DELETE()
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204) requireSuccess(response, "Could not close the scanner session");
    }

    private static URI eventsUri(RelaySession session) {
        return URI.create(OnlineVerificationService.configuredBaseUrl()
            + "/api/scanner/sessions/" + session.sessionId() + "/events");
    }

    private static void requireSuccess(HttpResponse<String> response, String message) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = response.body() == null ? "" : response.body().trim();
            if (detail.length() > 160) detail = detail.substring(0, 160);
            throw new IOException(message + " (HTTP " + response.statusCode() + ")"
                + (detail.isBlank() ? "." : ": " + detail));
        }
    }

    public record RelaySession(String sessionId, String desktopSecret, URI pairUrl, Instant expiresAt) {}
    public record PollResult(Optional<String> payload, boolean paired) {}
}
