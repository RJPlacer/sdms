package com.sdms.controller;

import com.sdms.model.Equipment;
import com.sdms.service.DatabaseService;
import com.sdms.service.PDFExportService;
import javafx.collections.FXCollections;
import com.sdms.util.SessionManager;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

public class EquipmentController {

    @FXML private TableView<Equipment> equipmentTable;
    @FXML private TableColumn<Equipment, String> colEquipmentId, colBorrower, colSchool, colEvent, colDate;
    @FXML private TextField searchField;
    @FXML private Label lblPreviewId, lblPreviewBorrower, lblPreviewSchool, lblPreviewEvent, lblPreviewDate;

    private final ObservableList<Equipment> equipmentList = FXCollections.observableArrayList();

    @FXML public void initialize() {
        colEquipmentId.setCellValueFactory(new PropertyValueFactory<>("equipmentId"));
        colBorrower.setCellValueFactory(new PropertyValueFactory<>("borrowerName"));
        colSchool.setCellValueFactory(new PropertyValueFactory<>("school"));
        colEvent.setCellValueFactory(new PropertyValueFactory<>("event"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        equipmentTable.setItems(equipmentList);
        equipmentTable.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, sel) -> updatePreview(sel));
        searchField.textProperty().addListener((obs, old, text) -> {
            if (text == null || text.isBlank()) loadAll(); else doSearch(text);
        });
        loadAll();
    }

    private void loadAll() {
        try { equipmentList.setAll(DatabaseService.getInstance().getAllEquipment()); }
        catch (SQLException e) { showError("Failed to load records: " + e.getMessage()); }
    }

    private void doSearch(String q) {
        try { equipmentList.setAll(DatabaseService.getInstance().searchEquipment(q)); }
        catch (SQLException e) { showError("Search error: " + e.getMessage()); }
    }

    private void updatePreview(Equipment e) {
        if (e == null) {
            lblPreviewId.setText("—"); lblPreviewBorrower.setText("");
            lblPreviewSchool.setText(""); lblPreviewEvent.setText(""); lblPreviewDate.setText(""); return;
        }
        lblPreviewId.setText(e.getEquipmentId()); lblPreviewBorrower.setText(e.getBorrowerName());
        lblPreviewSchool.setText(e.getSchool()); lblPreviewEvent.setText(e.getEvent());
        lblPreviewDate.setText(e.getDate() != null ? e.getDate().toString() : "");
    }

    @FXML private void onAdd() {
        if (!SessionManager.canEdit()) { showInfo("No permission to add records."); return; }
        openForm(new Equipment());
    }
    @FXML private void onEdit() {
        if (!SessionManager.canEdit()) { showInfo("No permission to edit records."); return; }
        Equipment sel = equipmentTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select a record to edit."); return; }
        openForm(sel);
    }
    @FXML private void onDelete() {
        if (!SessionManager.canDelete()) { showInfo("No permission to delete records."); return; }
        Equipment sel = equipmentTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select a record to delete."); return; }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete record " + sel.getEquipmentId() + "?", ButtonType.YES, ButtonType.CANCEL);
        Optional<ButtonType> r = c.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.YES) {
            try { DatabaseService.getInstance().deleteEquipment(sel.getId()); loadAll(); }
            catch (SQLException e) { showError("Delete failed: " + e.getMessage()); }
        }
    }

    @FXML private void onExportReport() {
        Equipment sel = equipmentTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showInfo("Select a record first."); return; }
        try {
            // Load items before export
            sel.setItems(DatabaseService.getInstance().getEquipmentItems(sel.getId()));
            String path = PDFExportService.exportEquipmentReport(sel);
            Desktop.getDesktop().open(new File(path));
        } catch (Exception e) { showError("Export failed: " + e.getMessage()); }
    }

    private void openForm(Equipment equipment) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/EquipmentForm.fxml"));
            Parent root = loader.load();
            EquipmentFormController ctrl = loader.getController();
            ctrl.setEquipment(equipment); ctrl.setOnSave(this::loadAll);
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle(equipment.getId() == 0 ? "New Equipment Record" : "Edit Equipment Record");
            dialog.setScene(new Scene(root, 800, 600));
            dialog.showAndWait();
        } catch (IOException e) { showError("Could not open form: " + e.getMessage()); }
    }

    private void showError(String msg) { new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait(); }
    private void showInfo(String msg)  { new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait(); }
}
