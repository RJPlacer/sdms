package com.sdms.controller;

import com.sdms.service.DatabaseService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.sql.SQLException;

public class DashboardController {

    @FXML private Label lblAthleteCount;
    @FXML private Label lblOfficialCount;
    @FXML private Label lblCoachCount;
    @FXML private Label lblEquipmentCount;

    @FXML public void initialize() {
        refresh();
    }

    public void refresh() {
        try {
            DatabaseService db = DatabaseService.getInstance();
            lblAthleteCount.setText(String.valueOf(db.countTable("athletes")));
            lblOfficialCount.setText(String.valueOf(db.countTable("officials")));
            lblCoachCount.setText(String.valueOf(db.countTable("coaches")));
            lblEquipmentCount.setText(String.valueOf(db.countTable("equipment")));
        } catch (SQLException e) {
            lblAthleteCount.setText("—");
            lblOfficialCount.setText("—");
            lblCoachCount.setText("—");
            lblEquipmentCount.setText("—");
        }
    }
}
