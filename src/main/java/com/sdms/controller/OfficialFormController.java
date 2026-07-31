package com.sdms.controller;

import com.sdms.model.Official;
import com.sdms.service.DatabaseService;
import com.sdms.service.QRCodeService;
import com.sdms.util.AppPaths;
import com.sdms.util.AgeUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

public class OfficialFormController {

    // ── A. Personal Data ──────────────────────────────────────────────────────
    @FXML private TextField txtLastName, txtFirstName, txtMiddleInitial;
    @FXML private ComboBox<String> cmbSex;
    @FXML private TextField txtMobile, txtAge, txtPlaceOfBirth;
    @FXML private DatePicker dpDateOfBirth;
    @FXML private TextField txtPosition, txtYearsService, txtSchool, txtEmployeeNo;
    @FXML private TextArea txaSchoolAddress, txaPresentAddress;
    @FXML private TextField txtEmergencyName, txtEmergencyNo;
    @FXML private ImageView imgPhoto;
    @FXML private Label lblPhotoPath, lblGeneratedId;

    // ── B. Education ──────────────────────────────────────────────────────────
    @FXML private TableView<Official.EducationalQualification> tblEducation;
    @FXML private TableColumn<Official.EducationalQualification, String> colEduLevel, colEduCourse, colEduSchool, colEduYear, colEduAwards;
    @FXML private ComboBox<String> cmbEduLevel;
    @FXML private TextField txtEduCourse, txtEduSchool, txtEduYear, txtEduCredits, txtEduAwards;

    // ── C. Sports Training ────────────────────────────────────────────────────
    @FXML private TableView<Official.SportsTraining> tblTraining;
    @FXML private TableColumn<Official.SportsTraining, String> colTrainTitle, colTrainDate, colTrainHours, colTrainBy;
    @FXML private TextField txtTrainTitle, txtTrainDate, txtTrainHours, txtTrainBy;

    // ── D. Track Record ───────────────────────────────────────────────────────
    @FXML private TableView<Official.TrackRecord> tblTrack;
    @FXML private TableColumn<Official.TrackRecord, String> colTrackMeet, colTrackDates, colTrackEvent, colTrackAwards;
    @FXML private TextField txtTrackMeet, txtTrackDates, txtTrackEvent, txtTrackAwards;

    private Official official;
    private Runnable onSave;
    private String selectedPhotoPath;

    private final ObservableList<Official.EducationalQualification> educationList = FXCollections.observableArrayList();
    private final ObservableList<Official.SportsTraining> trainingList = FXCollections.observableArrayList();
    private final ObservableList<Official.TrackRecord> trackList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        cmbSex.getItems().addAll("Male", "Female");
        cmbEduLevel.getItems().addAll("College", "Post Graduate");

        dpDateOfBirth.valueProperty().addListener((obs, old, date) -> {
            if (date == null || date.isAfter(LocalDate.now())) txtAge.clear();
            else txtAge.setText(String.valueOf(AgeUtil.calculate(date)));
        });

