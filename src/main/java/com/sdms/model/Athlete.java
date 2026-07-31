package com.sdms.model;

import javafx.beans.property.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Athlete model — mirrors the Palarong Pambansa delegation form.
 */
public class Athlete {

    // ── A. Personal Data ────────────────────────────────────────────────────
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty athleteId = new SimpleStringProperty();   // auto-generated e.g. ATH-2024-0001
    private final StringProperty lastName = new SimpleStringProperty();
    private final StringProperty firstName = new SimpleStringProperty();
    private final StringProperty middleInitial = new SimpleStringProperty();
    private final StringProperty sex = new SimpleStringProperty();         // "Male" | "Female"
    private final StringProperty learnerRefNumber = new SimpleStringProperty();
    private final StringProperty contactNo = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> dateOfBirth = new SimpleObjectProperty<>();
    private final IntegerProperty age = new SimpleIntegerProperty();
    private final StringProperty placeOfBirth = new SimpleStringProperty();
    private final StringProperty school = new SimpleStringProperty();
    private final StringProperty schoolAddress = new SimpleStringProperty();
    private final StringProperty presentAddress = new SimpleStringProperty();
    private final StringProperty photoPath = new SimpleStringProperty();   // path to 1x1 photo

    // ── Guardian Information ─────────────────────────────────────────────────
    private final StringProperty motherName = new SimpleStringProperty();
    private final StringProperty fatherName = new SimpleStringProperty();
    private final StringProperty guardianAddress = new SimpleStringProperty();

    // ── Generated ────────────────────────────────────────────────────────────
    private final StringProperty qrCodePath = new SimpleStringProperty();  // path to QR image

    // ── B. Previous Palarong Pambansa participations ─────────────────────────
    private List<PalaroPrevious> previousPalaroRecords = new ArrayList<>();

    // ── C. Lower meets (current school year) ─────────────────────────────────
    private List<LowerMeet> lowerMeetRecords = new ArrayList<>();

    // ── D. Certification ─────────────────────────────────────────────────────
    private final StringProperty certMeet = new SimpleStringProperty();
    private final StringProperty certCoachName = new SimpleStringProperty();
    private final StringProperty certDsoName = new SimpleStringProperty();
    private final StringProperty certRsoName = new SimpleStringProperty();

    // ─────────────────────────────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────────────────────────────

    public Athlete() {}

    public Athlete(int id, String athleteId, String lastName, String firstName,
                   String middleInitial, String sex, String learnerRefNumber,
                   String contactNo, LocalDate dateOfBirth, int age,
                   String placeOfBirth, String school, String schoolAddress,
                   String presentAddress, String photoPath,
                   String motherName, String fatherName, String guardianAddress) {
        setId(id);
        setAthleteId(athleteId);
        setLastName(lastName);
        setFirstName(firstName);
        setMiddleInitial(middleInitial);
        setSex(sex);
        setLearnerRefNumber(learnerRefNumber);
        setContactNo(contactNo);
        setDateOfBirth(dateOfBirth);
        setAge(age);
        setPlaceOfBirth(placeOfBirth);
        setSchool(school);
        setSchoolAddress(schoolAddress);
        setPresentAddress(presentAddress);
        setPhotoPath(photoPath);
        setMotherName(motherName);
        setFatherName(fatherName);
        setGuardianAddress(guardianAddress);
    }

    // ── Helper: full name ─────────────────────────────────────────────────────
    public String getFullName() {
        String mi = (getMiddleInitial() != null && !getMiddleInitial().isBlank())
                ? " " + getMiddleInitial().trim() + "." : "";
        return getLastName() + ", " + getFirstName() + mi;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Properties + getters/setters (generated)
    // ─────────────────────────────────────────────────────────────────────────

    public int getId() { return id.get(); }
    public IntegerProperty idProperty() { return id; }
    public void setId(int v) { id.set(v); }

    public String getAthleteId() { return athleteId.get(); }
    public StringProperty athleteIdProperty() { return athleteId; }
    public void setAthleteId(String v) { athleteId.set(v); }

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

    public String getLearnerRefNumber() { return learnerRefNumber.get(); }
    public StringProperty learnerRefNumberProperty() { return learnerRefNumber; }
    public void setLearnerRefNumber(String v) { learnerRefNumber.set(v); }

    public String getContactNo() { return contactNo.get(); }
    public StringProperty contactNoProperty() { return contactNo; }
    public void setContactNo(String v) { contactNo.set(v); }

    public LocalDate getDateOfBirth() { return dateOfBirth.get(); }
    public ObjectProperty<LocalDate> dateOfBirthProperty() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate v) { dateOfBirth.set(v); }

