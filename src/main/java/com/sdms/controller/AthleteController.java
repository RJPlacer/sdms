package com.sdms.controller;

import com.sdms.model.Athlete;
import com.sdms.service.DatabaseService;
import com.sdms.service.PDFExportService;
import com.sdms.service.QRCodeService;
import com.sdms.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

public class AthleteController {

    @FXML private TableView<Athlete> athleteTable;
    @FXML private TableColumn<Athlete, String> colAthleteId, colLastName, colFirstName, colSex, colSchool;
    @FXML private TableColumn<Athlete, Integer> colAge;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> schoolFilter;
    @FXML private Label lblPreviewName, lblPreviewId, lblPreviewSchool;
    @FXML private ImageView imgPreviewPhoto, imgPreviewQR;

    private final ObservableList<Athlete> athletes = FXCollections.observableArrayList();

    @FXML public void initialize() {
        colAthleteId.setCellValueFactory(new PropertyValueFactory<>("athleteId"));
        colLastName.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        colFirstName.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        colSex.setCellValueFactory(new PropertyValueFactory<>("sex"));
        colSchool.setCellValueFactory(new PropertyValueFactory<>("school"));
        colAge.setCellValueFactory(new PropertyValueFactory<>("age"));

        athleteTable.setItems(athletes);
        athleteTable.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, sel) -> updatePreview(sel));

        searchField.textProperty().addListener((obs, old, text) -> applyFilters());

        // Load school filter options
        try {
            schoolFilter.getItems().add("All Schools");
            schoolFilter.getItems().addAll(DatabaseService.getInstance().getAllSchools());
            schoolFilter.setValue("All Schools");
        } catch (Exception ignored) {}

        loadAllAthletes();
    }

    private void loadAllAthletes() {
        try { athletes.setAll(DatabaseService.getInstance().getAllAthletes()); }
        catch (SQLException e) { showError("Failed to load athletes: " + e.getMessage()); }
    }

    private void applyFilters() {
        String query  = searchField.getText();
        String school = schoolFilter.getValue();
        boolean allSchools = school == null || "All Schools".equals(school);

        try {
            var list = (query != null && !query.isBlank())
                ? DatabaseService.getInstance().searchAthletes(query)
                : DatabaseService.getInstance().getAllAthletes();

            if (!allSchools)
                list = list.stream().filter(a -> school.equals(a.getSchool())).toList();

            athletes.setAll(list);
        } catch (SQLException e) { showError("Filter error: " + e.getMessage()); }
    }

    @FXML private void onSchoolFilter() { applyFilters(); }

    private void updatePreview(Athlete a) {
        if (a == null) {
            lblPreviewName.setText("—"); lblPreviewId.setText(""); lblPreviewSchool.setText("");
            imgPreviewPhoto.setImage(null); imgPreviewQR.setImage(null); return;
        }
        lblPreviewName.setText(a.getFullName());
        lblPreviewId.setText(a.getAthleteId());
        lblPreviewSchool.setText(a.getSchool());
        if (a.getPhotoPath() != null && !a.getPhotoPath().isBlank())
            try { imgPreviewPhoto.setImage(new Image("file:" + a.getPhotoPath())); } catch (Exception ignored) {}
        if (a.getQrCodePath() != null && !a.getQrCodePath().isBlank())
            try { imgPreviewQR.setImage(new Image("file:" + a.getQrCodePath())); } catch (Exception ignored) {}
    }

    @FXML private void onAddAthlete() {
        if (!SessionManager.canEdit()) { showInfo("No permission to add records."); return; }
        openForm(new Athlete());
    }

    @FXML private void onEditAthlete() {
        if (!SessionManager.canEdit()) { showInfo("No permission to edit records."); return; }
        Athlete sel = athleteTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select an athlete to edit."); return; }
        try {
            sel.setPreviousPalaroRecords(DatabaseService.getInstance().getAthletePalaroRecords(sel.getId()));
            sel.setLowerMeetRecords(DatabaseService.getInstance().getAthleteLowerMeets(sel.getId()));
        } catch (Exception ignored) {}
        openForm(sel);
    }

    @FXML private void onDeleteAthlete() {
        if (!SessionManager.canDelete()) { showInfo("No permission to delete records."); return; }
        Athlete sel = athleteTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select an athlete to delete."); return; }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete " + sel.getFullName() + "?", ButtonType.YES, ButtonType.CANCEL);
        if (c.showAndWait().filter(b -> b == ButtonType.YES).isPresent()) {
            try { DatabaseService.getInstance().deleteAthlete(sel.getId()); loadAllAthletes(); }
            catch (SQLException e) { showError("Delete failed: " + e.getMessage()); }
        }
    }

    @FXML private void onExportIdCard() {
        Athlete sel = athleteTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select an athlete to export."); return; }
        try {
            if (sel.getQrCodePath() == null || sel.getQrCodePath().isBlank()) {
                String payload = QRCodeService.buildAthletePayload(sel.getAthleteId(), sel.getFullName(), sel.getSchool());
                sel.setQrCodePath(QRCodeService.generateQRCode(payload, sel.getAthleteId()));
                DatabaseService.getInstance().updateAthleteQrCode(sel.getId(), sel.getQrCodePath());
            }
            Desktop.getDesktop().open(new File(PDFExportService.exportAthleteIdCard(sel)));
        } catch (Exception e) { showError("Export failed: " + e.getMessage()); }
    }

    @FXML private void onExportReport() {
        Athlete sel = athleteTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select an athlete first."); return; }
        try { Desktop.getDesktop().open(new File(PDFExportService.exportAthleteReport(sel))); }
        catch (Exception e) { showError("Report export failed: " + e.getMessage()); }
    }

    private void openForm(Athlete athlete) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AthleteForm.fxml"));
            Parent root = loader.load();
            AthleteFormController ctrl = loader.getController();
            ctrl.setAthlete(athlete);
            ctrl.setOnSave(() -> { loadAllAthletes(); refreshSchoolFilter(); });
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle(athlete.getId() == 0 ? "New Athlete" : "Edit Athlete");
            dialog.setScene(new Scene(root));
            dialog.showAndWait();
        } catch (IOException e) { showError("Could not open form: " + e.getMessage()); }
    }

    private void refreshSchoolFilter() {
        String current = schoolFilter.getValue();
        schoolFilter.getItems().clear();
        schoolFilter.getItems().add("All Schools");
        try { schoolFilter.getItems().addAll(DatabaseService.getInstance().getAllSchools()); }
        catch (Exception ignored) {}
        schoolFilter.setValue(current != null ? current : "All Schools");
    }

    private void showError(String msg) { new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait(); }
    private void showInfo(String msg)  { new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait(); }
}