        // Education table
        colEduLevel.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().level));
        colEduCourse.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().course));
        colEduSchool.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().school));
        colEduYear.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().yearGraduated));
        colEduAwards.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().awardsReceived));
        tblEducation.setItems(educationList);

        // Training table
        colTrainTitle.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().title));
        colTrainDate.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().dateOfTraining));
        colTrainHours.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.valueOf(d.getValue().numberOfHours)));
        colTrainBy.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().conductedBy));
        tblTraining.setItems(trainingList);

        // Track record table
        colTrackMeet.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().athleteMeetAttended));
        colTrackDates.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().inclusiveDates));
        colTrackEvent.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().event));
        colTrackAwards.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().awardsReceived));
        tblTrack.setItems(trackList);
    }

    public void setOfficial(Official o) {
        this.official = o;
        populateForm(o);
    }

    public void setOnSave(Runnable cb) { this.onSave = cb; }

    private void populateForm(Official o) {
        lblGeneratedId.setText(o.getOfficialId() != null ? "ID: " + o.getOfficialId() : "ID: (auto-generated)");
        setText(txtLastName, o.getLastName()); setText(txtFirstName, o.getFirstName());
        setText(txtMiddleInitial, o.getMiddleInitial()); cmbSex.setValue(o.getSex());
        setText(txtMobile, o.getMobilePhone()); dpDateOfBirth.setValue(o.getDateOfBirth());
        txtAge.setText(o.getAge() > 0 ? String.valueOf(o.getAge()) : "");
        setText(txtPlaceOfBirth, o.getPlaceOfBirth()); setText(txtPosition, o.getCurrentPosition());
        txtYearsService.setText(o.getYearsInService() > 0 ? String.valueOf(o.getYearsInService()) : "");
        setText(txtSchool, o.getSchool()); setText(txtEmployeeNo, o.getEmployeeNumber());
        setText(txaSchoolAddress, o.getSchoolAddress()); setText(txaPresentAddress, o.getPresentAddress());
        setText(txtEmergencyName, o.getEmergencyContactName()); setText(txtEmergencyNo, o.getEmergencyContactNo());

        if (o.getPhotoPath() != null && !o.getPhotoPath().isBlank()) {
            selectedPhotoPath = o.getPhotoPath();
            lblPhotoPath.setText(new File(selectedPhotoPath).getName());
            try { imgPhoto.setImage(new Image("file:" + selectedPhotoPath)); } catch (Exception ignored) {}
        }
        educationList.setAll(o.getEducationalQualifications());
        trainingList.setAll(o.getSportsTrainings());
        trackList.setAll(o.getTrackRecords());
    }

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

    // ── Education sub-table actions ───────────────────────────────────────────
    @FXML private void onAddEducation() {
        if (txtEduCourse.getText().isBlank()) { showError("Course is required."); return; }
        Official.EducationalQualification eq = new Official.EducationalQualification();
        eq.level = cmbEduLevel.getValue(); eq.course = txtEduCourse.getText().trim();
        eq.school = txtEduSchool.getText().trim(); eq.yearGraduated = txtEduYear.getText().trim();
        eq.creditsEarned = txtEduCredits.getText().trim(); eq.awardsReceived = txtEduAwards.getText().trim();
        educationList.add(eq);
        cmbEduLevel.setValue(null); txtEduCourse.clear(); txtEduSchool.clear();
        txtEduYear.clear(); txtEduCredits.clear(); txtEduAwards.clear();
    }
    @FXML private void onRemoveEducation() {
        Official.EducationalQualification sel = tblEducation.getSelectionModel().getSelectedItem();
        if (sel != null) educationList.remove(sel);
    }

    // ── Sports Training sub-table actions ─────────────────────────────────────
    @FXML private void onAddTraining() {
        if (txtTrainTitle.getText().isBlank()) { showError("Title is required."); return; }
        Official.SportsTraining st = new Official.SportsTraining();
        st.title = txtTrainTitle.getText().trim(); st.dateOfTraining = txtTrainDate.getText().trim();
        try { st.numberOfHours = Integer.parseInt(txtTrainHours.getText().trim()); } catch (NumberFormatException ignored) {}
        st.conductedBy = txtTrainBy.getText().trim();
        trainingList.add(st);
        txtTrainTitle.clear(); txtTrainDate.clear(); txtTrainHours.clear(); txtTrainBy.clear();
    }
    @FXML private void onRemoveTraining() {
        Official.SportsTraining sel = tblTraining.getSelectionModel().getSelectedItem();
        if (sel != null) trainingList.remove(sel);
    }

    // ── Track Record sub-table actions ────────────────────────────────────────
    @FXML private void onAddTrack() {
        if (txtTrackMeet.getText().isBlank()) { showError("Meet is required."); return; }
        Official.TrackRecord tr = new Official.TrackRecord();
        tr.athleteMeetAttended = txtTrackMeet.getText().trim();
        tr.inclusiveDates = txtTrackDates.getText().trim();
        tr.event = txtTrackEvent.getText().trim();
        tr.awardsReceived = txtTrackAwards.getText().trim();
        trackList.add(tr);
        txtTrackMeet.clear(); txtTrackDates.clear(); txtTrackEvent.clear(); txtTrackAwards.clear();
    }
    @FXML private void onRemoveTrack() {
        Official.TrackRecord sel = tblTrack.getSelectionModel().getSelectedItem();
        if (sel != null) trackList.remove(sel);
    }

    @FXML private void onSave() {
        if (!validateForm()) return;
        collectFormData();
        try {
            DatabaseService.getInstance().saveOfficial(official);
            QRCodeService.refreshOfficialQRCode(official);
            lblGeneratedId.setText("ID: " + official.getOfficialId());
            if (onSave != null) onSave.run();
            close();
        } catch (Exception e) { showError("Save failed: " + e.getMessage()); }
    }

    @FXML private void onCancel() { close(); }

    private void collectFormData() {
        official.setLastName(txtLastName.getText().trim());
        official.setFirstName(txtFirstName.getText().trim());
        official.setMiddleInitial(txtMiddleInitial.getText().trim());
        official.setSex(cmbSex.getValue());
        official.setMobilePhone(txtMobile.getText().trim());
        official.setDateOfBirth(dpDateOfBirth.getValue());
        official.setAge(dpDateOfBirth.getValue() != null ? AgeUtil.calculate(dpDateOfBirth.getValue()) : 0);
        official.setPlaceOfBirth(txtPlaceOfBirth.getText().trim());
        official.setCurrentPosition(txtPosition.getText().trim());
        try { official.setYearsInService(Integer.parseInt(txtYearsService.getText().trim())); } catch (NumberFormatException ignored) {}
        official.setSchool(txtSchool.getText().trim());
        official.setEmployeeNumber(txtEmployeeNo.getText().trim());
        official.setSchoolAddress(txaSchoolAddress.getText().trim());
        official.setPresentAddress(txaPresentAddress.getText().trim());
        official.setEmergencyContactName(txtEmergencyName.getText().trim());
        official.setEmergencyContactNo(txtEmergencyNo.getText().trim());
        official.setPhotoPath(selectedPhotoPath);
        official.setEducationalQualifications(new java.util.ArrayList<>(educationList));
        official.setSportsTrainings(new java.util.ArrayList<>(trainingList));
        official.setTrackRecords(new java.util.ArrayList<>(trackList));
    }

    private boolean validateForm() {
        if (txtLastName.getText().isBlank()) { showError("Last name is required."); return false; }
        if (txtFirstName.getText().isBlank()) { showError("First name is required."); return false; }
        if (cmbSex.getValue() == null) { showError("Sex is required."); return false; }
        if (dpDateOfBirth.getValue() != null && dpDateOfBirth.getValue().isAfter(LocalDate.now())) {
            showError("Birth date cannot be in the future."); return false;
        }
        return true;
    }

    private void setText(javafx.scene.control.TextInputControl c, String v) { if (c != null) c.setText(v != null ? v : ""); }
    private void close() { ((Stage) txtLastName.getScene().getWindow()).close(); }
    private void showError(String msg) { new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait(); }
}
