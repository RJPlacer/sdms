package com.sdms.ui;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in visual QA that snapshots only the JavaFX scene, never the user's desktop. */
class SharingDialogSnapshotTest {

    @Test
    @EnabledIfSystemProperty(named = "sdms.ui.snapshot", matches = "true")
    void rendersSharingDialogWithoutLoadingErrors() throws Exception {
        String output = System.getProperty("sdms.ui.snapshot.output");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SharingDialog.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 720, 660);
                scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
                if (root instanceof Region region) region.resize(720, 660);
                root.applyCss();
                root.layout();
                WritableImage image = new WritableImage(720, 660);
                root.snapshot(null, image);
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", Path.of(output).toFile());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                finished.countDown();
                Platform.exit();
            }
        });
        assertTrue(finished.await(20, TimeUnit.SECONDS), "Sharing dialog snapshot timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
        assertTrue(java.nio.file.Files.size(Path.of(output)) > 0);
    }
}
