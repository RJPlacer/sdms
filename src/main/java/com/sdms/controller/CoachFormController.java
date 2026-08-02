package com.sdms.controller;

import com.sdms.model.Coach;
import com.sdms.model.Official;
import com.sdms.service.DatabaseService;
import com.sdms.service.QRCodeService;
import com.sdms.sharing.SharedAssetService;
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
import java.time.LocalDate;
import java.util.ArrayList;

public class CoachFormController {

    @FXML private TextField txtLastName, txtFirstName, txtMiddleInitial;
    @FXML private ComboBox<String> cmbSex;
    @FXML private TextField txtMobile, txtAge, txtPlaceOfBirth;
    @FXML private DatePicker dpDateOfBirth;
    @FXML private TextField txtPosition, txtYearsService, txtSchool, txtEmployeeNo;
    @FXML private TextArea txaSchoolAddress, txaPresentAddress;
    @FXML private TextField txtEmergencyName, txtEmergencyNo;
    @FXML private ImageView imgPhoto;
    @FXML private Label lblPhotoPath, lblGeneratedId;

    @FXML private TableView<Official.EducationalQualification> tblEducation;
    @FXML private TableColumn<Official.EducationalQualification, String> colEduLevel, colEduCourse, colEduSchool, colEduYear, colEduAwards;
    @FXML private ComboBox<String> cmbEduLevel;
    @FXML private TextField txtEduCourse, txtEduSchool, txtEduYear, txtEduCredits, txtEduAwards;

    @FXML private TableView<Official.SportsTraining> tblTraining;
    @FXML private TableColumn<Official.SportsTraining, String> colTrainTitle, colTrainDate, colTrainHours, colTrainBy;
    @FXML private TextField txtTrainTitle, txtTrainDate, txtTrainHours, txtTrainBy;

    @FXML private TableView<Official.TrackRecord> tblTrack;
    @FXML private TableColumn<Official.TrackRecord, String> colTrackMeet, colTrackDates, colTrackEvent, colTrackAwards;
    @FXML private TextField txtTrackMeet, txtTrackDates, txtTrackEvent, txtTrackAwards;

    private Coach coach;
    private Runnable onSave;
    private String selectedPhotoPath;

    private final ObservableList<Official.EducationalQualification> educationList = FXCollections.observableArrayList();
    private final ObservableList<Official.SportsTraining> trainingList = FXCollections.observableArrayList();
    private final ObservableList<Official.TrackRecord> trackList = FXCollections.observableArrayList();

