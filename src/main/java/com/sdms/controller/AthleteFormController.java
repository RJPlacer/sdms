package com.sdms.controller;

import com.sdms.model.Athlete;
import com.sdms.service.DatabaseService;
import com.sdms.service.QRCodeService;
import com.sdms.util.AppPaths;
import com.sdms.util.AgeUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;

public class AthleteFormController {

    // ── A. Personal Data ──────────────────────────────────────────────────────
    @FXML private TextField txtLastName, txtFirstName, txtMiddleInitial;
    @FXML private ComboBox<String> cmbSex;
    @FXML private TextField txtLRN, txtContactNo, txtAge, txtPlaceOfBirth, txtSchool;
    @FXML private DatePicker dpDateOfBirth;
    @FXML private TextArea txaSchoolAddress, txaPresentAddress;
    @FXML private ImageView imgPhoto;
    @FXML private Label lblPhotoPath, lblGeneratedId;

    // ── Guardian ──────────────────────────────────────────────────────────────
    @FXML private TextField txtMotherName, txtFatherName;
    @FXML private TextArea txaGuardianAddress;

    // ── B. Previous Palaro ────────────────────────────────────────────────────
    @FXML private TableView<Athlete.PalaroPrevious> tblPalaro;
    @FXML private TableColumn<Athlete.PalaroPrevious, String> colPalaroYear, colPalaroEvent, colPalaroVenue, colPalaroRemarks;
    @FXML private TextField txtPalaroYear, txtPalaroEvent, txtPalaroVenue, txtPalaroRemarks;

    // ── C. Lower Meets ────────────────────────────────────────────────────────
    @FXML private TableView<Athlete.LowerMeet> tblMeets;
    @FXML private TableColumn<Athlete.LowerMeet, String> colMeetDates, colMeetEvent, colMeetName, colMeetRemarks;
    @FXML private TextField txtMeetDates, txtMeetEvent, txtMeetName, txtMeetRemarks;

    // ── D. Certification ──────────────────────────────────────────────────────
    @FXML private TextField txtCertMeet, txtCertCoach, txtCertDso, txtCertRso;

    private Athlete athlete;
    private Runnable onSave;
    private String selectedPhotoPath;

    private final ObservableList<Athlete.PalaroPrevious> palaroList = FXCollections.observableArrayList();
    private final ObservableList<Athlete.LowerMeet> meetList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        cmbSex.getItems().addAll("Male", "Female");

        dpDateOfBirth.valueProperty().addListener((obs, old, date) -> {
            if (date == null || date.isAfter(LocalDate.now())) txtAge.clear();
            else txtAge.setText(String.valueOf(AgeUtil.calculate(date)));
        });

