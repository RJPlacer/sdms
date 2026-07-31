package com.sdms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;

public class TeamDialogController {

    private static final String DEPTRACK_URL = "https://depedsdocityofbaliwagdeptrack.com/pages/landingPage.php";
    @FXML private VBox root;

    @FXML private void onOpenDepTrack() {
        try {
            Desktop.getDesktop().browse(URI.create(DEPTRACK_URL));
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Could not open DepTrack site: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    @FXML private void onClose() {
        Stage stage = (Stage) root.getScene().getWindow();
        stage.close();
    }
}