    @FXML public void initialize() {
        cmbSex.getItems().addAll("Male", "Female");
        cmbEduLevel.getItems().addAll("College", "Post Graduate");

        dpDateOfBirth.valueProperty().addListener((obs, old, date) -> {
            if (date == null || date.isAfter(LocalDate.now())) txtAge.clear();
            else txtAge.setText(String.valueOf(AgeUtil.calculate(date)));
        });

        colEduLevel.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().level));
        colEduCourse.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().course));
        colEduSchool.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().school));
        colEduYear.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().yearGraduated));
        colEduAwards.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().awardsReceived));
        tblEducation.setItems(educationList);

        colTrainTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().title));
        colTrainDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().dateOfTraining));
        colTrainHours.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().numberOfHours)));
        colTrainBy.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().conductedBy));
        tblTraining.setItems(trainingList);

        colTrackMeet.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().athleteMeetAttended));
        colTrackDates.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().inclusiveDates));
        colTrackEvent.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().event));
        colTrackAwards.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().awardsReceived));
        tblTrack.setItems(trackList);
    }

    public void setCoach(Coach c) { this.coach = c; populateForm(c); }
    public void setOnSave(Runnable cb) { this.onSave = cb; }

    private void populateForm(Coach c) {
        lblGeneratedId.setText(c.getCoachId() != null ? "ID: " + c.getCoachId() : "ID: (auto-generated)");
        setText(txtLastName, c.getLastName()); setText(txtFirstName, c.getFirstName());
        setText(txtMiddleInitial, c.getMiddleInitial()); cmbSex.setValue(c.getSex());
        setText(txtMobile, c.getMobilePhone()); dpDateOfBirth.setValue(c.getDateOfBirth());
        txtAge.setText(c.getAge() > 0 ? String.valueOf(c.getAge()) : "");
        setText(txtPlaceOfBirth, c.getPlaceOfBirth()); setText(txtPosition, c.getCurrentPosition());
        txtYearsService.setText(c.getYearsInService() > 0 ? String.valueOf(c.getYearsInService()) : "");
        setText(txtSchool, c.getSchool()); setText(txtEmployeeNo, c.getEmployeeNumber());
        setText(txaSchoolAddress, c.getSchoolAddress()); setText(txaPresentAddress, c.getPresentAddress());
        setText(txtEmergencyName, c.getEmergencyContactName()); setText(txtEmergencyNo, c.getEmergencyContactNo());
        if (c.getPhotoPath() != null && !c.getPhotoPath().isBlank()) {
            selectedPhotoPath = c.getPhotoPath();
            lblPhotoPath.setText(SharedAssetService.displayName(selectedPhotoPath));
            try { imgPhoto.setImage(new Image(SharedAssetService.imageUrl(selectedPhotoPath))); } catch (Exception ignored) {}
        }
        educationList.setAll(c.getEducationalQualifications());
        trainingList.setAll(c.getSportsTrainings());
        trackList.setAll(c.getTrackRecords());
    }

    @FXML private void onChoosePhoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select 1x1 Photo");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png","*.jpg","*.jpeg"));
        File chosen = fc.showOpenDialog(txtLastName.getScene().getWindow());
        if (chosen != null) {
            try {
                selectedPhotoPath = SharedAssetService.importPhoto(chosen.toPath());
                lblPhotoPath.setText(chosen.getName());
                imgPhoto.setImage(new Image(SharedAssetService.imageUrl(selectedPhotoPath)));
            } catch (Exception e) { showError("Could not copy photo: " + e.getMessage()); }
        }
    }

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
        var sel = tblEducation.getSelectionModel().getSelectedItem();
        if (sel != null) educationList.remove(sel);
    }

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
        var sel = tblTraining.getSelectionModel().getSelectedItem();
        if (sel != null) trainingList.remove(sel);
    }

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
        var sel = tblTrack.getSelectionModel().getSelectedItem();
        if (sel != null) trackList.remove(sel);
    }

    @FXML private void onSave() {
        if (!validateForm()) return;
        collectFormData();
        try {
            DatabaseService.getInstance().saveCoach(coach);
            QRCodeService.refreshCoachQRCode(coach);
            lblGeneratedId.setText("ID: " + coach.getCoachId());
            if (onSave != null) onSave.run();
            close();
        } catch (Exception e) { showError("Save failed: " + e.getMessage()); }
    }

    @FXML private void onCancel() { close(); }

    private void collectFormData() {
        coach.setLastName(txtLastName.getText().trim()); coach.setFirstName(txtFirstName.getText().trim());
        coach.setMiddleInitial(txtMiddleInitial.getText().trim()); coach.setSex(cmbSex.getValue());
        coach.setMobilePhone(txtMobile.getText().trim()); coach.setDateOfBirth(dpDateOfBirth.getValue());
        coach.setAge(dpDateOfBirth.getValue() != null ? AgeUtil.calculate(dpDateOfBirth.getValue()) : 0);
        coach.setPlaceOfBirth(txtPlaceOfBirth.getText().trim()); coach.setCurrentPosition(txtPosition.getText().trim());
        try { coach.setYearsInService(Integer.parseInt(txtYearsService.getText().trim())); } catch (NumberFormatException ignored) {}
        coach.setSchool(txtSchool.getText().trim()); coach.setEmployeeNumber(txtEmployeeNo.getText().trim());
        coach.setSchoolAddress(txaSchoolAddress.getText().trim()); coach.setPresentAddress(txaPresentAddress.getText().trim());
        coach.setEmergencyContactName(txtEmergencyName.getText().trim()); coach.setEmergencyContactNo(txtEmergencyNo.getText().trim());
        coach.setPhotoPath(selectedPhotoPath);
        coach.setEducationalQualifications(new ArrayList<>(educationList));
        coach.setSportsTrainings(new ArrayList<>(trainingList));
        coach.setTrackRecords(new ArrayList<>(trackList));
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