        // B. Palaro table
        colPalaroYear.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().year));
        colPalaroEvent.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().sportsEvent));
        colPalaroVenue.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().venue));
        colPalaroRemarks.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().remarks));
        tblPalaro.setItems(palaroList);

        // C. Lower meets table
        colMeetDates.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().inclusiveDates));
        colMeetEvent.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().sportsEvent));
        colMeetName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().athleticMeet));
        colMeetRemarks.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().remarks));
        tblMeets.setItems(meetList);
    }

    public void setAthlete(Athlete a) {
        this.athlete = a;
        populateForm(a);
    }

    public void setOnSave(Runnable cb) { this.onSave = cb; }

    private void populateForm(Athlete a) {
        lblGeneratedId.setText(a.getAthleteId() != null ? "ID: " + a.getAthleteId() : "ID: (auto-generated)");
        setText(txtLastName, a.getLastName());
        setText(txtFirstName, a.getFirstName());
        setText(txtMiddleInitial, a.getMiddleInitial());
        cmbSex.setValue(a.getSex());
        setText(txtLRN, a.getLearnerRefNumber());
        setText(txtContactNo, a.getContactNo());
        dpDateOfBirth.setValue(a.getDateOfBirth());
        txtAge.setText(a.getAge() > 0 ? String.valueOf(a.getAge()) : "");
        setText(txtPlaceOfBirth, a.getPlaceOfBirth());
        setText(txtSchool, a.getSchool());
        setText(txaSchoolAddress, a.getSchoolAddress());
        setText(txaPresentAddress, a.getPresentAddress());
        setText(txtMotherName, a.getMotherName());
        setText(txtFatherName, a.getFatherName());
        setText(txaGuardianAddress, a.getGuardianAddress());
        setText(txtCertMeet, a.getCertMeet());
        setText(txtCertCoach, a.getCertCoachName());
        setText(txtCertDso, a.getCertDsoName());
        setText(txtCertRso, a.getCertRsoName());

        if (a.getPhotoPath() != null && !a.getPhotoPath().isBlank()) {
            selectedPhotoPath = a.getPhotoPath();
            lblPhotoPath.setText(new File(selectedPhotoPath).getName());
            try { imgPhoto.setImage(new Image("file:" + selectedPhotoPath)); } catch (Exception ignored) {}
        }

        if (a.getPreviousPalaroRecords() != null) palaroList.setAll(a.getPreviousPalaroRecords());
        if (a.getLowerMeetRecords() != null)      meetList.setAll(a.getLowerMeetRecords());
    }

    // ── Photo ─────────────────────────────────────────────────────────────────

    @FXML private void onChoosePhoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select 1x1 Photo");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png","*.jpg","*.jpeg"));
        File chosen = fc.showOpenDialog(txtLastName.getScene().getWindow());
        if (chosen != null) {
            try {
                Path dir = AppPaths.photosDir();
                Path dest = dir.resolve(chosen.getName());
                Files.copy(chosen.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
                selectedPhotoPath = dest.toAbsolutePath().toString();
                lblPhotoPath.setText(chosen.getName());
                imgPhoto.setImage(new Image("file:" + selectedPhotoPath));
            } catch (Exception e) { showError("Could not copy photo: " + e.getMessage()); }
        }
    }

    // ── B. Previous Palaro actions ────────────────────────────────────────────

    @FXML private void onAddPalaro() {
        if (txtPalaroEvent.getText().isBlank()) { showError("Sports Event is required."); return; }
        Athlete.PalaroPrevious r = new Athlete.PalaroPrevious();
        r.year        = txtPalaroYear.getText().trim();
        r.sportsEvent = txtPalaroEvent.getText().trim();
        r.venue       = txtPalaroVenue.getText().trim();
        r.remarks     = txtPalaroRemarks.getText().trim();
        palaroList.add(r);
        txtPalaroYear.clear(); txtPalaroEvent.clear();
        txtPalaroVenue.clear(); txtPalaroRemarks.clear();
    }

    @FXML private void onRemovePalaro() {
        Athlete.PalaroPrevious sel = tblPalaro.getSelectionModel().getSelectedItem();
        if (sel != null) palaroList.remove(sel);
    }

    // ── C. Lower Meets actions ────────────────────────────────────────────────

    @FXML private void onAddMeet() {
        if (txtMeetEvent.getText().isBlank()) { showError("Sports Event is required."); return; }
        Athlete.LowerMeet m = new Athlete.LowerMeet();
        m.inclusiveDates = txtMeetDates.getText().trim();
        m.sportsEvent    = txtMeetEvent.getText().trim();
        m.athleticMeet   = txtMeetName.getText().trim();
        m.remarks        = txtMeetRemarks.getText().trim();
        meetList.add(m);
        txtMeetDates.clear(); txtMeetEvent.clear();
        txtMeetName.clear(); txtMeetRemarks.clear();
    }

    @FXML private void onRemoveMeet() {
        Athlete.LowerMeet sel = tblMeets.getSelectionModel().getSelectedItem();
        if (sel != null) meetList.remove(sel);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @FXML private void onSave() {
        if (!validateForm()) return;
        collectFormData();
        try {
            DatabaseService.getInstance().saveAthleteWithRecords(
                athlete, new ArrayList<>(palaroList), new ArrayList<>(meetList));

            // Generate QR if missing
            if (athlete.getQrCodePath() == null || athlete.getQrCodePath().isBlank()) {
                String payload = QRCodeService.buildAthletePayload(
                    athlete.getAthleteId(), athlete.getFullName(), athlete.getSchool());
                String qrPath = QRCodeService.generateQRCode(payload, athlete.getAthleteId());
                athlete.setQrCodePath(qrPath);
                DatabaseService.getInstance().updateAthleteQrCode(athlete.getId(), qrPath);
            }

            lblGeneratedId.setText("ID: " + athlete.getAthleteId());
            if (onSave != null) onSave.run();
            close();
        } catch (Exception e) { showError("Save failed: " + e.getMessage()); }
    }

    @FXML private void onCancel() { close(); }

    private void collectFormData() {
        athlete.setLastName(txtLastName.getText().trim());
        athlete.setFirstName(txtFirstName.getText().trim());
        athlete.setMiddleInitial(txtMiddleInitial.getText().trim());
        athlete.setSex(cmbSex.getValue());
        athlete.setLearnerRefNumber(txtLRN.getText().trim());
        athlete.setContactNo(txtContactNo.getText().trim());
        athlete.setDateOfBirth(dpDateOfBirth.getValue());
        athlete.setAge(dpDateOfBirth.getValue() != null ? AgeUtil.calculate(dpDateOfBirth.getValue()) : 0);
        athlete.setPlaceOfBirth(txtPlaceOfBirth.getText().trim());
        athlete.setSchool(txtSchool.getText().trim());
        athlete.setSchoolAddress(txaSchoolAddress.getText().trim());
        athlete.setPresentAddress(txaPresentAddress.getText().trim());
        athlete.setPhotoPath(selectedPhotoPath);
        athlete.setMotherName(txtMotherName.getText().trim());
        athlete.setFatherName(txtFatherName.getText().trim());
        athlete.setGuardianAddress(txaGuardianAddress.getText().trim());
        athlete.setCertMeet(txtCertMeet.getText().trim());
        athlete.setCertCoachName(txtCertCoach.getText().trim());
        athlete.setCertDsoName(txtCertDso.getText().trim());
        athlete.setCertRsoName(txtCertRso.getText().trim());
    }

    private boolean validateForm() {
        if (txtLastName.getText().isBlank())  { showError("Last name is required."); return false; }
        if (txtFirstName.getText().isBlank()) { showError("First name is required."); return false; }
        if (cmbSex.getValue() == null)        { showError("Sex is required."); return false; }
        if (dpDateOfBirth.getValue() != null && dpDateOfBirth.getValue().isAfter(LocalDate.now())) {
            showError("Birth date cannot be in the future."); return false;
        }
        return true;
    }

    private void setText(javafx.scene.control.TextInputControl c, String v) {
        if (c != null) c.setText(v != null ? v : "");
    }
    private void close() { ((Stage) txtLastName.getScene().getWindow()).close(); }
    private void showError(String msg) { new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait(); }
}
