package com.sdms.sharing;

import com.sdms.util.AppPaths;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

/** Stores and resolves photos and QR images in both local and shared modes. */
public final class SharedAssetService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6)).build();

    private SharedAssetService() {}

    public static String importPhoto(Path source) throws IOException, InterruptedException {
        return store(source, "photos", source.getFileName().toString());
    }

    public static String importQrCode(Path source, String suggestedName) throws IOException, InterruptedException {
        return store(source, "qrcodes", suggestedName);
    }

    public static String hostImportLegacy(String existing, String folder) {
        if (existing == null || existing.isBlank() || existing.startsWith("shared:")) return existing;
        try {
            Path source = Path.of(existing);
            if (!Files.isRegularFile(source)) return existing;
            Path destinationFolder = "photos".equals(folder)
                ? AppPaths.sharedPhotosDir() : AppPaths.sharedQrCodesDir();
            String fileName = UUID.randomUUID() + "-" + safeFileName(source.getFileName().toString());
            Files.copy(source, destinationFolder.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return "shared:" + folder + "/" + fileName;
        } catch (Exception e) {
            return existing;
        }
    }

    private static String store(Path source, String folder, String suggestedName)
            throws IOException, InterruptedException {
        if (!Files.isRegularFile(source)) throw new IOException("The selected image could not be read.");
        if (Files.size(source) > 12L * 1024L * 1024L) throw new IOException("The image must be 12 MB or smaller.");
        SharingManager sharing = SharingManager.getInstance();
        if (!sharing.isShared()) {
            Path destinationFolder = "photos".equals(folder) ? AppPaths.photosDir() : AppPaths.qrCodesDir();
            String fileName = UUID.randomUUID() + "-" + safeFileName(suggestedName);
            Path destination = destinationFolder.resolve(fileName);
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.toAbsolutePath().toString();
        }
        if (sharing.isHost()) {
            Path destinationFolder = "photos".equals(folder)
                ? AppPaths.sharedPhotosDir() : AppPaths.sharedQrCodesDir();
            String fileName = UUID.randomUUID() + "-" + safeFileName(suggestedName);
            Files.copy(source, destinationFolder.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return "shared:" + folder + "/" + fileName;
        }
        SharingConfig config = sharing.getConfig();
        String url = "http://" + config.getHost() + ":" + config.getPairingPort()
            + "/upload?folder=" + URLEncoder.encode(folder, StandardCharsets.UTF_8)
            + "&name=" + URLEncoder.encode(suggestedName, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("X-Athlete-Track-Token", config.getAssetToken())
            .POST(HttpRequest.BodyPublishers.ofFile(source)).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || !response.body().startsWith("shared:")) {
            throw new IOException("The main computer could not store the image.");
        }
        return response.body().trim();
    }

    public static String imageUrl(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return null;
        if (!storedPath.startsWith("shared:")) {
            try { return Path.of(storedPath).toUri().toString(); }
            catch (Exception e) { return storedPath; }
        }
        SharingManager sharing = SharingManager.getInstance();
        String relative = storedPath.substring("shared:".length());
        if (sharing.isHost()) return AppPaths.sharedAssetsDir().resolve(relative).toUri().toString();
        SharingConfig config = sharing.getConfig();
        return "http://" + config.getHost() + ":" + config.getPairingPort()
            + "/assets/" + relative + "?token="
            + URLEncoder.encode(config.getAssetToken(), StandardCharsets.UTF_8);
    }

    public static Path localPath(String storedPath) throws IOException, InterruptedException {
        if (storedPath == null || storedPath.isBlank()) return null;
        if (!storedPath.startsWith("shared:")) return Path.of(storedPath);
        String relative = storedPath.substring("shared:".length());
        SharingManager sharing = SharingManager.getInstance();
        if (sharing.isHost()) return AppPaths.sharedAssetsDir().resolve(relative);
        String extension = extension(relative);
        Path cached = AppPaths.cacheDir().resolve(Integer.toHexString(storedPath.hashCode()) + extension);
        if (Files.isRegularFile(cached)) return cached;
        String url = imageUrl(storedPath);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(cached));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(cached);
            throw new IOException("Could not retrieve an image from the main computer.");
        }
        return cached;
    }

    public static String displayName(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return "No file selected";
        String value = storedPath.startsWith("shared:")
            ? storedPath.substring(storedPath.lastIndexOf('/') + 1) : Path.of(storedPath).getFileName().toString();
        int separator = value.indexOf('-');
        return separator > 30 ? value.substring(separator + 1) : value;
    }

    private static String extension(String name) {
        if (name == null) return ".bin";
        int dot = name.lastIndexOf('.');
        if (dot < 0) return ".bin";
        String ext = name.substring(dot).toLowerCase();
        return ext.matches("\\.(png|jpg|jpeg|gif|webp|bmp)") ? ext : ".bin";
    }

    private static String safeFileName(String name) {
        String fallback = "image" + extension(name);
        if (name == null || name.isBlank()) return fallback;
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.length() > 80) cleaned = cleaned.substring(cleaned.length() - 80);
        return cleaned.isBlank() ? fallback : cleaned;
    }
}
