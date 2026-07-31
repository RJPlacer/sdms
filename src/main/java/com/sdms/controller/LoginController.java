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
    @FXML private PasswordField txtPassword;
    @FXML private Label lblError;
    @FXML private Label lblVersion;
    @FXML private Button btnLogin;

    @FXML public void initialize() {
        lblError.setVisible(false);
        if (lblVersion != null) {
            lblVersion.setText("Version " + AppInfo.version());
        }
        // Allow Enter key to trigger login
        txtPassword.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) onLogin(); });
        txtUsername.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) txtPassword.requestFocus(); });
    }

    @FXML private void onLogin() {
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
