package com.sdms.model;

import javafx.beans.property.*;

public class User {
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty username = new SimpleStringProperty();
    private final StringProperty passwordHash = new SimpleStringProperty();
    private final StringProperty role = new SimpleStringProperty(); // ADMIN, ENCODER, VIEWER
    private final StringProperty fullName = new SimpleStringProperty();
    private final BooleanProperty active = new SimpleBooleanProperty(true);
    private final BooleanProperty mustChangePassword = new SimpleBooleanProperty(false);

    public User() {}
    public User(int id, String username, String passwordHash, String role, String fullName, boolean active) {
        setId(id); setUsername(username); setPasswordHash(passwordHash);
        setRole(role); setFullName(fullName); setActive(active);
    }

    public int getId() { return id.get(); }
    public IntegerProperty idProperty() { return id; }
    public void setId(int v) { id.set(v); }

    public String getUsername() { return username.get(); }
    public StringProperty usernameProperty() { return username; }
    public void setUsername(String v) { username.set(v); }

    public String getPasswordHash() { return passwordHash.get(); }
    public StringProperty passwordHashProperty() { return passwordHash; }
    public void setPasswordHash(String v) { passwordHash.set(v); }

    public String getRole() { return role.get(); }
    public StringProperty roleProperty() { return role; }
    public void setRole(String v) { role.set(v); }

    public String getFullName() { return fullName.get(); }
    public StringProperty fullNameProperty() { return fullName; }
    public void setFullName(String v) { fullName.set(v); }

    public boolean isActive() { return active.get(); }
    public BooleanProperty activeProperty() { return active; }
    public void setActive(boolean v) { active.set(v); }

    public boolean isMustChangePassword() { return mustChangePassword.get(); }
    public BooleanProperty mustChangePasswordProperty() { return mustChangePassword; }
    public void setMustChangePassword(boolean v) { mustChangePassword.set(v); }

    public boolean isAdmin()   { return "ADMIN".equals(getRole()); }
    public boolean isEncoder() { return "ENCODER".equals(getRole()); }
    public boolean isViewer()  { return "VIEWER".equals(getRole()); }
    public boolean canEdit()   { return isAdmin() || isEncoder(); }
    public boolean canDelete() { return isAdmin(); }
}
