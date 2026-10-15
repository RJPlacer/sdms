package com.sdms.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sdms.model.Equipment;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WordExportServiceTest {
  @Test
  void restoredWordExportContainsBorrowerItemsAndSignatories(@TempDir Path appDir)
      throws Exception {
    System.setProperty("sdms.appDir", appDir.toString());
    try {
      Equipment equipment = new Equipment();
      equipment.setEquipmentId("EQP-2026-0099");
      equipment.setBorrowerName("Sample Borrower");
      equipment.setDesignation("Coach");
      equipment.setSchool("Sample School");
      equipment.setEvent("Athletics");
      equipment.setDate(LocalDate.of(2026, 10, 15));
      equipment.setIssuedBy("Sample Custodian");
      equipment.setIssuedTo("Sample Borrower");
      Equipment.EquipmentItem item = new Equipment.EquipmentItem();
      item.qty = 2;
      item.unit = "pieces";
      item.makeAndDescription = "Training stopwatch";
      item.dateBorrowed = LocalDate.of(2026, 10, 15);
      item.remarks = "Return after the meet";
      equipment.setItems(List.of(item));
      Path output = Path.of(WordExportService.exportEquipmentMemorandum(equipment));
      try (XWPFDocument document = new XWPFDocument(Files.newInputStream(output));
          XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
        String text = extractor.getText();
        assertTrue(text.contains("Sample School"));
        assertTrue(text.contains("Training stopwatch"));
        assertTrue(text.contains("SAMPLE CUSTODIAN"));
        assertTrue(text.contains("SAMPLE BORROWER"));
        assertFalse(document.getHeaderList().isEmpty());
        assertFalse(document.getFooterList().isEmpty());
      }
      String samples = System.getProperty("sdms.wordSnapshotDir");
      if (samples != null) {
        Files.createDirectories(Path.of(samples));
        Files.copy(
            output,
            Path.of(samples).resolve("equipment-memorandum.docx"),
            StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      System.clearProperty("sdms.appDir");
    }
  }
}
