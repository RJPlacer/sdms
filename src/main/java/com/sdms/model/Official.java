package com.sdms.model;

import javafx.beans.property.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Official model — personal data, educational qualifications,
 * sports training, and sports track record.
 */
public class Official {

    // ── A. Personal Data ────────────────────────────────────────────────────
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty officialId = new SimpleStringProperty();  // auto-generated OFC-2024-0001
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

    // ── B. Educational Qualifications ────────────────────────────────────────
    private List<EducationalQualification> educationalQualifications = new ArrayList<>();

    // ── C. Sports Training (last 3 years) ────────────────────────────────────
    private List<SportsTraining> sportsTrainings = new ArrayList<>();

    // ── D. Sports Track Record / Experience ──────────────────────────────────
    private List<TrackRecord> trackRecords = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────────────────────────────

    public Official() {}

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

    public String getOfficialId() { return officialId.get(); }
    public StringProperty officialIdProperty() { return officialId; }
    public void setOfficialId(String v) { officialId.set(v); }

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

    public List<EducationalQualification> getEducationalQualifications() { return educationalQualifications; }
    public void setEducationalQualifications(List<EducationalQualification> v) { educationalQualifications = v; }

    public List<SportsTraining> getSportsTrainings() { return sportsTrainings; }
    public void setSportsTrainings(List<SportsTraining> v) { sportsTrainings = v; }

    public List<TrackRecord> getTrackRecords() { return trackRecords; }
    public void setTrackRecords(List<TrackRecord> v) { trackRecords = v; }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared inner record types (also used by Coach)
    // ─────────────────────────────────────────────────────────────────────────

    public static class EducationalQualification {
        public int id;
        public String level;          // "College" | "Post Graduate"
        public String course;
        public String school;
        public String yearGraduated;
        public String creditsEarned;
        public String awardsReceived;
    }

    public static class SportsTraining {
        public int id;
        public String title;
        public String dateOfTraining;
        public int numberOfHours;
        public String conductedBy;
    }

    public static class TrackRecord {
        public int id;
        public String athleteMeetAttended;
        public String inclusiveDates;
        public String event;
        public String awardsReceived;
    }
}
