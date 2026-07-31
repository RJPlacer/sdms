package com.sdms.controller;

import com.sdms.model.User;
import com.sdms.service.DatabaseService;
import com.sdms.util.PasswordUtil;
import com.sdms.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.Optional;

public class UserManagementController {

    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> colUsername, colFullName, colRole, colActive;
    @FXML private TextField txtUsername, txtFullName, txtNewPassword;
    @FXML private ComboBox<String> cmbRole;
    @FXML private CheckBox chkActive;
    @FXML private Label lblFormTitle;
    @FXML private Button btnSave, btnDelete;

    private final ObservableList<User> users = FXCollections.observableArrayList();
    private User selectedUser = null;

    @FXML public void initialize() {
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colActive.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue().isActive() ? "Active" : "Inactive"));

        cmbRole.getItems().addAll("ADMIN", "ENCODER", "VIEWER");
        userTable.setItems(users);
        userTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) populateForm(sel);
        });

        boolean admin = SessionManager.isAdmin();
        btnSave.setDisable(!admin);
        btnDelete.setDisable(!admin);
        loadUsers();
        onNew();
    }

    private void loadUsers() {
        try { users.setAll(DatabaseService.getInstance().getAllUsers()); }
        catch (SQLException e) { showError("Failed to load users: " + e.getMessage()); }
    }

    private void populateForm(User u) {
        selectedUser = u;
        lblFormTitle.setText("Edit User");
        txtUsername.setText(u.getUsername());
        txtFullName.setText(u.getFullName());
        cmbRole.setValue(u.getRole());
        chkActive.setSelected(u.isActive());
        txtNewPassword.clear();
        txtNewPassword.setPromptText("Leave blank to keep current password");
    }

    @FXML private void onNew() {
        selectedUser = null;
        lblFormTitle.setText("New User");
        txtUsername.clear(); txtFullName.clear(); txtNewPassword.clear();
        cmbRole.setValue("ENCODER"); chkActive.setSelected(true);
        txtNewPassword.setPromptText("Enter password");
        userTable.getSelectionModel().clearSelection();
    }

    @FXML private void onSave() {
        if (!SessionManager.isAdmin()) { showError("Administrator permission is required."); return; }
        if (txtUsername.getText().isBlank()) { showError("Username is required."); return; }
        if (txtFullName.getText().isBlank())  { showError("Full name is required."); return; }
        if (cmbRole.getValue() == null)       { showError("Role is required."); return; }
        if (selectedUser == null && txtNewPassword.getText().isBlank()) {
            showError("Password is required for new users."); return;
        }
        if (!txtNewPassword.getText().isBlank() && txtNewPassword.getText().length() < 8) {
            showError("Password must be at least 8 characters."); return;
        }

        boolean activeAdminAfterSave = "ADMIN".equals(cmbRole.getValue()) && chkActive.isSelected();
        if (selectedUser != null && selectedUser.getId() == SessionManager.getCurrentUser().getId()
                && !activeAdminAfterSave) {
            showError("You cannot demote or deactivate your own administrator account."); return;
        }

        try {
            if (selectedUser == null) {
                User u = new User();
                u.setUsername(txtUsername.getText().trim());
                u.setFullName(txtFullName.getText().trim());
                u.setRole(cmbRole.getValue());
                u.setActive(chkActive.isSelected());
                u.setPasswordHash(PasswordUtil.hash(txtNewPassword.getText()));
                u.setMustChangePassword(true);
                DatabaseService.getInstance().saveUser(u);
            } else {
                if (selectedUser.isAdmin() && selectedUser.isActive() && !activeAdminAfterSave
                        && DatabaseService.getInstance().countActiveAdmins() <= 1) {
                    showError("At least one active administrator account is required."); return;
                }
                selectedUser.setUsername(txtUsername.getText().trim());
                selectedUser.setFullName(txtFullName.getText().trim());
                selectedUser.setRole(cmbRole.getValue());
                selectedUser.setActive(chkActive.isSelected());
                if (!txtNewPassword.getText().isBlank()) {
                    selectedUser.setPasswordHash(PasswordUtil.hash(txtNewPassword.getText()));
                    selectedUser.setMustChangePassword(true);
                }
                DatabaseService.getInstance().saveUser(selectedUser);
            }
            loadUsers();
            onNew();
        } catch (Exception e) { showError("Save failed: " + e.getMessage()); }
    }

    @FXML private void onDelete() {
        if (!SessionManager.isAdmin()) { showError("Administrator permission is required."); return; }
        User sel = userTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Select a user to delete."); return; }
        if (sel.getUsername().equals(SessionManager.getCurrentUser().getUsername())) {
            showError("You cannot delete your own account."); return;
        }
        try {
            if (sel.isAdmin() && sel.isActive()
                    && DatabaseService.getInstance().countActiveAdmins() <= 1) {
                showError("The last active administrator cannot be deleted."); return;
            }
        } catch (SQLException e) {
            showError("Could not verify administrator accounts: " + e.getMessage()); return;
        }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete user '" + sel.getUsername() + "'?", ButtonType.YES, ButtonType.CANCEL);
        Optional<ButtonType> r = c.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.YES) {
            try { DatabaseService.getInstance().deleteUser(sel.getId()); loadUsers(); onNew(); }
            catch (SQLException e) { showError("Delete failed: " + e.getMessage()); }
        }
    }

    @FXML private void onClose() { ((Stage) userTable.getScene().getWindow()).close(); }

    private void showError(String msg) { new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait(); }
}
