package com.sdms.controller;

import com.sdms.service.BackupService;
import com.sdms.service.DatabaseService;
import com.sdms.sharing.HostOffer;
import com.sdms.sharing.NetworkDiscoveryService;
import com.sdms.sharing.SharingConfig;
import com.sdms.sharing.SharingManager;
import com.sdms.util.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

public class SharingController {

    @FXML private Label lblMode, lblStatus, lblHostAddress, lblPairingCode, lblMessage;
    @FXML private TextField txtServerName, txtHost, txtPairingCode;
    @FXML private ComboBox<HostOffer> cmbHosts;
    @FXML private Button btnStartHost, btnConnect, btnUseLocal, btnDiscover, btnBackup, btnRestore;
    @FXML private ProgressIndicator progress;

    private boolean connectionChanged;

    @FXML public void initialize() {
        lblMessage.setVisible(false);
        lblMessage.setManaged(false);
        progress.setVisible(false);
        progress.setManaged(false);
        refreshState();
        if (SharingManager.getInstance().getMode() != SharingConfig.Mode.HOST) discoverHosts();
    }

    public boolean isConnectionChanged() { return connectionChanged; }

    private void refreshState() {
        SharingManager sharing = SharingManager.getInstance();
        SharingConfig config = sharing.getConfig();
        lblMode.setText(switch (config.getMode()) {
            case LOCAL -> "This computer only";
            case HOST -> "Main computer";
            case CLIENT -> "Connected computer";
        });
        lblStatus.setText(sharing.statusText());
        lblHostAddress.setText(sharing.isHost() ? sharing.hostAddress() : "—");
        txtServerName.setText(config.getServerName());

        boolean admin = SessionManager.isAdmin();
        boolean firstRun = false;
        try { firstRun = !DatabaseService.getInstance().hasUsers(); } catch (Exception ignored) {}
        boolean mayConfigureHost = admin || firstRun;
        lblPairingCode.setText(sharing.isHost()
            ? (mayConfigureHost ? formatCode(config.getPairingCode()) : "Administrator sign-in required")
            : "—");
        btnStartHost.setDisable(!mayConfigureHost || config.getMode() != SharingConfig.Mode.LOCAL);
        btnBackup.setDisable(!admin || !sharing.isHost());
        btnRestore.setDisable(!mayConfigureHost || config.getMode() != SharingConfig.Mode.LOCAL);
        btnUseLocal.setDisable(config.getMode() != SharingConfig.Mode.CLIENT);
        btnConnect.setDisable(config.getMode() == SharingConfig.Mode.HOST);
    }

    @FXML private void onDiscover() { discoverHosts(); }

    private void discoverHosts() {
        runTask("Looking for the main computer...",
            () -> NetworkDiscoveryService.discover(1600), hosts -> {
                cmbHosts.getItems().setAll(hosts);
                if (!hosts.isEmpty()) {
                    cmbHosts.getSelectionModel().selectFirst();
                    txtHost.setText(hosts.get(0).host());
                    showMessage("Found " + hosts.size() + " Athlete Track main computer(s).", false);
                } else {
                    showMessage("No main computer was found automatically. You can enter its address below.", false);
                }
            });
    }

    @FXML private void onHostSelected() {
        HostOffer offer = cmbHosts.getValue();
        if (offer != null) txtHost.setText(offer.host());
    }

