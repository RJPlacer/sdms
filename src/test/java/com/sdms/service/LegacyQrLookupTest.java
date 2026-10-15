package com.sdms.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sdms.model.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyQrLookupTest {
  @Test
  void olderLearnerAndEmployeeCodesResolveWithoutGuessingDuplicateIdentities(@TempDir Path appDir)
      throws Exception {
    System.setProperty("sdms.appDir", appDir.toString());
    DatabaseService db = DatabaseService.getInstance();
    try {
      db.initializeDatabase();
      Athlete athlete = new Athlete();
      athlete.setFirstName("Legacy");
      athlete.setLastName("Athlete");
      athlete.setLearnerRefNumber("123456789012");
      db.saveAthlete(athlete);
      assertArrayEquals(
          new String[] {"ATHLETE", athlete.getAthleteId()},
          db.findPersonByLegacyId("123456789012"));
      Official official = new Official();
      official.setEmployeeNumber("EMP-123");
      db.saveOfficial(official);
      assertArrayEquals(
          new String[] {"OFFICIAL", official.getOfficialId()}, db.findPersonByLegacyId("EMP-123"));
      Coach coach = new Coach();
      coach.setEmployeeNumber("EMP-123");
      db.saveCoach(coach);
      assertThrows(java.sql.SQLException.class, () -> db.findPersonByLegacyId("EMP-123"));
      assertNull(db.findPersonByLegacyId("unknown"));
    } finally {
      db.closeConnection();
      System.clearProperty("sdms.appDir");
    }
  }
}
