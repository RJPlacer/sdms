package com.sdms.controller;

import com.sdms.util.SessionManager;
import com.sdms.util.AppInfo;
import com.sdms.sharing.SharingManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class MainController {

    @FXML private StackPane contentArea;
    @FXML private Button btnDashboard, btnAthletes, btnOfficials, btnCoaches, btnEquipment;
    @FXML private Label lblCurrentUser, lblUserRole, lblConnectionStatus;
    @FXML private Button btnUsers;

    private List<Button> navButtons;

    @FXML public void initialize() {
        navButtons = List.of(btnDashboard, btnAthletes, btnOfficials, btnCoaches, btnEquipment);
        if (SessionManager.isLoggedIn()) {
            lblCurrentUser.setText(SessionManager.getCurrentUser().getFullName());
            lblUserRole.setText(SessionManager.getCurrentUser().getRole());
        }
        btnUsers.setVisible(SessionManager.isAdmin());
        btnUsers.setManaged(SessionManager.isAdmin());
        lblConnectionStatus.setText(SharingManager.getInstance().statusText());
        onNavDashboard();
    }

    @FXML private void onNavDashboard() { loadView("/fxml/Dashboard.fxml",     btnDashboard); }
    @FXML private void onNavAthletes()  { loadView("/fxml/AthleteView.fxml",   btnAthletes); }
    @FXML private void onNavOfficials() { loadView("/fxml/OfficialView.fxml",  btnOfficials); }
    @FXML private void onNavCoaches()   { loadView("/fxml/CoachView.fxml",     btnCoaches); }
    @FXML private void onNavEquipment() { loadView("/fxml/EquipmentView.fxml", btnEquipment); }

    @FXML private void onGlobalSearch()    { openDialog("/fxml/GlobalSearch.fxml",    "Global Search",         800, 500); }
    @FXML private void onBatchPrint()      { openDialog("/fxml/BatchPrint.fxml",      "Batch ID Card Export",  500, 420); }
    @FXML private void onScanQr()          { openDialog("/fxml/QRScan.fxml",          "Scan QR",               900, 720); }
    @FXML private void onUserManagement()  {
        if (!SessionManager.isAdmin()) {
            new Alert(Alert.AlertType.ERROR, "Administrator permission is required.").showAndWait();
            return;
        }
        openDialog("/fxml/UserManagement.fxml", "User Management", 860, 500);
    }
    @FXML private void onRoster()          { openDialog("/fxml/Roster.fxml",          "Delegation Roster",     480, 400); }
    @FXML private void onChangePassword()  { openDialog("/fxml/ChangePassword.fxml",  "Change Password",       420, 360); }
    @FXML private void onAbout()           { openDialog("/fxml/About.fxml",           "About Athlete Track",   560, 580); }
    @FXML private void onSharing() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SharingDialog.fxml"));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initOwner(contentArea.getScene().getWindow());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Multi-PC Sharing");
            Scene scene = new Scene(root, 720, 660);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
            SharingController controller = loader.getController();
            lblConnectionStatus.setText(SharingManager.getInstance().statusText());
            if (controller.isConnectionChanged()) returnToLogin();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Could not open multi-PC sharing: " + e.getMessage()).showAndWait();
        }
    }
    @FXML private void onFooterClick(MouseEvent event) {
        openDialog("/fxml/TeamDialog.fxml", "About the Developer", 760, 620);
    }

    @FXML private void onLogout() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Log out of Athlete Track?", ButtonType.YES, ButtonType.CANCEL);
        confirm.setTitle("Confirm Logout");
        if (confirm.showAndWait().filter(b -> b == ButtonType.YES).isPresent()) {
            returnToLogin();
        }
    }

    private void returnToLogin() {
        SessionManager.logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            Stage stage = (Stage) btnDashboard.getScene().getWindow();
            stage.setScene(scene);
            stage.setWidth(980); stage.setHeight(680);
            stage.setMinWidth(860); stage.setMinHeight(620);
            stage.centerOnScreen();
            stage.setTitle(AppInfo.appName() + " - Login (" + AppInfo.displayVersion() + ")");
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not return to login: " + e.getMessage()).showAndWait();
        }
    }

    private void openDialog(String fxml, String title, double w, double h) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initOwner(contentArea.getScene().getWindow());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle(title);
            Scene scene = new Scene(root, w, h);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            dialog.setScene(scene);
            dialog.show();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not open " + title + ": " + e.getMessage()).showAndWait();
        }
    }

    private void loadView(String fxmlPath, Button activeBtn) {
        try {
            Node view = FXMLLoader.load(getClass().getResource(fxmlPath));
            contentArea.getChildren().setAll(view);
            navButtons.forEach(b -> b.getStyleClass().remove("nav-btn-active"));
            activeBtn.getStyleClass().add("nav-btn-active");
        } catch (IOException e) {
            Label placeholder = new Label("Could not load view: " + fxmlPath);
            placeholder.setStyle("-fx-font-size: 14px; -fx-text-fill: #888;");
            contentArea.getChildren().setAll(placeholder);
            navButtons.forEach(b -> b.getStyleClass().remove("nav-btn-active"));
            activeBtn.getStyleClass().add("nav-btn-active");
        }
    }
}
