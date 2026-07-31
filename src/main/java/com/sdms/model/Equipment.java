package com.sdms.model;

import javafx.beans.property.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Equipment borrowing record.
 */
public class Equipment {

    // ── Borrower Information ─────────────────────────────────────────────────
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty equipmentId = new SimpleStringProperty();  // auto-generated EQP-2024-0001
    private final StringProperty borrowerName = new SimpleStringProperty();
    private final StringProperty designation = new SimpleStringProperty();
    private final StringProperty school = new SimpleStringProperty();
    private final StringProperty event = new SimpleStringProperty();
    private final StringProperty mobileNo = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> date = new SimpleObjectProperty<>();

    // ── Insurance ────────────────────────────────────────────────────────────
    private final StringProperty issuedBy = new SimpleStringProperty();
    private final StringProperty issuedTo = new SimpleStringProperty();

    // ── Equipment Items (child rows) ──────────────────────────────────────────
    private List<EquipmentItem> items = new ArrayList<>();

    public Equipment() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Properties + getters/setters
    // ─────────────────────────────────────────────────────────────────────────

    public int getId() { return id.get(); }
    public IntegerProperty idProperty() { return id; }
    public void setId(int v) { id.set(v); }

    public String getEquipmentId() { return equipmentId.get(); }
    public StringProperty equipmentIdProperty() { return equipmentId; }
    public void setEquipmentId(String v) { equipmentId.set(v); }

    public String getBorrowerName() { return borrowerName.get(); }
    public StringProperty borrowerNameProperty() { return borrowerName; }
    public void setBorrowerName(String v) { borrowerName.set(v); }

    public String getDesignation() { return designation.get(); }
    public StringProperty designationProperty() { return designation; }
    public void setDesignation(String v) { designation.set(v); }

    public String getSchool() { return school.get(); }
    public StringProperty schoolProperty() { return school; }
    public void setSchool(String v) { school.set(v); }

    public String getEvent() { return event.get(); }
    public StringProperty eventProperty() { return event; }
    public void setEvent(String v) { event.set(v); }

    public String getMobileNo() { return mobileNo.get(); }
    public StringProperty mobileNoProperty() { return mobileNo; }
    public void setMobileNo(String v) { mobileNo.set(v); }

    public LocalDate getDate() { return date.get(); }
    public ObjectProperty<LocalDate> dateProperty() { return date; }
    public void setDate(LocalDate v) { date.set(v); }

    public String getIssuedBy() { return issuedBy.get(); }
    public StringProperty issuedByProperty() { return issuedBy; }
    public void setIssuedBy(String v) { issuedBy.set(v); }

    public String getIssuedTo() { return issuedTo.get(); }
    public StringProperty issuedToProperty() { return issuedTo; }
    public void setIssuedTo(String v) { issuedTo.set(v); }

    public List<EquipmentItem> getItems() { return items; }
    public void setItems(List<EquipmentItem> v) { items = v; }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner: individual equipment items in the borrowing record
    // ─────────────────────────────────────────────────────────────────────────

    public static class EquipmentItem {
        public int id;
        public int equipmentId;
        public int qty;
        public String unit;
        public String makeAndDescription;
        public LocalDate dateBorrowed;
        public LocalDate dateReturned;
        public String remarks;
    }
}
