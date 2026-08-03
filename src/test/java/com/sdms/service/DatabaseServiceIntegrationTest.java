package com.sdms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sdms.model.Athlete;
import com.sdms.model.Official;
import com.sdms.model.User;
import com.sdms.util.PasswordUtil;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseServiceIntegrationTest {

    @Test
    void idsRemainUniqueAndDeletingOfficialRemovesChildren(@TempDir Path appDir) throws Exception {
        System.setProperty("sdms.appDir", appDir.toString());
        DatabaseService database = DatabaseService.getInstance();
        database.initializeDatabase();
        try {
            assertFalse(database.hasUsers(), "A new database must not create a shared default account");
            User administrator = new User();
            administrator.setUsername("test-admin");
            administrator.setFullName("Test Administrator");
            administrator.setPasswordHash(PasswordUtil.hash("strong-test-password"));
            administrator.setRole("ADMIN");
            administrator.setActive(true);
            database.saveUser(administrator);
            assertTrue(database.hasUsers());

            Athlete first = athlete("First");
            Athlete second = athlete("Second");
            Athlete third = athlete("Third");
            database.saveAthlete(first);
            database.saveAthlete(second);
            database.saveAthlete(third);
            assertEquals("Grade 6", database.getAllAthletes().get(0).getGradeLevel());

            String verificationToken = database.getOrCreateVerificationToken("ATHLETE", first.getId());
            assertEquals(verificationToken,
                database.getOrCreateVerificationToken("ATHLETE", first.getId()));
            assertArrayEquals(new String[]{"ATHLETE", first.getAthleteId()},
                database.findPersonByVerificationToken(verificationToken));

            database.deleteAthlete(second.getId());

            Athlete fourth = athlete("Fourth");
            database.saveAthlete(fourth);
            assertNotEquals(third.getAthleteId(), fourth.getAthleteId());

            Official official = new Official();
            official.setLastName("Official");
            official.setFirstName("Test");
            Official.EducationalQualification education = new Official.EducationalQualification();
            education.level = "College";
            official.setEducationalQualifications(List.of(education));
            database.saveOfficial(official);
            database.deleteOfficial(official.getId());

            try (var connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + appDir.resolve("sdms.db"));
                 var statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM official_education WHERE parent_id=?")) {
                statement.setInt(1, official.getId());
                try (var result = statement.executeQuery()) {
                    result.next();
                    assertEquals(0, result.getInt(1));
                }
            }
        } finally {
            database.closeConnection();
            System.clearProperty("sdms.appDir");
        }
    }

    private Athlete athlete(String lastName) {
        Athlete athlete = new Athlete();
        athlete.setLastName(lastName);
        athlete.setFirstName("Test");
        athlete.setGradeLevel("Grade 6");
        return athlete;
    }
}
