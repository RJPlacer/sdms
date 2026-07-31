package com.sdms.controller;

import com.sdms.service.DatabaseService;
import com.sdms.util.AppInfo;
import com.sdms.util.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class AboutController {

    @FXML private Label lblAthleteCount, lblOfficialCount, lblCoachCount, lblEquipmentCount, lblUserCount;
    @FXML private Label lblLoggedInUser, lblRole;
    @FXML private Label lblVersion;

    @FXML public void initialize() {
        try {
            DatabaseService db = DatabaseService.getInstance();
            lblAthleteCount.setText(String.valueOf(db.countTable("athletes")));
            lblOfficialCount.setText(String.valueOf(db.countTable("officials")));
            lblCoachCount.setText(String.valueOf(db.countTable("coaches")));
            lblEquipmentCount.setText(String.valueOf(db.countTable("equipment")));
            lblUserCount.setText(String.valueOf(db.countTable("users")));
        } catch (Exception e) {
            lblAthleteCount.setText("—");
        }
        if (SessionManager.isLoggedIn()) {
            lblLoggedInUser.setText(SessionManager.getCurrentUser().getFullName());
            lblRole.setText(SessionManager.getCurrentUser().getRole());
        }
        if (lblVersion != null) {
            lblVersion.setText("Version " + AppInfo.version());
        }
    }

    @FXML private void onClose() {
        ((Stage) lblAthleteCount.getScene().getWindow()).close();
    }
}
