package com.sdms.controller;

import com.sdms.service.DatabaseService;
import com.sdms.util.PasswordUtil;
import com.sdms.util.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class ChangePasswordController {

    @FXML private Label lblUsername;
    @FXML private PasswordField txtCurrent, txtNew, txtConfirm;
    @FXML private Label lblError, lblSuccess;

    @FXML public void initialize() {
        lblUsername.setText(SessionManager.getCurrentUser().getFullName()
            + " (" + SessionManager.getCurrentUser().getUsername() + ")");
        lblError.setVisible(false);
        lblSuccess.setVisible(false);
    }

    @FXML private void onSave() {
        lblError.setVisible(false);
        lblSuccess.setVisible(false);

        String current = txtCurrent.getText();
        String newPw   = txtNew.getText();
        String confirm = txtConfirm.getText();

        if (current.isBlank() || newPw.isBlank() || confirm.isBlank()) {
            showError("All fields are required."); return;
        }
        if (!PasswordUtil.verify(current, SessionManager.getCurrentUser().getPasswordHash())) {
            showError("Current password is incorrect."); return;
        }
        if (newPw.length() < 8) {
            showError("New password must be at least 8 characters."); return;
        }
        if (!newPw.equals(confirm)) {
            showError("New passwords do not match."); return;
        }

        try {
            SessionManager.getCurrentUser().setPasswordHash(PasswordUtil.hash(newPw));
            SessionManager.getCurrentUser().setMustChangePassword(false);
            DatabaseService.getInstance().saveUser(SessionManager.getCurrentUser());
            txtCurrent.clear(); txtNew.clear(); txtConfirm.clear();
            lblSuccess.setText("Password changed successfully.");
            lblSuccess.setVisible(true);
            ((Stage) txtCurrent.getScene().getWindow()).close();
        } catch (Exception e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    @FXML private void onCancel() { ((Stage) txtCurrent.getScene().getWindow()).close(); }

    private void showError(String msg) {
        lblError.setText(msg); lblError.setVisible(true);
    }
}
