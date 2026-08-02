package com.sdms.controller;

import com.sdms.service.DatabaseService;
import com.sdms.model.User;
import com.sdms.util.AppInfo;
import com.sdms.util.SessionManager;
import com.sdms.util.PasswordUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;

public class LoginController {

    @FXML private TextField txtUsername;
    @FXML private TextField txtFullName;
    @FXML private PasswordField txtPassword;
    @FXML private PasswordField txtConfirm;
    @FXML private Label lblError, lblSetup, lblFullName, lblConfirm;
    @FXML private Label lblVersion;
    @FXML private Button btnLogin;
    private boolean firstRun;

    @FXML public void initialize() {
        lblError.setVisible(false);
        if (lblVersion != null) {
            lblVersion.setText("Version " + AppInfo.version());
        }
        try {
            firstRun = !DatabaseService.getInstance().hasUsers();
            configureMode();
        } catch (Exception e) {
            showError("Could not check the user database.");
            btnLogin.setDisable(true);
        }
        // Allow Enter key to trigger login
        txtPassword.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) onLogin(); });
        txtConfirm.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) onLogin(); });
        txtUsername.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) txtPassword.requestFocus(); });
    }

    @FXML private void onLogin() {
        if (firstRun) {
            createInitialAdministrator();
            return;
        }

        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();

        if (username.isBlank() || password.isBlank()) {
            showError("Please enter username and password.");
            return;
        }

        try {
            User user = DatabaseService.getInstance().findUserByUsername(username);
            if (user == null || !PasswordUtil.verify(password, user.getPasswordHash())) {
                showError("Invalid username or password.");
                txtPassword.clear();
                return;
            }
            if (!user.isActive()) {
                showError("This account has been deactivated.");
                return;
            }

            if (PasswordUtil.needsRehash(user.getPasswordHash())) {
                user.setPasswordHash(PasswordUtil.hash(password));
                DatabaseService.getInstance().saveUser(user);
            }

            SessionManager.login(user);
            if (user.isMustChangePassword() && !requirePasswordChange()) {
                SessionManager.logout();
                showError("You must change the temporary password before continuing.");
                return;
            }
            openMainWindow();
        } catch (Exception e) {
            showError("Login error: " + e.getMessage());
        }
    }

    private void configureMode() {
        lblSetup.setVisible(firstRun);
        lblSetup.setManaged(firstRun);
        lblFullName.setVisible(firstRun);
        lblFullName.setManaged(firstRun);
        txtFullName.setVisible(firstRun);
        txtFullName.setManaged(firstRun);
        lblConfirm.setVisible(firstRun);
        lblConfirm.setManaged(firstRun);
        txtConfirm.setVisible(firstRun);
        txtConfirm.setManaged(firstRun);
        btnLogin.setText(firstRun ? "Create Administrator" : "Log In");
        txtUsername.setPromptText(firstRun ? "Choose an administrator username" : "Enter username");
        txtPassword.setPromptText(firstRun ? "Choose a password (12+ characters)" : "Enter password");
    }

    private void createInitialAdministrator() {
        String username = txtUsername.getText().trim();
        String fullName = txtFullName.getText().trim();
        String password = txtPassword.getText();
        String confirm = txtConfirm.getText();

        if (!username.matches("[A-Za-z0-9._-]{3,32}")) {
            showError("Username must be 3-32 letters, numbers, dots, underscores, or hyphens.");
            return;
        }
        if (fullName.isBlank()) {
            showError("Please enter the administrator's full name.");
            return;
        }
        if (password.length() < 12) {
            showError("Administrator password must be at least 12 characters.");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }

        try {
            if (DatabaseService.getInstance().hasUsers()) {
                firstRun = false;
                configureMode();
                showError("Setup is already complete. Please log in.");
                return;
            }
            User admin = new User();
            admin.setUsername(username);
            admin.setFullName(fullName);
            admin.setPasswordHash(PasswordUtil.hash(password));
            admin.setRole("ADMIN");
            admin.setActive(true);
            admin.setMustChangePassword(false);
            DatabaseService.getInstance().saveUser(admin);
            SessionManager.login(admin);
            openMainWindow();
        } catch (Exception e) {
            showError("Could not create the administrator account.");
        }
    }

    private boolean requirePasswordChange() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChangePassword.fxml"));
        Parent root = loader.load();
        Stage dialog = new Stage();
        dialog.initOwner(btnLogin.getScene().getWindow());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Password Change Required");
        Scene scene = new Scene(root, 420, 360);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
        return !SessionManager.getCurrentUser().isMustChangePassword();
    }

    private void openMainWindow() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainLayout.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        Stage stage = (Stage) btnLogin.getScene().getWindow();
        stage.setScene(scene);
        stage.setTitle("Athlete Track — Sports Delegation Management (" + AppInfo.displayVersion() + ")");
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
    }

    @FXML private void onFooterClick(MouseEvent event) {
        if (event.getTarget() instanceof Hyperlink) return;
        openTeamDialog();
    }

    @FXML private void onOpenDepTrack() {
        try {
            Desktop.getDesktop().browse(URI.create("https://depedsdocityofbaliwagdeptrack.com/pages/landingPage.php"));
        } catch (Exception e) {
            showError("Could not open DepTrack site: " + e.getMessage());
        }
    }

    private void openTeamDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TeamDialog.fxml"));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Alfa Dev Team");
            Scene scene = new Scene(root, 1320, 760);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            dialog.setScene(scene);
            dialog.show();
        } catch (Exception e) {
            showError("Could not open team dialog: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
    }
}
