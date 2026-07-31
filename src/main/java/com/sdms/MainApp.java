package com.sdms;

import com.sdms.service.DatabaseService;
import com.sdms.util.AppPaths;
import com.sdms.util.AppInfo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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
            DatabaseService.getInstance().initializeDatabase();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            URL iconUrl = getClass().getResource("/images/app-logo.png");
            if (iconUrl != null) {
                primaryStage.getIcons().add(new Image(iconUrl.toExternalForm()));
            }

            primaryStage.setTitle(AppInfo.appName() + " - Login (" + AppInfo.displayVersion() + ")");
            primaryStage.setScene(scene);
            primaryStage.setWidth(520);
            primaryStage.setHeight(520);
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
