package com.sdms;

import com.sdms.service.DatabaseService;
import com.sdms.service.BackupService;
import com.sdms.sharing.SharingConfig;
import com.sdms.sharing.SharingManager;
import com.sdms.util.AppPaths;
import com.sdms.util.AppInfo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            if (!initializeDataLayer()) {
                Platform.exit();
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            URL iconUrl = getClass().getResource("/images/app-logo.png");
            if (iconUrl != null) {
                primaryStage.getIcons().add(new Image(iconUrl.toExternalForm()));
            }

            primaryStage.setTitle(AppInfo.appName() + " - Login (" + AppInfo.displayVersion() + ")");
            primaryStage.setScene(scene);
            primaryStage.setWidth(980);
            primaryStage.setHeight(680);
            primaryStage.setMinWidth(860);
            primaryStage.setMinHeight(620);
            primaryStage.setResizable(true);
            primaryStage.centerOnScreen();
            primaryStage.show();
        } catch (Exception ex) {
            showStartupError(ex);
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        DatabaseService.getInstance().closeConnection();
        SharingManager.getInstance().close();
    }

    private boolean initializeDataLayer() {
        while (true) {
            try {
                SharingManager.getInstance().initializeAtStartup();
                DatabaseService.getInstance().initializeDatabase();
                BackupService.startAutomaticBackupIfDue();
                return true;
            } catch (Exception ex) {
                SharingManager sharing = SharingManager.getInstance();
                if (sharing.getMode() == SharingConfig.Mode.CLIENT && sharing.tryRediscoverClient()) {
                    continue;
                }
                ButtonType retry = new ButtonType("Try Again", ButtonBar.ButtonData.OK_DONE);
                ButtonType exit = new ButtonType("Exit", ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert alert;
                if (sharing.getMode() == SharingConfig.Mode.CLIENT) {
                    ButtonType local = new ButtonType("Use This Computer Only", ButtonBar.ButtonData.OTHER);
                    alert = new Alert(Alert.AlertType.ERROR,
                        "Athlete Track could not reach the main computer.\n\n"
                            + "Check that the main computer is turned on and both computers are connected to the same Wi-Fi.",
                        retry, local, exit);
                    alert.setTitle("Main Computer Unavailable");
                    alert.setHeaderText("Shared records are temporarily unavailable");
                    ButtonType choice = alert.showAndWait().orElse(exit);
                    if (choice == retry) continue;
                    if (choice == local) {
                        try {
                            sharing.useLocalDatabase();
                            return true;
                        } catch (Exception localError) {
                            showStartupError(localError);
                            return false;
                        }
                    }
                    return false;
                }
                alert = new Alert(Alert.AlertType.ERROR,
                    "Athlete Track could not start the shared database.\n\n"
                        + "Close any other copy of Athlete Track on this computer, then try again.",
                    retry, exit);
                alert.setTitle("Shared Database Could Not Start");
                alert.setHeaderText("The main computer service is unavailable");
                if (alert.showAndWait().orElse(exit) == retry) continue;
                showStartupError(ex);
                return false;
            }
        }
    }

    private void showStartupError(Exception ex) {
        Path logFile = AppPaths.logsDir().resolve("startup-error.log");
        try {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Files.writeString(logFile, sw.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}

        String msg = "Startup failed.\n\n"
            + ex.getMessage()
            + "\n\nLog: " + logFile;
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    public static void main(String[] args) { launch(args); }
}
