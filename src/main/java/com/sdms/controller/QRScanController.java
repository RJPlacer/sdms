package com.sdms.controller;

import com.sdms.model.Athlete;
import com.sdms.model.Coach;
import com.sdms.model.Official;
import com.sdms.service.DatabaseService;
import com.sdms.service.OnlineVerificationService;
import com.sdms.service.ScannerRelayService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QRScanController {

    @FXML private TextField txtScan;
    @FXML private TextArea txtRaw;
    @FXML private Label lblStatus;
    @FXML private ImageView imgPhoto;
    @FXML private Label lblNoPhoto;
    @FXML private VBox detailsContainer;
    @FXML private Button btnConnectPhone;
    @FXML private Button btnDisconnectPhone;
    @FXML private Label lblPhoneStatus;
    @FXML private ImageView imgPairingQr;

    private final ScheduledExecutorService relayExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "scanner-relay");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean pollInProgress = new AtomicBoolean();
    private volatile ScannerRelayService.RelaySession relaySession;
    private ScheduledFuture<?> pollingTask;

    @FXML public void initialize() {
        clearResult();
        setPhoneDisconnected("Connect an Android phone to scan over the Internet.");
        txtScan.requestFocus();
        Platform.runLater(() -> txtScan.getScene().getWindow().addEventHandler(
            WindowEvent.WINDOW_HIDDEN, event -> shutdownRelay()));
    }

    @FXML private void onConnectPhone() {
        btnConnectPhone.setDisable(true);
        lblPhoneStatus.setText("Creating a secure 15-minute scanner session…");
        relayExecutor.execute(() -> {
            try {
                ScannerRelayService.RelaySession session = ScannerRelayService.createSession();
                relaySession = session;
                Image pairingQr = createPairingQr(session.pairUrl().toString());
                Platform.runLater(() -> {
                    imgPairingQr.setImage(pairingQr);
                    imgPairingQr.setVisible(true);
                    imgPairingQr.setManaged(true);
                    btnDisconnectPhone.setDisable(false);
                    lblPhoneStatus.setText("Scan this pairing QR with Android, then tap Connect on the phone.");
                });
                pollingTask = relayExecutor.scheduleWithFixedDelay(this::pollRelay, 0, 750, TimeUnit.MILLISECONDS);
            } catch (Exception error) {
                Platform.runLater(() -> setPhoneDisconnected(cleanError(error)));
            }
        });
    }

    @FXML private void onDisconnectPhone() {
        disconnectPhone(false);
    }

    private void pollRelay() {
        ScannerRelayService.RelaySession session = relaySession;
        if (session == null || !pollInProgress.compareAndSet(false, true)) return;
        try {
            ScannerRelayService.PollResult result = ScannerRelayService.poll(session);
            Platform.runLater(() -> {
                if (relaySession != session) return;
                lblPhoneStatus.setText(result.paired()
                    ? "Phone connected. Waiting for a QR scan…"
                    : "Waiting for the Android phone to connect…");
                result.payload().ifPresent(payload -> {
                    txtScan.setText(payload);
                    onScan();
                    lblPhoneStatus.setText("Scan received from phone. Ready for the next QR code.");
                });
            });
        } catch (Exception error) {
            Platform.runLater(() -> {
                if (relaySession == session) disconnectPhoneWithMessage(cleanError(error));
            });
        } finally {
            pollInProgress.set(false);
        }
    }

    private Image createPairingQr(String value) throws Exception {
        var matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 210, 210);
        return SwingFXUtils.toFXImage(MatrixToImageWriter.toBufferedImage(matrix), null);
    }

    private void disconnectPhone(boolean shuttingDown) {
        ScannerRelayService.RelaySession session = relaySession;
        relaySession = null;
        if (pollingTask != null) pollingTask.cancel(false);
        pollingTask = null;
        if (!shuttingDown) setPhoneDisconnected("Phone scanner disconnected.");
        if (session != null && !relayExecutor.isShutdown()) {
            relayExecutor.execute(() -> {
                try {
                    ScannerRelayService.disconnect(session);
                } catch (Exception ignored) {
                    // The session expires automatically if the network is unavailable.
                }
            });
        }
    }

    private void disconnectPhoneWithMessage(String message) {
        disconnectPhone(false);
        lblPhoneStatus.setText(message);
    }

    private void setPhoneDisconnected(String message) {
        imgPairingQr.setImage(null);
        imgPairingQr.setVisible(false);
        imgPairingQr.setManaged(false);
        btnConnectPhone.setDisable(false);
        btnDisconnectPhone.setDisable(true);
        lblPhoneStatus.setText(message);
    }

    private String cleanError(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Phone scanner connection failed." : message;
    }

    private void shutdownRelay() {
        disconnectPhone(true);
        relayExecutor.shutdown();
    }

    @FXML private void onScan() {
        String raw = txtScan.getText() != null ? txtScan.getText().trim() : "";
        if (raw.isBlank()) {
            lblStatus.setText("Scan input is empty.");
            return;
        }

        txtRaw.setText(raw);
        detailsContainer.getChildren().clear();
        showPhoto(null);

        try {
            String verificationToken = OnlineVerificationService.extractVerificationToken(raw);
            if (verificationToken != null) {
                String[] person = DatabaseService.getInstance()
                    .findPersonByVerificationToken(verificationToken);
                if (person == null) {
                    lblStatus.setText("This online verification QR is not linked to a record in this local database.");
                    return;
                }
                showPerson(person[0], person[1]);
                return;
            }

            ScanPayload payload = parsePayload(raw);
            if (payload.module == null || payload.id == null || payload.id.isBlank()) {
                lblStatus.setText("Could not parse QR payload. Expected Athlete Track format.");
                return;
            }

            showPerson(payload.module, payload.id);
        } catch (Exception e) {
            lblStatus.setText("Scan error: " + e.getMessage());
        }
    }

    private void showPerson(String module, String id) throws SQLException {
        switch (module) {
            case "ATHLETE" -> showAthlete(id);
            case "OFFICIAL" -> showOfficial(id);
            case "COACH" -> showCoach(id);
            default -> lblStatus.setText("Unsupported module in QR: " + module);
        }
    }

    @FXML private void onClear() {
        txtScan.clear();
        txtRaw.clear();
        clearResult();
        txtScan.requestFocus();
    }

    @FXML private void onClose() {
        shutdownRelay();
        ((Stage) txtScan.getScene().getWindow()).close();
    }

    private void showAthlete(String athleteId) throws SQLException {
        Athlete a = DatabaseService.getInstance().findAthleteByAthleteId(athleteId);
        if (a == null) {
            lblStatus.setText("No athlete found for ID: " + athleteId);
            return;
        }

        showPhoto(a.getPhotoPath());
        addSection("Identity",
            "Module", "Athlete",
            "Athlete ID", a.getAthleteId(),
            "Full Name", a.getFullName());
        addSection("Personal Information",
            "Sex", a.getSex(),
            "Learner Reference No.", a.getLearnerRefNumber(),
            "Contact No.", a.getContactNo(),
            "Date of Birth", a.getDateOfBirth(),
            "Age", a.getAge(),
            "Place of Birth", a.getPlaceOfBirth(),
            "Present Address", a.getPresentAddress());
        addSection("School Information",
            "School", a.getSchool(),
            "School Address", a.getSchoolAddress());
        addSection("Guardian Information",
            "Mother's Name", a.getMotherName(),
            "Father's Name", a.getFatherName(),
            "Guardian Address", a.getGuardianAddress());
        addAthleteHistory(a);
        addSection("Certification",
            "Meet", a.getCertMeet(),
            "Coach", a.getCertCoachName(),
            "Division Sports Officer", a.getCertDsoName(),
            "Regional Sports Officer", a.getCertRsoName());
        lblStatus.setText("Match found. Full athlete record loaded.");
    }

    private void showOfficial(String officialId) throws SQLException {
        Official o = DatabaseService.getInstance().findOfficialByOfficialId(officialId);
        if (o == null) {
            lblStatus.setText("No official found for ID: " + officialId);
            return;
        }

        showPhoto(o.getPhotoPath());
        addSection("Identity",
            "Module", "Official",
            "Official ID", o.getOfficialId(),
            "Employee No.", o.getEmployeeNumber(),
            "Full Name", o.getFullName());
        addStaffDetails(o.getSex(), o.getMobilePhone(), o.getDateOfBirth(), o.getAge(),
            o.getPlaceOfBirth(), o.getCurrentPosition(), o.getYearsInService(), o.getSchool(),
            o.getSchoolAddress(), o.getPresentAddress(), o.getEmergencyContactName(), o.getEmergencyContactNo());
        addProfessionalHistory(o.getEducationalQualifications(), o.getSportsTrainings(), o.getTrackRecords());
        lblStatus.setText("Match found. Full official record loaded.");
    }

    private void showCoach(String coachId) throws SQLException {
        Coach c = DatabaseService.getInstance().findCoachByCoachId(coachId);
        if (c == null) {
            lblStatus.setText("No coach found for ID: " + coachId);
            return;
        }

        showPhoto(c.getPhotoPath());
        addSection("Identity",
            "Module", "Coach",
            "Coach ID", c.getCoachId(),
            "Employee No.", c.getEmployeeNumber(),
            "Full Name", c.getFullName());
        addStaffDetails(c.getSex(), c.getMobilePhone(), c.getDateOfBirth(), c.getAge(),
            c.getPlaceOfBirth(), c.getCurrentPosition(), c.getYearsInService(), c.getSchool(),
            c.getSchoolAddress(), c.getPresentAddress(), c.getEmergencyContactName(), c.getEmergencyContactNo());
        addProfessionalHistory(c.getEducationalQualifications(), c.getSportsTrainings(), c.getTrackRecords());
        lblStatus.setText("Match found. Full coach record loaded.");
    }

    private void addStaffDetails(Object sex, Object phone, Object birthDate, Object age,
                                 Object birthPlace, Object position, Object yearsInService,
                                 Object school, Object schoolAddress, Object presentAddress,
                                 Object emergencyName, Object emergencyPhone) {
        addSection("Personal Information",
            "Sex", sex,
            "Mobile Phone", phone,
            "Date of Birth", birthDate,
            "Age", age,
            "Place of Birth", birthPlace,
            "Current Position", position,
            "Years in Service", yearsInService,
            "Present Address", presentAddress);
        addSection("School Information",
            "School/Office", school,
            "School Address", schoolAddress);
        addSection("Emergency Contact",
            "Contact Person", emergencyName,
            "Contact No.", emergencyPhone);
    }

    private void addAthleteHistory(Athlete a) {
        List<Athlete.PalaroPrevious> previous = a.getPreviousPalaroRecords();
        if (previous == null || previous.isEmpty()) {
            addSection("Previous Palarong Pambansa", "Records", "No records");
        } else {
            int index = 1;
            for (Athlete.PalaroPrevious record : previous) {
                addSection("Previous Palarong Pambansa - Record " + index++,
                    "Year", record.year,
                    "Sports Event", record.sportsEvent,
                    "Venue", record.venue,
                    "Remarks", record.remarks);
            }
        }

        List<Athlete.LowerMeet> lowerMeets = a.getLowerMeetRecords();
        if (lowerMeets == null || lowerMeets.isEmpty()) {
            addSection("Lower Meets", "Records", "No records");
        } else {
            int index = 1;
            for (Athlete.LowerMeet record : lowerMeets) {
                addSection("Lower Meet - Record " + index++,
                    "Inclusive Dates", record.inclusiveDates,
                    "Sports Event", record.sportsEvent,
                    "Athletic Meet", record.athleticMeet,
                    "Remarks", record.remarks);
            }
        }
    }

    private void addProfessionalHistory(List<Official.EducationalQualification> education,
                                        List<Official.SportsTraining> trainings,
                                        List<Official.TrackRecord> trackRecords) {
        if (education == null || education.isEmpty()) {
            addSection("Educational Qualifications", "Records", "No records");
        } else {
            int index = 1;
            for (Official.EducationalQualification record : education) {
                addSection("Educational Qualification - Record " + index++,
                    "Level", record.level,
                    "Course", record.course,
                    "School", record.school,
                    "Year Graduated", record.yearGraduated,
                    "Credits Earned", record.creditsEarned,
                    "Awards", record.awardsReceived);
            }
        }

        if (trainings == null || trainings.isEmpty()) {
            addSection("Sports Training", "Records", "No records");
        } else {
            int index = 1;
            for (Official.SportsTraining record : trainings) {
                addSection("Sports Training - Record " + index++,
                    "Title", record.title,
                    "Date", record.dateOfTraining,
                    "Hours", record.numberOfHours,
                    "Conducted By", record.conductedBy);
            }
        }

        if (trackRecords == null || trackRecords.isEmpty()) {
            addSection("Sports Track Record", "Records", "No records");
        } else {
            int index = 1;
            for (Official.TrackRecord record : trackRecords) {
                addSection("Sports Track Record - Record " + index++,
                    "Meet Attended", record.athleteMeetAttended,
                    "Inclusive Dates", record.inclusiveDates,
                    "Event", record.event,
                    "Awards", record.awardsReceived);
            }
        }
    }

    private void addSection(String title, Object... labelValuePairs) {
        Label heading = new Label(title);
        heading.setMaxWidth(Double.MAX_VALUE);
        heading.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a56a0; " +
            "-fx-background-color: #eef4fb; -fx-padding: 6 8;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        grid.setStyle("-fx-padding: 0 8 2 8;");

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(145);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);

        int row = 0;
        for (int i = 0; i + 1 < labelValuePairs.length; i += 2) {
            Label label = new Label(String.valueOf(labelValuePairs[i]) + ":");
            label.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
            Label value = new Label(displayValue(labelValuePairs[i + 1]));
            value.setWrapText(true);
            value.setMaxWidth(Double.MAX_VALUE);
            grid.add(label, 0, row);
            grid.add(value, 1, row++);
        }

        VBox section = new VBox(7, heading, grid);
        detailsContainer.getChildren().add(section);
    }

    private void showPhoto(String photoPath) {
        imgPhoto.setImage(null);
        lblNoPhoto.setVisible(true);
        lblNoPhoto.setManaged(true);
        if (photoPath == null || photoPath.isBlank()) return;

        try {
            Path path = Path.of(photoPath);
            if (!Files.isRegularFile(path)) return;
            imgPhoto.setImage(new Image(path.toUri().toString(), false));
            lblNoPhoto.setVisible(false);
            lblNoPhoto.setManaged(false);
        } catch (Exception ignored) {
            imgPhoto.setImage(null);
        }
    }

    private void clearResult() {
        clearProfile();
        lblStatus.setText("Ready. Scan a QR code or type an ID and press Enter.");
    }

    private void clearProfile() {
        detailsContainer.getChildren().clear();
        showPhoto(null);
        addSection("No record selected", "Status", "Scan a QR code to load the person's full record");
    }

    private String displayValue(Object value) {
        if (value == null) return "-";
        String text = String.valueOf(value).trim();
        return text.isBlank() ? "-" : text;
    }

    private ScanPayload parsePayload(String raw) {
        String text = raw.trim();
        Map<String, String> map = new HashMap<>();
        String module = null;

        String[] parts = text.split("\\|");
        if (parts.length > 0 && parts[0].contains(":")) {
            String first = parts[0];
            module = first.substring(first.lastIndexOf(':') + 1).trim().toUpperCase();
        }

        for (String part : parts) {
            int idx = part.indexOf(':');
            if (idx > 0 && idx < part.length() - 1) {
                String key = part.substring(0, idx).trim().toUpperCase();
                String val = part.substring(idx + 1).trim();
                map.put(key, val);
            }
        }

        String id = map.get("ID");
        if (id == null || id.isBlank()) id = extractStructuredId(text);
        if (module == null || module.isBlank()) module = inferModuleFromId(id);
        return new ScanPayload(module, id);
    }

    private String extractStructuredId(String text) {
        Pattern p = Pattern.compile("(ATH-\\d{4}-\\d+|OFC-\\d{4}-\\d+|CCH-\\d{4}-\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    private String inferModuleFromId(String id) {
        if (id == null) return null;
        if (id.startsWith("ATH-")) return "ATHLETE";
        if (id.startsWith("OFC-")) return "OFFICIAL";
        if (id.startsWith("CCH-")) return "COACH";
        return null;
    }

    private record ScanPayload(String module, String id) {}
}
