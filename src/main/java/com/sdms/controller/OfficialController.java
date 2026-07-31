package com.sdms.controller;

import com.sdms.model.Official;
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

public class OfficialController {

    @FXML private TableView<Official> officialTable;
    @FXML private TableColumn<Official, String> colOfficialId, colLastName, colFirstName, colSex, colPosition, colSchool;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> schoolFilter;
    @FXML private Label lblPreviewName, lblPreviewId, lblPreviewPosition, lblPreviewSchool;
    @FXML private ImageView imgPreviewPhoto, imgPreviewQR;

    private final ObservableList<Official> officials = FXCollections.observableArrayList();

    @FXML public void initialize() {
        colOfficialId.setCellValueFactory(new PropertyValueFactory<>("officialId"));
        colLastName.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        colFirstName.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        colSex.setCellValueFactory(new PropertyValueFactory<>("sex"));
        colPosition.setCellValueFactory(new PropertyValueFactory<>("currentPosition"));
        colSchool.setCellValueFactory(new PropertyValueFactory<>("school"));

        officialTable.setItems(officials);
        officialTable.getSelectionModel().selectedItemProperty()
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
        try { officials.setAll(DatabaseService.getInstance().getAllOfficials()); }
        catch (SQLException e) { showError("Failed to load: " + e.getMessage()); }
    }

    private void applyFilters() {
        String query = searchField.getText();
        String school = schoolFilter.getValue();
        boolean allSchools = school == null || "All Schools".equals(school);
        try {
            var list = (query != null && !query.isBlank())
                ? DatabaseService.getInstance().searchOfficials(query)
                : DatabaseService.getInstance().getAllOfficials();
            if (!allSchools) list = list.stream().filter(o -> school.equals(o.getSchool())).toList();
            officials.setAll(list);
        } catch (SQLException e) { showError("Filter error: " + e.getMessage()); }
    }

    @FXML private void onSchoolFilter() { applyFilters(); }

    private void updatePreview(Official o) {
        if (o == null) {
            lblPreviewName.setText("—"); lblPreviewId.setText("");
            lblPreviewPosition.setText(""); lblPreviewSchool.setText("");
            imgPreviewPhoto.setImage(null); imgPreviewQR.setImage(null); return;
        }
        lblPreviewName.setText(o.getFullName()); lblPreviewId.setText(o.getOfficialId());
        lblPreviewPosition.setText(o.getCurrentPosition()); lblPreviewSchool.setText(o.getSchool());
        if (o.getPhotoPath() != null && !o.getPhotoPath().isBlank())
            try { imgPreviewPhoto.setImage(new Image("file:" + o.getPhotoPath())); } catch (Exception ignored) {}
        if (o.getQrCodePath() != null && !o.getQrCodePath().isBlank())
            try { imgPreviewQR.setImage(new Image("file:" + o.getQrCodePath())); } catch (Exception ignored) {}
    }

    @FXML private void onAdd() {
        if (!SessionManager.canEdit()) { showInfo("No permission to add records."); return; }
        openForm(new Official());
    }
    @FXML private void onEdit() {
        if (!SessionManager.canEdit()) { showInfo("No permission to edit records."); return; }
        Official sel = officialTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select an official to edit."); return; }
        openForm(sel);
    }
    @FXML private void onDelete() {
        if (!SessionManager.canDelete()) { showInfo("No permission to delete records."); return; }
        Official sel = officialTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select an official to delete."); return; }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + sel.getFullName() + "?", ButtonType.YES, ButtonType.CANCEL);
        if (c.showAndWait().filter(b -> b == ButtonType.YES).isPresent()) {
            try { DatabaseService.getInstance().deleteOfficial(sel.getId()); loadAll(); }
            catch (SQLException e) { showError("Delete failed: " + e.getMessage()); }
        }
    }

    @FXML private void onExportIdCard() {
        Official sel = officialTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select an official first."); return; }
        try {
            QRCodeService.refreshOfficialQRCode(sel);
            Desktop.getDesktop().open(new File(PDFExportService.exportOfficialIdCard(sel)));
        } catch (Exception e) { showError("Export failed: " + e.getMessage()); }
    }

    @FXML private void onExportReport() {
        Official sel = officialTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select an official first."); return; }
        try { Desktop.getDesktop().open(new File(PDFExportService.exportOfficialReport(sel))); }
        catch (Exception e) { showError("Report failed: " + e.getMessage()); }
    }

    private void openForm(Official official) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/OfficialForm.fxml"));
            Parent root = loader.load();
            OfficialFormController ctrl = loader.getController();
            ctrl.setOfficial(official);
            ctrl.setOnSave(this::loadAll);
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle(official.getId() == 0 ? "New Official" : "Edit Official");
            dialog.setScene(new Scene(root));
            dialog.showAndWait();
        } catch (IOException e) { showError("Could not open form: " + e.getMessage()); }
    }

    private void showError(String msg) { new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait(); }
    private void showInfo(String msg)  { new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait(); }
}
