package com.sdms.model;

import javafx.beans.property.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Coach model — identical field structure to Official.
 * Auto-generated ID prefix: CCH-YYYY-NNNN
 */
public class Coach {

    // ── A. Personal Data ────────────────────────────────────────────────────
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty coachId = new SimpleStringProperty();
    private final StringProperty lastName = new SimpleStringProperty();
    private final StringProperty firstName = new SimpleStringProperty();
    private final StringProperty middleInitial = new SimpleStringProperty();
    private final StringProperty sex = new SimpleStringProperty();
    private final StringProperty mobilePhone = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> dateOfBirth = new SimpleObjectProperty<>();
    private final IntegerProperty age = new SimpleIntegerProperty();
    private final StringProperty placeOfBirth = new SimpleStringProperty();
    private final StringProperty currentPosition = new SimpleStringProperty();
    private final IntegerProperty yearsInService = new SimpleIntegerProperty();
    private final StringProperty school = new SimpleStringProperty();
    private final StringProperty employeeNumber = new SimpleStringProperty();
    private final StringProperty schoolAddress = new SimpleStringProperty();
    private final StringProperty presentAddress = new SimpleStringProperty();
    private final StringProperty emergencyContactName = new SimpleStringProperty();
    private final StringProperty emergencyContactNo = new SimpleStringProperty();
    private final StringProperty photoPath = new SimpleStringProperty();
    private final StringProperty qrCodePath = new SimpleStringProperty();

    // ── B–D. (same child-row structure as Official) ───────────────────────
    private List<Official.EducationalQualification> educationalQualifications = new ArrayList<>();
    private List<Official.SportsTraining> sportsTrainings = new ArrayList<>();
    private List<Official.TrackRecord> trackRecords = new ArrayList<>();

    public Coach() {}

    public String getFullName() {
        String mi = (getMiddleInitial() != null && !getMiddleInitial().isBlank())
                ? " " + getMiddleInitial().trim() + "." : "";
        return getLastName() + ", " + getFirstName() + mi;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Properties + getters/setters
    // ─────────────────────────────────────────────────────────────────────────

    public int getId() { return id.get(); }
    public IntegerProperty idProperty() { return id; }
    public void setId(int v) { id.set(v); }

    public String getCoachId() { return coachId.get(); }
    public StringProperty coachIdProperty() { return coachId; }
    public void setCoachId(String v) { coachId.set(v); }

    public String getLastName() { return lastName.get(); }
    public StringProperty lastNameProperty() { return lastName; }
    public void setLastName(String v) { lastName.set(v); }

    public String getFirstName() { return firstName.get(); }
    public StringProperty firstNameProperty() { return firstName; }
    public void setFirstName(String v) { firstName.set(v); }

    public String getMiddleInitial() { return middleInitial.get(); }
    public StringProperty middleInitialProperty() { return middleInitial; }
    public void setMiddleInitial(String v) { middleInitial.set(v); }

    public String getSex() { return sex.get(); }
    public StringProperty sexProperty() { return sex; }
    public void setSex(String v) { sex.set(v); }

    public String getMobilePhone() { return mobilePhone.get(); }
    public StringProperty mobilePhoneProperty() { return mobilePhone; }
    public void setMobilePhone(String v) { mobilePhone.set(v); }

    public LocalDate getDateOfBirth() { return dateOfBirth.get(); }
    public ObjectProperty<LocalDate> dateOfBirthProperty() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate v) { dateOfBirth.set(v); }

    public int getAge() { return age.get(); }
    public IntegerProperty ageProperty() { return age; }
    public void setAge(int v) { age.set(v); }

    public String getPlaceOfBirth() { return placeOfBirth.get(); }
    public StringProperty placeOfBirthProperty() { return placeOfBirth; }
    public void setPlaceOfBirth(String v) { placeOfBirth.set(v); }

    public String getCurrentPosition() { return currentPosition.get(); }
    public StringProperty currentPositionProperty() { return currentPosition; }
    public void setCurrentPosition(String v) { currentPosition.set(v); }

    public int getYearsInService() { return yearsInService.get(); }
    public IntegerProperty yearsInServiceProperty() { return yearsInService; }
    public void setYearsInService(int v) { yearsInService.set(v); }

    public String getSchool() { return school.get(); }
    public StringProperty schoolProperty() { return school; }
    public void setSchool(String v) { school.set(v); }

    public String getEmployeeNumber() { return employeeNumber.get(); }
    public StringProperty employeeNumberProperty() { return employeeNumber; }
    public void setEmployeeNumber(String v) { employeeNumber.set(v); }

    public String getSchoolAddress() { return schoolAddress.get(); }
    public StringProperty schoolAddressProperty() { return schoolAddress; }
    public void setSchoolAddress(String v) { schoolAddress.set(v); }

    public String getPresentAddress() { return presentAddress.get(); }
    public StringProperty presentAddressProperty() { return presentAddress; }
    public void setPresentAddress(String v) { presentAddress.set(v); }

    public String getEmergencyContactName() { return emergencyContactName.get(); }
    public StringProperty emergencyContactNameProperty() { return emergencyContactName; }
    public void setEmergencyContactName(String v) { emergencyContactName.set(v); }

    public String getEmergencyContactNo() { return emergencyContactNo.get(); }
    public StringProperty emergencyContactNoProperty() { return emergencyContactNo; }
    public void setEmergencyContactNo(String v) { emergencyContactNo.set(v); }

    public String getPhotoPath() { return photoPath.get(); }
    public StringProperty photoPathProperty() { return photoPath; }
    public void setPhotoPath(String v) { photoPath.set(v); }

    public String getQrCodePath() { return qrCodePath.get(); }
    public StringProperty qrCodePathProperty() { return qrCodePath; }
    public void setQrCodePath(String v) { qrCodePath.set(v); }

    public List<Official.EducationalQualification> getEducationalQualifications() { return educationalQualifications; }
    public void setEducationalQualifications(List<Official.EducationalQualification> v) { educationalQualifications = v; }

    public List<Official.SportsTraining> getSportsTrainings() { return sportsTrainings; }
    public void setSportsTrainings(List<Official.SportsTraining> v) { sportsTrainings = v; }

    public List<Official.TrackRecord> getTrackRecords() { return trackRecords; }
    public void setTrackRecords(List<Official.TrackRecord> v) { trackRecords = v; }
}
