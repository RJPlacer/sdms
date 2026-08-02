package com.sdms.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class TeamDialogController {

    @FXML private VBox root;

    @FXML private void onClose() {
        Stage stage = (Stage) root.getScene().getWindow();
        stage.close();
    }
}
