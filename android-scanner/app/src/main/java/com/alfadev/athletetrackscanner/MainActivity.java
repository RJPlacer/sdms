package com.alfadev.athletetrackscanner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@ExperimentalGetImage
public class MainActivity extends AppCompatActivity {

    private static final String TOKEN_HEADER = "X-Athlete-Track-Token";
    private static final long DUPLICATE_DELAY_MS = 2_500;

    private PreviewView cameraPreview;
    private TextView statusText;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean processingImage = new AtomicBoolean();
    private BarcodeScanner barcodeScanner;
    private volatile DesktopConnection connection;
    private String lastValue = "";
    private long lastValueAt;

    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startCamera();
            else setStatus("Camera permission is required to scan QR codes.");
        });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraPreview = findViewById(R.id.cameraPreview);
        statusText = findViewById(R.id.statusText);
        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(view -> resetPairing());

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeImage);
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception error) {
                setStatus("Could not start the camera: " + safeMessage(error));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (!processingImage.compareAndSet(false, true)) {
            imageProxy.close();
            return;
        }
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            processingImage.set(false);
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(
            mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(image)
            .addOnSuccessListener(this::handleBarcodes)
            .addOnCompleteListener(task -> {
                processingImage.set(false);
                imageProxy.close();
            });
    }

    private void handleBarcodes(List<Barcode> barcodes) {
        for (Barcode barcode : barcodes) {
            String value = barcode.getRawValue();
            if (value != null && !value.isBlank()) {
                handleDecoded(value.trim());
                return;
            }
        }
    }

    private synchronized void handleDecoded(String value) {
        long now = System.currentTimeMillis();
        if (value.equals(lastValue) && now - lastValueAt < DUPLICATE_DELAY_MS) return;
        lastValue = value;
        lastValueAt = now;

        if (value.startsWith("athletetrack://pair")) {
            if (connection == null) pairWithDesktop(value);
            return;
        }
        DesktopConnection current = connection;
        if (current == null) {
            setStatus("First scan the pairing QR shown on the computer.");
            return;
        }
        networkExecutor.execute(() -> sendScan(current, value));
    }

    private void pairWithDesktop(String pairingValue) {
        DesktopConnection parsed;
        try {
            Uri uri = Uri.parse(pairingValue);
            String host = uri.getQueryParameter("host");
            String token = uri.getQueryParameter("token");
            int port = Integer.parseInt(String.valueOf(uri.getQueryParameter("port")));
            if (!"athletetrack".equals(uri.getScheme()) || !"pair".equals(uri.getHost())
                    || !isPrivateIpv4(host) || port < 1024 || port > 65535
                    || token == null || token.length() < 32) {
                throw new IllegalArgumentException("Invalid local pairing code");
            }
            parsed = new DesktopConnection(host, port, token);
        } catch (Exception error) {
            setStatus("That is not a valid Athlete Track pairing QR.");
            return;
        }

        setStatus("Connecting directly to the computer...");
        networkExecutor.execute(() -> {
            try {
                int status = post(parsed, "pair", new byte[0]);
                if (status != 200) throw new IOException("Desktop returned HTTP " + status);
                connection = parsed;
                vibrate();
                setStatus("Connected. Now scan an Athlete Track ID QR code.");
            } catch (Exception error) {
                setStatus("Could not reach the computer. Confirm both devices use the same Wi-Fi and allow Athlete Track through Windows Firewall.");
            }
        });
    }

    private void sendScan(DesktopConnection desktop, String payload) {
        try {
            setStatus("Sending scan directly to the computer...");
            int status = post(desktop, "scan", payload.getBytes(StandardCharsets.UTF_8));
            if (status != 202) throw new IOException("Desktop returned HTTP " + status);
            vibrate();
            setStatus("Scan sent. Ready for the next QR code.");
        } catch (Exception error) {
            setStatus("Could not send the scan. Keep both devices on the same Wi-Fi and reconnect if needed.");
        }
    }

    private int post(DesktopConnection desktop, String path, byte[] body) throws IOException {
        URL url = new URL("http", desktop.host(), desktop.port(), "/athlete-track/" + path);
        HttpURLConnection request = (HttpURLConnection) url.openConnection();
        request.setRequestMethod("POST");
        request.setConnectTimeout(4_000);
        request.setReadTimeout(5_000);
        request.setRequestProperty(TOKEN_HEADER, desktop.token());
        request.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        request.setDoOutput(true);
        if (body.length > 0) request.getOutputStream().write(body);
        int status = request.getResponseCode();
        request.disconnect();
        return status;
    }

    private boolean isPrivateIpv4(String host) {
        if (host == null || !host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) return false;
        String[] parts = host.split("\\.");
        int[] numbers = new int[4];
        for (int index = 0; index < parts.length; index++) {
            numbers[index] = Integer.parseInt(parts[index]);
            if (numbers[index] > 255) return false;
        }
        return numbers[0] == 10 || (numbers[0] == 192 && numbers[1] == 168)
            || (numbers[0] == 172 && numbers[1] >= 16 && numbers[1] <= 31);
    }

    private void resetPairing() {
        connection = null;
        lastValue = "";
        setStatus("Disconnected. Scan a new pairing QR shown by Athlete Track on the computer.");
    }

    private void setStatus(String message) {
        runOnUiThread(() -> statusText.setText(message));
    }

    private void vibrate() {
        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(80);
        }
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        barcodeScanner.close();
        cameraExecutor.shutdownNow();
        networkExecutor.shutdownNow();
    }

    private record DesktopConnection(String host, int port, String token) {}
}