    @FXML private void onStartHost() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "This computer will become the main Athlete Track computer. Existing records and user accounts will be copied into the shared database.\n\nContinue?",
            ButtonType.YES, ButtonType.CANCEL);
        confirm.setTitle("Start Multi-PC Sharing");
        if (confirm.showAndWait().filter(ButtonType.YES::equals).isEmpty()) return;
        runTask("Preparing the shared database...", () -> {
            SharingManager.getInstance().enableHost(txtServerName.getText());
            return null;
        }, ignored -> {
            connectionChanged = true;
            refreshState();
            showMessage("Sharing is ready. Use the address and connection code shown above on the other computers.", false);
        });
    }

    @FXML private void onConnect() {
        HostOffer selected = cmbHosts.getValue();
        String host = txtHost.getText().trim();
        int port = selected != null && selected.host().equals(host)
            ? selected.pairingPort() : SharingConfig.DEFAULT_PAIRING_PORT;
        String code = txtPairingCode.getText().replaceAll("\\D", "");
        if (host.isBlank()) { showMessage("Select a main computer or enter its address.", true); return; }
        if (code.length() != 6) { showMessage("Enter the six-digit connection code.", true); return; }

        runTask("Connecting to the main computer...", () -> {
            SharingManager.getInstance().connectClient(host, port, code);
            return null;
        }, ignored -> {
            connectionChanged = true;
            refreshState();
            showMessage("Connected. Close this window and sign in using the account created by the administrator.", false);
        });
    }

    @FXML private void onUseLocal() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Disconnect from the main computer and return to this computer's original local records? Shared records will remain safely on the main computer.",
            ButtonType.YES, ButtonType.CANCEL);
        confirm.setTitle("Disconnect This Computer");
        if (confirm.showAndWait().filter(ButtonType.YES::equals).isEmpty()) return;
        runTask("Returning to the local database...", () -> {
            SharingManager.getInstance().useLocalDatabase();
            return null;
        }, ignored -> {
            connectionChanged = true;
            refreshState();
            showMessage("This installation is using its original local database again.", false);
        });
    }

    @FXML private void onBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Athlete Track Backup");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Athlete Track backup", "*.atbackup"));
        chooser.setInitialFileName(BackupService.defaultBackupFileName());
        File destination = chooser.showSaveDialog(btnBackup.getScene().getWindow());
        if (destination == null) return;
        runTask("Creating backup...", () -> BackupService.createSharedBackup(destination.toPath()), path ->
            showMessage("Backup saved to " + path, false));
    }

    @FXML private void onRestore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Restore Athlete Track Main Computer");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Athlete Track backup", "*.atbackup"));
        File source = chooser.showOpenDialog(btnRestore.getScene().getWindow());
        if (source == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "This computer will become the main computer using the records and accounts in the selected backup.\n\nContinue?",
            ButtonType.YES, ButtonType.CANCEL);
        confirm.setTitle("Restore Main Computer");
        if (confirm.showAndWait().filter(ButtonType.YES::equals).isEmpty()) return;
        runTask("Restoring the shared database...", () -> {
            SharingManager.getInstance().restoreHostBackup(source.toPath(), txtServerName.getText());
            return null;
        }, ignored -> {
            connectionChanged = true;
            refreshState();
            showMessage("The backup was restored. This computer is now the main computer.", false);
        });
    }

    @FXML private void onClose() {
        ((Stage) btnConnect.getScene().getWindow()).close();
    }

    private <T> void runTask(String message, Callable<T> work, java.util.function.Consumer<T> success) {
        setBusy(true, message);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(event -> {
            setBusy(false, null);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(false, null);
            Throwable error = task.getException();
            showMessage(error == null ? "The operation could not be completed." : error.getMessage(), true);
        });
        Thread thread = new Thread(task, "athlete-track-sharing-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisible(busy);
        progress.setManaged(busy);
        btnStartHost.setDisable(busy || btnStartHost.isDisable());
        btnConnect.setDisable(busy || SharingManager.getInstance().isHost());
        btnDiscover.setDisable(busy);
        btnRestore.setDisable(busy || btnRestore.isDisable());
        if (busy) showMessage(message, false); else refreshState();
    }

    private void showMessage(String message, boolean error) {
        Platform.runLater(() -> {
            lblMessage.setText(message == null ? "" : message);
            lblMessage.getStyleClass().removeAll("sharing-message-error", "sharing-message-ok");
            lblMessage.getStyleClass().add(error ? "sharing-message-error" : "sharing-message-ok");
            lblMessage.setVisible(true);
            lblMessage.setManaged(true);
        });
    }

    private static String formatCode(String code) {
        if (code == null || code.length() != 6) return code == null ? "—" : code;
        return code.substring(0, 3) + " " + code.substring(3);
    }
}
