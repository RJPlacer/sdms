package com.sdms.controller;

import com.sdms.model.Equipment;
import com.sdms.service.DatabaseService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.ArrayList;

public class EquipmentFormController {

    // ── Borrower Info ─────────────────────────────────────────────────────────
    @FXML private TextField txtBorrowerName, txtDesignation, txtSchool, txtEvent, txtMobileNo;
    @FXML private DatePicker dpDate;
    @FXML private Label lblGeneratedId;

    // ── Insurance ─────────────────────────────────────────────────────────────
    @FXML private TextField txtIssuedBy, txtIssuedTo;

    // ── Equipment Items sub-table ─────────────────────────────────────────────
    @FXML private TableView<Equipment.EquipmentItem> tblItems;
    @FXML private TableColumn<Equipment.EquipmentItem, String> colQty, colUnit, colDesc, colDateBorrowed, colDateReturned, colRemarks;
    @FXML private TextField txtQty, txtUnit, txtDesc, txtRemarks;
    @FXML private DatePicker dpDateBorrowed, dpDateReturned;

    private Equipment equipment;
    private Runnable onSave;
    private final ObservableList<Equipment.EquipmentItem> itemsList = FXCollections.observableArrayList();

    @FXML public void initialize() {
        colQty.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().qty)));
        colUnit.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().unit));
        colDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().makeAndDescription));
        colDateBorrowed.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().dateBorrowed != null ? d.getValue().dateBorrowed.toString() : ""));
        colDateReturned.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().dateReturned != null ? d.getValue().dateReturned.toString() : ""));
        colRemarks.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().remarks));
        tblItems.setItems(itemsList);
    }

    public void setEquipment(Equipment e) { this.equipment = e; populateForm(e); }
    public void setOnSave(Runnable cb) { this.onSave = cb; }

    private void populateForm(Equipment e) {
        lblGeneratedId.setText(e.getEquipmentId() != null ? "ID: " + e.getEquipmentId() : "ID: (auto-generated)");
        setText(txtBorrowerName, e.getBorrowerName()); setText(txtDesignation, e.getDesignation());
        setText(txtSchool, e.getSchool()); setText(txtEvent, e.getEvent());
        setText(txtMobileNo, e.getMobileNo()); dpDate.setValue(e.getDate());
        setText(txtIssuedBy, e.getIssuedBy()); setText(txtIssuedTo, e.getIssuedTo());
        if (e.getItems() != null) itemsList.setAll(e.getItems());
    }

    @FXML private void onAddItem() {
        if (txtDesc.getText().isBlank()) { showError("Description is required."); return; }
        Equipment.EquipmentItem item = new Equipment.EquipmentItem();
        try { item.qty = Integer.parseInt(txtQty.getText().trim()); } catch (NumberFormatException ignored) { item.qty = 1; }
        item.unit = txtUnit.getText().trim();
        item.makeAndDescription = txtDesc.getText().trim();
        item.dateBorrowed = dpDateBorrowed.getValue();
        item.dateReturned = dpDateReturned.getValue();
        item.remarks = txtRemarks.getText().trim();
        itemsList.add(item);
        txtQty.clear(); txtUnit.clear(); txtDesc.clear(); txtRemarks.clear();
        dpDateBorrowed.setValue(null); dpDateReturned.setValue(null);
    }

    @FXML private void onRemoveItem() {
        Equipment.EquipmentItem sel = tblItems.getSelectionModel().getSelectedItem();
        if (sel != null) itemsList.remove(sel);
    }

    @FXML private void onSave() {
        if (txtBorrowerName.getText().isBlank()) { showError("Borrower name is required."); return; }
        collectFormData();
        try {
            DatabaseService.getInstance().saveEquipment(equipment);
            lblGeneratedId.setText("ID: " + equipment.getEquipmentId());
            if (onSave != null) onSave.run();
            close();
        } catch (Exception e) { showError("Save failed: " + e.getMessage()); }
    }

    @FXML private void onCancel() { close(); }

    private void collectFormData() {
        equipment.setBorrowerName(txtBorrowerName.getText().trim());
        equipment.setDesignation(txtDesignation.getText().trim());
        equipment.setSchool(txtSchool.getText().trim());
        equipment.setEvent(txtEvent.getText().trim());
        equipment.setMobileNo(txtMobileNo.getText().trim());
        equipment.setDate(dpDate.getValue());
        equipment.setIssuedBy(txtIssuedBy.getText().trim());
        equipment.setIssuedTo(txtIssuedTo.getText().trim());
        equipment.setItems(new ArrayList<>(itemsList));
    }

    private void setText(javafx.scene.control.TextInputControl c, String v) { if (c != null) c.setText(v != null ? v : ""); }
    private void close() { ((Stage) txtBorrowerName.getScene().getWindow()).close(); }
    private void showError(String msg) { new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait(); }
}
