package com.sdms.sharing;

import com.sdms.model.*;
import com.sdms.service.DatabaseService;
import com.sdms.service.QRCodeService;
import com.sdms.util.SessionManager;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;

/** A separate JVM verifies the same client path used on an additional computer. */
public final class SharingClientProbe {
  public static void main(String[] args) throws Exception {
    System.setProperty("sdms.appDir", args[0]);
    SharingManager sharing = SharingManager.getInstance();
    DatabaseService db = DatabaseService.getInstance();
    try {
      sharing.connectClient("127.0.0.1", Integer.parseInt(args[1]), args[2]);
      SessionManager.login(db.authenticate("test-encoder", "secure-test-password"));
      if (db.getAllAthletes().size() != 1) throw new AssertionError("Read did not reach host");
      Athlete athlete = new Athlete();
      athlete.setFirstName("Client");
      athlete.setLastName("Roundtrip");
      athlete.setSchool("Test School");
      athlete.setDateOfBirth(LocalDate.of(2010, 1, 2));
      Athlete.PalaroPrevious participation = new Athlete.PalaroPrevious();
      participation.year = "2025";
      participation.sportsEvent = "Athletics";
      db.saveAthleteWithRecords(athlete, List.of(participation), List.of());
      if (athlete.getId() == 0
          || athlete.getAthleteId() == null
          || db.getAthletePalaroRecords(athlete.getId()).size() != 1)
        throw new AssertionError("Generated IDs or children lost");
      String qr = QRCodeService.refreshAthleteQRCode(athlete);
      if (!qr.startsWith("shared:qrcodes/")) throw new AssertionError("QR was not uploaded");
      if (!Files.isRegularFile(SharedAssetService.localPath(qr)))
        throw new AssertionError("Shared QR could not be retrieved");
      Official official = new Official();
      official.setFirstName("Client");
      official.setLastName("Official");
      Official.EducationalQualification education = new Official.EducationalQualification();
      education.school = "Test University";
      official.setEducationalQualifications(List.of(education));
      db.saveOfficial(official);
      if (db.findOfficialByOfficialId(official.getOfficialId())
              .getEducationalQualifications()
              .size()
          != 1) throw new AssertionError("Official education lost");
      Coach coach = new Coach();
      coach.setFirstName("Client");
      coach.setLastName("Coach");
      coach.setEducationalQualifications(List.of(education));
      db.saveCoach(coach);
      if (db.findCoachByCoachId(coach.getCoachId()).getEducationalQualifications().size() != 1)
        throw new AssertionError("Coach education lost");
      Equipment equipment = new Equipment();
      equipment.setBorrowerName("Client Borrower");
      equipment.setDate(LocalDate.of(2026, 10, 15));
      Equipment.EquipmentItem item = new Equipment.EquipmentItem();
      item.qty = 2;
      item.makeAndDescription = "Test equipment";
      equipment.setItems(List.of(item));
      db.saveEquipment(equipment);
      if (db.getEquipmentItems(equipment.getId()).get(0).qty != 2)
        throw new AssertionError("Equipment children lost");
      if (db.globalSearch("Roundtrip").isEmpty()) throw new AssertionError("Search lost");
      try {
        db.deleteAthlete(athlete.getId());
        throw new AssertionError("Encoder could delete");
      } catch (java.sql.SQLException expected) {
      }
      SessionManager.logout();
      try {
        db.getAllAthletes();
        throw new AssertionError("Logout left access");
      } catch (java.sql.SQLException expected) {
      }
      System.out.println("CLIENT_ROUNDTRIP_OK");
    } finally {
      db.closeConnection();
      sharing.close();
    }
  }
}
