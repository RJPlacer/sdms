package com.sdms.controller;

import com.sdms.model.Athlete;
import com.sdms.model.Coach;
import com.sdms.model.Official;
import com.sdms.service.DatabaseService;
import com.sdms.service.PDFExportService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RosterController {

    @FXML private ComboBox<String> cmbSchool;
    @FXML private CheckBox chkAthletes, chkOfficials, chkCoaches;
    @FXML private Label lblStatus;
    @FXML private Button btnExport;
    @FXML private ProgressBar progressBar;

    @FXML public void initialize() {
        chkAthletes.setSelected(true);
        chkOfficials.setSelected(true);
        chkCoaches.setSelected(true);
        progressBar.setVisible(false);
        lblStatus.setText("Select a school/division and click Export.");

        try {
            List<String> schools = DatabaseService.getInstance().getAllSchools();
            cmbSchool.getItems().add("All Schools");
            cmbSchool.getItems().addAll(schools);
            cmbSchool.setValue("All Schools");
        } catch (Exception e) {
            lblStatus.setText("Error loading schools: " + e.getMessage());
        }
    }

    @FXML private void onExport() {
        if (!chkAthletes.isSelected() && !chkOfficials.isSelected() && !chkCoaches.isSelected()) {
            lblStatus.setText("Select at least one module."); return;
        }

        String school = cmbSchool.getValue();
        boolean allSchools = school == null || "All Schools".equals(school);

        btnExport.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        lblStatus.setText("Generating roster PDF...");

        new Thread(() -> {
            try {
                List<Athlete> athletes = chkAthletes.isSelected()
                    ? (allSchools ? DatabaseService.getInstance().getAllAthletes()
                                  : DatabaseService.getInstance().getAthletesBySchool(school))
                    : new ArrayList<>();

                List<Official> officials = chkOfficials.isSelected()
                    ? (allSchools ? DatabaseService.getInstance().getAllOfficials()
                                  : DatabaseService.getInstance().getOfficialsBySchool(school))
                    : new ArrayList<>();

                List<Coach> coaches = chkCoaches.isSelected()
                    ? (allSchools ? DatabaseService.getInstance().getAllCoaches()
                                  : DatabaseService.getInstance().getCoachesBySchool(school))
                    : new ArrayList<>();

                String path = PDFExportService.exportDelegationRoster(
                    allSchools ? "All Schools" : school, athletes, officials, coaches);

                javafx.application.Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    btnExport.setDisable(false);
                    int total = athletes.size() + officials.size() + coaches.size();
                    lblStatus.setText("Done! " + total + " members exported.");
                    try { Desktop.getDesktop().open(new File(path)); } catch (Exception ignored) {}
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    btnExport.setDisable(false);
                    lblStatus.setText("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML private void onClose() { ((Stage) btnExport.getScene().getWindow()).close(); }
}