    public int getAge() { return age.get(); }
    public IntegerProperty ageProperty() { return age; }
    public void setAge(int v) { age.set(v); }

    public String getPlaceOfBirth() { return placeOfBirth.get(); }
    public StringProperty placeOfBirthProperty() { return placeOfBirth; }
    public void setPlaceOfBirth(String v) { placeOfBirth.set(v); }

    public String getSchool() { return school.get(); }
    public StringProperty schoolProperty() { return school; }
    public void setSchool(String v) { school.set(v); }

    public String getSchoolAddress() { return schoolAddress.get(); }
    public StringProperty schoolAddressProperty() { return schoolAddress; }
    public void setSchoolAddress(String v) { schoolAddress.set(v); }

    public String getPresentAddress() { return presentAddress.get(); }
    public StringProperty presentAddressProperty() { return presentAddress; }
    public void setPresentAddress(String v) { presentAddress.set(v); }

    public String getPhotoPath() { return photoPath.get(); }
    public StringProperty photoPathProperty() { return photoPath; }
    public void setPhotoPath(String v) { photoPath.set(v); }

    public String getMotherName() { return motherName.get(); }
    public StringProperty motherNameProperty() { return motherName; }
    public void setMotherName(String v) { motherName.set(v); }

    public String getFatherName() { return fatherName.get(); }
    public StringProperty fatherNameProperty() { return fatherName; }
    public void setFatherName(String v) { fatherName.set(v); }

    public String getGuardianAddress() { return guardianAddress.get(); }
    public StringProperty guardianAddressProperty() { return guardianAddress; }
    public void setGuardianAddress(String v) { guardianAddress.set(v); }

    public String getQrCodePath() { return qrCodePath.get(); }
    public StringProperty qrCodePathProperty() { return qrCodePath; }
    public void setQrCodePath(String v) { qrCodePath.set(v); }

    public String getCertMeet() { return certMeet.get(); }
    public StringProperty certMeetProperty() { return certMeet; }
    public void setCertMeet(String v) { certMeet.set(v); }

    public String getCertCoachName() { return certCoachName.get(); }
    public StringProperty certCoachNameProperty() { return certCoachName; }
    public void setCertCoachName(String v) { certCoachName.set(v); }

    public String getCertDsoName() { return certDsoName.get(); }
    public StringProperty certDsoNameProperty() { return certDsoName; }
    public void setCertDsoName(String v) { certDsoName.set(v); }

    public String getCertRsoName() { return certRsoName.get(); }
    public StringProperty certRsoNameProperty() { return certRsoName; }
    public void setCertRsoName(String v) { certRsoName.set(v); }

    public List<PalaroPrevious> getPreviousPalaroRecords() { return previousPalaroRecords; }
    public void setPreviousPalaroRecords(List<PalaroPrevious> v) { previousPalaroRecords = v; }

    public List<LowerMeet> getLowerMeetRecords() { return lowerMeetRecords; }
    public void setLowerMeetRecords(List<LowerMeet> v) { lowerMeetRecords = v; }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner record classes (stored as child rows in the DB)
    // ─────────────────────────────────────────────────────────────────────────

    public static class PalaroPrevious {
        public int id;
        public int athleteId;
        public String year;
        public String sportsEvent;
        public String venue;
        public String remarks;
    }

    public static class LowerMeet {
        public int id;
        public int athleteId;
        public String inclusiveDates;
        public String sportsEvent;
        public String athleticMeet;
        public String remarks;
    }
}
