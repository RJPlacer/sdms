package com.sdms.controller;

import com.sdms.model.Athlete;
import com.sdms.model.Coach;
import com.sdms.model.Official;
import com.sdms.service.DatabaseService;
import com.sdms.service.PDFExportService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.util.List;

public class BatchPrintController {

    @FXML private CheckBox chkAthletes, chkOfficials, chkCoaches;
    @FXML private Label lblStatus;
    @FXML private Button btnExport;
    @FXML private ProgressBar progressBar;

    @FXML public void initialize() {
        chkAthletes.setSelected(true);
        chkOfficials.setSelected(true);
        chkCoaches.setSelected(true);
        progressBar.setVisible(false);
        lblStatus.setText("Select which modules to include, then click Export.");
    }

    @FXML private void onExport() {
        if (!chkAthletes.isSelected() && !chkOfficials.isSelected() && !chkCoaches.isSelected()) {
            lblStatus.setText("Select at least one module.");
            return;
        }

        btnExport.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1); // indeterminate
        lblStatus.setText("Generating PDF...");

        // Run in background thread so UI doesn't freeze
        new Thread(() -> {
            try {
                List<Athlete>  athletes  = chkAthletes.isSelected()  ? DatabaseService.getInstance().getAllAthletes()  : List.of();
                List<Official> officials = chkOfficials.isSelected() ? DatabaseService.getInstance().getAllOfficials() : List.of();
                List<Coach>    coaches   = chkCoaches.isSelected()   ? DatabaseService.getInstance().getAllCoaches()   : List.of();

                String path = PDFExportService.exportBatchIdCards(athletes, officials, coaches);

                javafx.application.Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    btnExport.setDisable(false);
                    int total = athletes.size() + officials.size() + coaches.size();
                    lblStatus.setText("Done! " + total + " ID cards exported.");
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
