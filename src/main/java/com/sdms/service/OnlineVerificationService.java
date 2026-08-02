package com.sdms.service;

import com.sdms.model.Athlete;
import com.sdms.model.Coach;
import com.sdms.model.Official;
import com.sdms.sharing.SharedAssetService;
import com.sdms.util.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;

/** Publishes the small, approved public identity profile used by mobile QR verification. */
public final class OnlineVerificationService {

    private static final int MAX_PHOTO_BYTES = 2_000_000;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private OnlineVerificationService() {}

    public static boolean isConfigured() {
        return !configuredBaseUrl().isBlank() && !configuredApiKey().isBlank();
    }

    public static Optional<String> publishAthlete(Athlete athlete)
            throws SQLException, IOException, InterruptedException {
        if (!isConfigured()) return Optional.empty();
        String token = DatabaseService.getInstance()
            .getOrCreateVerificationToken("ATHLETE", athlete.getId());
        publish(token, athlete.getAthleteId(), "ATHLETE", athlete.getFullName(),
            athlete.getSchool(), "Sex", athlete.getSex(), athlete.getPhotoPath());
        return Optional.of(verificationUrl(token));
    }

    public static Optional<String> publishOfficial(Official official)
            throws SQLException, IOException, InterruptedException {
        if (!isConfigured()) return Optional.empty();
        String token = DatabaseService.getInstance()
            .getOrCreateVerificationToken("OFFICIAL", official.getId());
        publish(token, official.getOfficialId(), "OFFICIAL", official.getFullName(),
            official.getSchool(), "Position", official.getCurrentPosition(), official.getPhotoPath());
        return Optional.of(verificationUrl(token));
    }

    public static Optional<String> publishCoach(Coach coach)
            throws SQLException, IOException, InterruptedException {
        if (!isConfigured()) return Optional.empty();
        String token = DatabaseService.getInstance()
            .getOrCreateVerificationToken("COACH", coach.getId());
        publish(token, coach.getCoachId(), "COACH", coach.getFullName(),
            coach.getSchool(), "Position", coach.getCurrentPosition(), coach.getPhotoPath());
        return Optional.of(verificationUrl(token));
    }

    public static String extractVerificationToken(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.trim());
            String path = uri.getPath();
            if (path == null) return null;
            int marker = path.indexOf("/verify/");
            if (marker < 0) return null;
            String token = path.substring(marker + "/verify/".length());
            int slash = token.indexOf('/');
            if (slash >= 0) token = token.substring(0, slash);
            return token.matches("[A-Za-z0-9_-]{32,128}") ? token : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void publish(String token, String personId, String personType,
                                String fullName, String school, String descriptorLabel,
                                String descriptorValue, String photoPath)
            throws IOException, InterruptedException {
        PhotoData photo = readPhoto(photoPath);
        String json = "{" +
            field("token", token) + "," +
            field("personId", personId) + "," +
            field("personType", personType) + "," +
            field("fullName", fullName) + "," +
            field("school", school) + "," +
            field("descriptorLabel", descriptorLabel) + "," +
            field("descriptorValue", descriptorValue) + "," +
            field("status", "ACTIVE") +
            (photo == null ? "" : "," + field("photoBase64", photo.base64)
                + "," + field("photoContentType", photo.contentType)) +
            "}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(configuredBaseUrl() + "/api/records"))
            .timeout(Duration.ofSeconds(25))
            .header("Authorization", "Bearer " + configuredApiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Online verification sync failed (HTTP " + response.statusCode() + ").");
        }
    }

    private static PhotoData readPhoto(String photoPath) {
        if (photoPath == null || photoPath.isBlank()) return null;
        try {
            Path path = SharedAssetService.localPath(photoPath);
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_PHOTO_BYTES) return null;
            String contentType = Files.probeContentType(path);
            if (contentType == null) {
                String lower = path.getFileName().toString().toLowerCase();
                contentType = lower.endsWith(".png") ? "image/png" : "image/jpeg";
            }
            if (!contentType.equals("image/jpeg") && !contentType.equals("image/png")) return null;
            return new PhotoData(Base64.getEncoder().encodeToString(Files.readAllBytes(path)), contentType);
        } catch (IOException | RuntimeException ignored) {
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String field(String name, String value) {
        return "\"" + escapeJson(name) + "\":\"" + escapeJson(value) + "\"";
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }

    private static String verificationUrl(String token) {
        return configuredBaseUrl() + "/verify/" + token;
    }

    static String configuredBaseUrl() {
        String value = setting("sdms.verification.baseUrl", "SDMS_VERIFICATION_BASE_URL", "baseUrl");
        return value.replaceAll("/+$", "");
    }

    static String configuredApiKey() {
        return setting("sdms.verification.apiKey", "SDMS_VERIFICATION_API_KEY", "apiKey");
    }

    private static String setting(String property, String environmentVariable, String fileKey) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(environmentVariable);
        if (value == null || value.isBlank()) {
            Path configPath = AppPaths.appDir().resolve("verification.properties");
            if (Files.isRegularFile(configPath)) {
                Properties properties = new Properties();
                try (InputStream input = Files.newInputStream(configPath)) {
                    properties.load(input);
                    value = properties.getProperty(fileKey);
                } catch (IOException ignored) {
                    value = null;
                }
            }
        }
        return value == null ? "" : value.trim();
    }

    private record PhotoData(String base64, String contentType) {}
}
