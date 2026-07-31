package com.sdms.controller;

import com.sdms.model.Coach;
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

public class CoachController {

    @FXML private TableView<Coach> coachTable;
    @FXML private TableColumn<Coach, String> colCoachId, colLastName, colFirstName, colSex, colPosition, colSchool;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> schoolFilter;
    @FXML private Label lblPreviewName, lblPreviewId, lblPreviewPosition, lblPreviewSchool;
    @FXML private ImageView imgPreviewPhoto, imgPreviewQR;

    private final ObservableList<Coach> coaches = FXCollections.observableArrayList();

    @FXML public void initialize() {
        colCoachId.setCellValueFactory(new PropertyValueFactory<>("coachId"));
        colLastName.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        colFirstName.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        colSex.setCellValueFactory(new PropertyValueFactory<>("sex"));
        colPosition.setCellValueFactory(new PropertyValueFactory<>("currentPosition"));
        colSchool.setCellValueFactory(new PropertyValueFactory<>("school"));

        coachTable.setItems(coaches);
        coachTable.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, sel) -> updatePreview(sel));
        searchField.textProperty().addListener((obs, old, text) -> applyFilters());

        try {
            schoolFilter.getItems().add("All Schools");
            schoolFilter.getItems().addAll(DatabaseService.getInstance().getAllSchools());
            schoolFilter.setValue("All Schools");
        } catch (Exception ignored) {}

        loadAll();
    }

    private void loadAll() {
        try { coaches.setAll(DatabaseService.getInstance().getAllCoaches()); }
        catch (SQLException e) { showError("Failed to load: " + e.getMessage()); }
    }

    private void applyFilters() {
        String query = searchField.getText();
        String school = schoolFilter.getValue();
        boolean allSchools = school == null || "All Schools".equals(school);
        try {
            var list = (query != null && !query.isBlank())
                ? DatabaseService.getInstance().searchCoaches(query)
                : DatabaseService.getInstance().getAllCoaches();
            if (!allSchools) list = list.stream().filter(c -> school.equals(c.getSchool())).toList();
            coaches.setAll(list);
        } catch (SQLException e) { showError("Filter error: " + e.getMessage()); }
    }

    @FXML private void onSchoolFilter() { applyFilters(); }

    private void updatePreview(Coach c) {
        if (c == null) {
            lblPreviewName.setText("—"); lblPreviewId.setText("");
            lblPreviewPosition.setText(""); lblPreviewSchool.setText("");
            imgPreviewPhoto.setImage(null); imgPreviewQR.setImage(null); return;
        }
        lblPreviewName.setText(c.getFullName()); lblPreviewId.setText(c.getCoachId());
        lblPreviewPosition.setText(c.getCurrentPosition()); lblPreviewSchool.setText(c.getSchool());
        if (c.getPhotoPath() != null && !c.getPhotoPath().isBlank())
            try { imgPreviewPhoto.setImage(new Image("file:" + c.getPhotoPath())); } catch (Exception ignored) {}
        if (c.getQrCodePath() != null && !c.getQrCodePath().isBlank())
            try { imgPreviewQR.setImage(new Image("file:" + c.getQrCodePath())); } catch (Exception ignored) {}
    }

    @FXML private void onAdd() {
        if (!SessionManager.canEdit()) { showInfo("No permission."); return; }
        openForm(new Coach());
    }
    @FXML private void onEdit() {
        if (!SessionManager.canEdit()) { showInfo("No permission."); return; }
        Coach sel = coachTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select a coach to edit."); return; }
        openForm(sel);
    }
    @FXML private void onDelete() {
        if (!SessionManager.canDelete()) { showInfo("No permission."); return; }
        Coach sel = coachTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select a coach to delete."); return; }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + sel.getFullName() + "?", ButtonType.YES, ButtonType.CANCEL);
        if (c.showAndWait().filter(b -> b == ButtonType.YES).isPresent()) {
            try { DatabaseService.getInstance().deleteCoach(sel.getId()); loadAll(); }
            catch (SQLException e) { showError("Delete failed: " + e.getMessage()); }
        }
    }

    @FXML private void onExportIdCard() {
        Coach sel = coachTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select a coach first."); return; }
        try {
            QRCodeService.refreshCoachQRCode(sel);
            Desktop.getDesktop().open(new File(PDFExportService.exportCoachIdCard(sel)));
        } catch (Exception e) { showError("Export failed: " + e.getMessage()); }
    }

    @FXML private void onExportReport() {
        Coach sel = coachTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select a coach first."); return; }
        try { Desktop.getDesktop().open(new File(PDFExportService.exportCoachReport(sel))); }
        catch (Exception e) { showError("Report failed: " + e.getMessage()); }
    }

    private void openForm(Coach coach) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CoachForm.fxml"));
            Parent root = loader.load();
            CoachFormController ctrl = loader.getController();
            ctrl.setCoach(coach); ctrl.setOnSave(this::loadAll);
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle(coach.getId() == 0 ? "New Coach" : "Edit Coach");
            dialog.setScene(new Scene(root));
            dialog.showAndWait();
        } catch (IOException e) { showError("Could not open form: " + e.getMessage()); }
    }

    private void showError(String msg) { new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait(); }
    private void showInfo(String msg)  { new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait(); }
}
