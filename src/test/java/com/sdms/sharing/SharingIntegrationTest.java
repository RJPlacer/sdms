package com.sdms.sharing;

import com.sdms.model.Athlete;
import com.sdms.model.Coach;
import com.sdms.model.Equipment;
import com.sdms.model.Official;
import com.sdms.model.User;
import com.sdms.service.BackupService;
import com.sdms.service.DatabaseService;
import com.sdms.util.PasswordUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SharingIntegrationTest {

    @Test
    void hostMigrationPairingSharedAssetsAndBackupWorkTogether(@TempDir Path appDir) throws Exception {
        System.setProperty("sdms.appDir", appDir.toString());
        System.setProperty("sdms.disableAutoBackup", "true");
        DatabaseService database = DatabaseService.getInstance();
        SharingManager sharing = SharingManager.getInstance();
        try {
            database.initializeDatabase();
            User admin = new User();
            admin.setUsername("sharing-admin");
            admin.setFullName("Sharing Administrator");
            admin.setPasswordHash(PasswordUtil.hash("secure-test-password"));
            admin.setRole("ADMIN");
            admin.setActive(true);
            database.saveUser(admin);

            Athlete athlete = new Athlete();
            athlete.setFirstName("Shared");
            athlete.setLastName("Athlete");
            athlete.setSchool("Test School");
            Path legacyPhoto = appDir.resolve("legacy-photo.png");
            Files.write(legacyPhoto, new byte[]{9, 8, 7});
            athlete.setPhotoPath(legacyPhoto.toString());
            database.saveAthlete(athlete);

            Official official = new Official();
            official.setFirstName("Shared");
            official.setLastName("Official");
            official.setSchool("Test School");
            Official.EducationalQualification education = new Official.EducationalQualification();
            education.level = "College";
            education.school = "State University";
            official.setEducationalQualifications(List.of(education));
            database.saveOfficial(official);

            Coach coach = new Coach();
            coach.setFirstName("Shared");
            coach.setLastName("Coach");
            coach.setSchool("Test School");
            Official.SportsTraining training = new Official.SportsTraining();
            training.title = "Coaching Clinic";
            training.numberOfHours = 8;
            coach.setSportsTrainings(List.of(training));
            database.saveCoach(coach);

            Equipment equipment = new Equipment();
            equipment.setBorrowerName("Shared Borrower");
            equipment.setSchool("Test School");
            equipment.setEvent("Athletics");
            equipment.setDate(LocalDate.now());
            Equipment.EquipmentItem item = new Equipment.EquipmentItem();
            item.qty = 2;
            item.unit = "pcs";
            item.makeAndDescription = "Stopwatch";
            equipment.setItems(List.of(item));
            database.saveEquipment(equipment);

            sharing.enableHost("Test Main Computer");
            assertTrue(sharing.isHost());
            assertEquals(1, database.getAllAthletes().size());
            assertTrue(database.getAllAthletes().get(0).getPhotoPath().startsWith("shared:photos/"));
            assertEquals(1, database.getAllOfficials().size());
            assertEquals(1, database.getAllOfficials().get(0).getEducationalQualifications().size());
            assertEquals(1, database.getAllCoaches().size());
            assertEquals(1, database.getAllCoaches().get(0).getSportsTrainings().size());
            assertEquals(1, database.getAllEquipment().size());
            assertEquals(1, database.getAllEquipment().get(0).getItems().size());
            assertNotNull(database.findUserByUsername("sharing-admin"));

            Athlete second = new Athlete();
            second.setFirstName("Second");
            second.setLastName("Encoder Entry");
            second.setSchool("Test School");
            database.saveAthlete(second);
            assertNotEquals(athlete.getAthleteId(), second.getAthleteId());
            assertEquals(2, database.getAthletesBySchool("Test School").size());
            assertFalse(database.globalSearch("Shared").isEmpty());

            SharingConfig client = PairingClient.pair("127.0.0.1",
                sharing.getConfig().getPairingPort(), sharing.getConfig().getPairingCode());
            assertTrue(client.getDatabasePassword().isBlank());
            assertTrue(client.getDatabaseUser().isBlank());
            assertFalse(client.getCertificatePin().isBlank());
            assertThrows(java.sql.SQLException.class, () -> DriverManager.getConnection(
                "jdbc:h2:tcp://127.0.0.1:" + client.getDatabasePort() + "/./athlete-track", "", ""));

            Path image = appDir.resolve("sample.png");
            byte[] imageBytes = new byte[]{1, 2, 3, 4, 5};
            Files.write(image, imageBytes);
            String stored = SharedAssetService.importPhoto(image);
            assertTrue(stored.startsWith("shared:photos/"));
            String imageUrl = "https://127.0.0.1:" + sharing.getConfig().getPairingPort()
                + "/assets/" + stored.substring("shared:".length())
                + "?token=" + sharing.getConfig().getAssetToken();
            HttpResponse<byte[]> response = TlsSupport.client(client.getCertificatePin()).send(
                HttpRequest.newBuilder(URI.create(imageUrl))
                    .header("X-Athlete-Track-Session", SecureSharingTest.login(client, "sharing-admin")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertArrayEquals(imageBytes, response.body());

            Path backup = appDir.resolve("test.atbackup");
            BackupService.createSharedBackup(backup);
            assertTrue(Files.size(backup) > 0);

            sharing.useLocalDatabase();
            sharing.restoreHostBackup(backup, "Restored Main Computer");
            assertTrue(sharing.isHost());
            assertEquals(2, database.getAllAthletes().size());
            assertNotNull(database.findUserByUsername("sharing-admin"));
        } finally {
            if (sharing.getMode() != SharingConfig.Mode.LOCAL) sharing.useLocalDatabase();
            database.closeConnection();
            sharing.close();
            System.clearProperty("sdms.appDir");
            System.clearProperty("sdms.disableAutoBackup");
        }
    }
}
