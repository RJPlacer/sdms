package com.sdms.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.sdms.model.Athlete;
import com.sdms.model.Equipment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SDOFormPDFServiceTest {

    @Test
    void createsOfficialAthleteAndEquipmentForms(@TempDir Path appDir) throws Exception {
        System.setProperty("sdms.appDir", appDir.toString());
        try {
            Athlete athlete = sampleAthlete();
            Path athletePdf = Path.of(SDOFormPDFService.exportAthleteRecord(athlete));
            verifyPdf(athletePdf);
            verifyAthleteTemplate(athletePdf);

            Equipment equipment = sampleEquipment();
            Path equipmentPdf = Path.of(SDOFormPDFService.exportEquipmentMemorandum(equipment));
            verifyPdf(equipmentPdf);
        } finally {
            System.clearProperty("sdms.appDir");
        }
    }

    @Test
    void writesVisualSamplesWhenRequested(@TempDir Path appDir) throws Exception {
        String snapshotDir = System.getProperty("sdms.pdfSnapshotDir");
        Assumptions.assumeTrue(snapshotDir != null && !snapshotDir.isBlank(),
            "Set sdms.pdfSnapshotDir to generate visual PDF samples");
        Path destination = Path.of(snapshotDir);
        Files.createDirectories(destination);
        System.setProperty("sdms.appDir", appDir.toString());
        try {
            Path athlete = Path.of(SDOFormPDFService.exportAthleteRecord(sampleAthlete()));
            Path equipment = Path.of(SDOFormPDFService.exportEquipmentMemorandum(sampleEquipment()));
            Files.copy(athlete, destination.resolve("SAMPLE_SDO_ATHLETE_RECORD.pdf"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(equipment, destination.resolve("SAMPLE_SDO_EQUIPMENT_MEMORANDUM.pdf"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            System.clearProperty("sdms.appDir");
        }
    }

    private static Athlete sampleAthlete() {
        Athlete athlete = new Athlete();
        athlete.setAthleteId("ATH-2026-0099");
        athlete.setLastName("Santos");
        athlete.setFirstName("Jessie");
        athlete.setMiddleInitial("S");
        athlete.setSex("Male");
        athlete.setLearnerRefNumber("10476035707");
        athlete.setContactNo("09171234567");
        athlete.setDateOfBirth(LocalDate.of(2013, 12, 24));
        athlete.setAge(12);
        athlete.setPlaceOfBirth("City of Baliwag, Bulacan");
        athlete.setSchool("Virgen Delas Flores Elementary School");
        athlete.setGradeLevel("Grade 6");
        athlete.setSchoolAddress("City of Baliwag, Bulacan");
        athlete.setPresentAddress("Mercury Street, City of Baliwag, Bulacan");
        athlete.setFatherName("Juan Santos");
        athlete.setMotherName("Maria Santos");
        athlete.setGuardianAddress("Mercury Street, City of Baliwag, Bulacan");
        Athlete.PalaroPrevious previous = new Athlete.PalaroPrevious();
        previous.year = "2025";
        previous.sportsEvent = "Athletics";
        previous.venue = "Laoag City";
        previous.remarks = "Bronze";
        athlete.setPreviousPalaroRecords(List.of(previous));
        Athlete.LowerMeet lower = new Athlete.LowerMeet();
        lower.inclusiveDates = "10/10/2025";
        lower.sportsEvent = "100m Run";
        lower.athleticMeet = "Division Meet";
        lower.remarks = "1st Place";
        athlete.setLowerMeetRecords(List.of(lower));
        athlete.setCertMeet("Division Meet");
        athlete.setCertCoachName("Coach Test");
        athlete.setCertDsoName("DSO Test");
        athlete.setCertRsoName("RSO Test");
        return athlete;
    }

    private static Equipment sampleEquipment() {
        Equipment equipment = new Equipment();
        equipment.setEquipmentId("EQP-2026-0099");
        equipment.setBorrowerName("Maria L. Santos");
        equipment.setDesignation("Athletics Coordinator");
        equipment.setSchool("San Jose del Monte National High School");
        equipment.setEvent("Athletics");
        equipment.setMobileNo("09171234567");
        equipment.setDate(LocalDate.of(2026, 8, 3));
        equipment.setIssuedBy("Juan Dela Cruz");
        equipment.setIssuedTo("Maria L. Santos");
        Equipment.EquipmentItem item = new Equipment.EquipmentItem();
        item.qty = 2;
        item.unit = "pcs";
        item.makeAndDescription = "Digital stopwatch";
        item.dateBorrowed = LocalDate.of(2026, 8, 3);
        item.remarks = "Good condition";
        equipment.setItems(List.of(item));
        return equipment;
    }

    private static void verifyPdf(Path pdf) throws Exception {
        assertTrue(Files.exists(pdf));
        assertTrue(Files.size(pdf) > 10_000);
        PdfReader reader = new PdfReader(pdf.toString());
        try {
            assertEquals(1, reader.getNumberOfPages());
        } finally {
            reader.close();
        }
    }

    private static void verifyAthleteTemplate(Path pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf.toString());
        try {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(text.contains("AR (ATHLETE RECORD)"));
            assertTrue(text.contains("CITY OF BALIWAG"));
            assertTrue(text.contains("(Last)"));
            assertTrue(text.contains("(First)"));
            assertTrue(text.contains("(M.I.)"));
            assertTrue(text.contains("Grade Level"));
            assertTrue(text.contains("Athlete's Signature over Printed Name"));
            assertFalse(text.contains("Athlete ID"));
        } finally {
            reader.close();
        }
    }
}
