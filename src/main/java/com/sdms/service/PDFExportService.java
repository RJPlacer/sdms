package com.sdms.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.sdms.model.Athlete;
import com.sdms.model.Coach;
import com.sdms.model.Equipment;
import com.sdms.model.Official;
import com.sdms.sharing.SharedAssetService;
import com.sdms.util.AppPaths;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PDFExportService {

    private static final Path PDF_DIR = AppPaths.exportsDir();
    private static final Color DEPED_BLUE = new Color(0x1A, 0x56, 0xA0);
    private static final Color LIGHT_GRAY = new Color(0xF0, 0xF0, 0xF0);
    private static final Color DARK_GRAY  = new Color(0x44, 0x44, 0x44);

    // ── ID Card shared layout ─────────────────────────────────────────────────

    private static String exportIdCard(String entityType, String entityId, String fullName,
                                       String idLabel, String school, String extra1Label, String extra1Value,
                                       String contactNo, String photoPath, String qrCodePath)
            throws DocumentException, IOException {
        Files.createDirectories(PDF_DIR);
        String filePath = PDF_DIR.resolve("ID_" + entityId + ".pdf").toString();

        float cardW = 243f, cardH = 153f;
        Document doc = new Document(new Rectangle(cardW, cardH), 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(filePath));
        doc.open();
        PdfContentByte cb = writer.getDirectContent();

        // Header bar
        cb.setColorFill(DEPED_BLUE);
        cb.rectangle(0, cardH - 30, cardW, 30); cb.fill();

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.WHITE);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
            new Phrase("ATHLETE TRACK — PALARONG PAMBANSA", headerFont), cardW/2, cardH-12, 0);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
            new Phrase(entityType.toUpperCase(), headerFont), cardW/2, cardH-22, 0);

        // Photo: center-crop to fill the frame so differently shaped source
        // images do not leave a large blank area inside the border.
        float photoX = 8f, photoY = 54f, photoW = 55f, photoH = 63f;
        cb.setColorFill(new Color(0xF5, 0xF7, 0xFA));
        cb.rectangle(photoX, photoY, photoW, photoH); cb.fill();
        if (photoPath != null && !photoPath.isBlank()) {
            try {
                Path localPhoto = SharedAssetService.localPath(photoPath);
                Image photo = Image.getInstance(localPhoto.toString());
                drawCoverImage(cb, photo, photoX, photoY, photoW, photoH);
            } catch (Exception ignored) {}
        }
        cb.setColorStroke(new Color(0xC7, 0xD2, 0xE0));
        cb.setLineWidth(0.6f);
        cb.rectangle(photoX, photoY, photoW, photoH); cb.stroke();

        // QR Code
        if (qrCodePath != null && !qrCodePath.isBlank()) {
            try {
                Path localQr = SharedAssetService.localPath(qrCodePath);
                Image qr = Image.getInstance(localQr.toString());
                qr.setAbsolutePosition(cardW-55, 28); qr.scaleToFit(50, 50); doc.add(qr);
            } catch (Exception ignored) {}
        }

        // Text fields
        Font nameFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 6, DARK_GRAY);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.BLACK);
        float tx = 70f;

        drawFittedText(cb, ns(fullName), nameFont, tx, 108f, cardW - 8f, 119f, 6f);
        addCardRow(cb, labelFont, valueFont, "ID NO:", entityId, tx, 100f);

        String details = "SCHOOL: " + ns(school);
        if (extra1Label != null && !extra1Label.isBlank()) {
            details += "\n" + extra1Label + ": " + ns(extra1Value);
        }
        drawFittedText(cb, details, valueFont, tx, 76f, cardW - 62f, 96f, 4.5f);

        // Footer strip
        cb.setColorFill(LIGHT_GRAY); cb.rectangle(0, 0, cardW, 16); cb.fill();
        Font footFont = FontFactory.getFont(FontFactory.HELVETICA, 5.5f, DARK_GRAY);
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
            new Phrase("Contact: " + ns(contactNo), footFont), 8, 5, 0);

        doc.close();
        return filePath;
    }

    public static String exportAthleteIdCard(Athlete a) throws DocumentException, IOException {
        return exportIdCard("Athlete", a.getAthleteId(), a.getFullName(),
            "ID NO", a.getSchool(), "SEX", a.getSex(), a.getContactNo(),
            a.getPhotoPath(), a.getQrCodePath());
    }

    public static String exportOfficialIdCard(Official o) throws DocumentException, IOException {
        return exportIdCard("Official", o.getOfficialId(), o.getFullName(),
            "ID NO", o.getSchool(), "POSITION", o.getCurrentPosition(), o.getMobilePhone(),
            o.getPhotoPath(), o.getQrCodePath());
    }

    public static String exportCoachIdCard(Coach c) throws DocumentException, IOException {
        return exportIdCard("Coach", c.getCoachId(), c.getFullName(),
            "ID NO", c.getSchool(), "POSITION", c.getCurrentPosition(), c.getMobilePhone(),
            c.getPhotoPath(), c.getQrCodePath());
    }

    // ── Batch ID cards (8-up on A4) ───────────────────────────────────────────

    public static String exportBatchAthleteIdCards(java.util.List<Athlete> athletes)
            throws DocumentException, IOException {
        Files.createDirectories(PDF_DIR);
        String filePath = PDF_DIR.resolve("BATCH_ATHLETE_IDS.pdf").toString();

        Document doc = new Document(PageSize.A4, 20, 20, 20, 20);
        PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(filePath));
        doc.open();

        float cardW = 243f, cardH = 153f, gapX = 10f, gapY = 10f;
        int col = 0, row = 0;
        float startX = 20, startY = PageSize.A4.getHeight() - 20 - cardH;

        PdfContentByte cb = writer.getDirectContent();

        for (Athlete a : athletes) {
            float x = startX + col * (cardW + gapX);
            float y = startY - row * (cardH + gapY);

            // Draw card outline
            cb.setColorStroke(new Color(0xCC, 0xCC, 0xCC)); cb.setLineWidth(0.5f);
            cb.rectangle(x, y, cardW, cardH); cb.stroke();

            // Header
            cb.setColorFill(DEPED_BLUE);
            cb.rectangle(x, y + cardH - 30, cardW, 30); cb.fill();

            Font hf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6, Color.WHITE);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase("ATHLETE", hf), x + cardW/2, y + cardH - 20, 0);

            Font nf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.BLACK);
            Font lf = FontFactory.getFont(FontFactory.HELVETICA, 5.5f, DARK_GRAY);
            Font vf = FontFactory.getFont(FontFactory.HELVETICA, 6, Color.BLACK);

            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase(a.getFullName(), nf), x + 8, y + cardH - 38, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase("ID: " + ns(a.getAthleteId()), vf), x + 8, y + cardH - 50, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase(ns(a.getSchool()), lf), x + 8, y + cardH - 62, 0);

            // QR
            if (a.getQrCodePath() != null && !a.getQrCodePath().isBlank()) {
                try {
                    Path localQr = SharedAssetService.localPath(a.getQrCodePath());
                    Image qr = Image.getInstance(localQr.toString());
                    qr.setAbsolutePosition(x + cardW - 50, y + 8); qr.scaleToFit(44, 44); doc.add(qr);
                } catch (Exception ignored) {}
            }

            col++;
            if (col >= 2) { col = 0; row++; }
            if (row >= 4) {
                doc.newPage();
                cb = writer.getDirectContent();
                row = 0;
            }
        }

        doc.close();
        return filePath;
    }

    // ── Report: Athlete ───────────────────────────────────────────────────────

    public static String exportAthleteReport(Athlete a) throws DocumentException, IOException {
        Files.createDirectories(PDF_DIR);
        String filePath = PDF_DIR.resolve("REPORT_" + a.getAthleteId() + ".pdf").toString();

        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        PdfWriter.getInstance(doc, new FileOutputStream(filePath));
        doc.open();

        Font titleFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, DEPED_BLUE);
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, DEPED_BLUE);
        Font labelFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
        Font valueFont   = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        Paragraph title = new Paragraph("ATHLETE TRACK — ATHLETE RECORD", titleFont);
        title.setAlignment(Element.ALIGN_CENTER); doc.add(title); doc.add(Chunk.NEWLINE);

        doc.add(new Paragraph("A. Personal Data", sectionFont));
        addRow(doc, labelFont, valueFont, "Athlete ID:", a.getAthleteId());
        addRow(doc, labelFont, valueFont, "Name:", a.getFullName());
        addRow(doc, labelFont, valueFont, "Sex:", a.getSex());
        addRow(doc, labelFont, valueFont, "LRN:", a.getLearnerRefNumber());
        addRow(doc, labelFont, valueFont, "Contact No.:", a.getContactNo());
        addRow(doc, labelFont, valueFont, "Date of Birth:", a.getDateOfBirth() != null ? a.getDateOfBirth().toString() : "");
        addRow(doc, labelFont, valueFont, "Age:", String.valueOf(a.getAge()));
        addRow(doc, labelFont, valueFont, "Place of Birth:", a.getPlaceOfBirth());
        addRow(doc, labelFont, valueFont, "School:", a.getSchool());
        addRow(doc, labelFont, valueFont, "School Address:", a.getSchoolAddress());
        addRow(doc, labelFont, valueFont, "Present Address:", a.getPresentAddress());
        doc.add(Chunk.NEWLINE);

        doc.add(new Paragraph("Guardian Information", sectionFont));
        addRow(doc, labelFont, valueFont, "Mother's Name:", a.getMotherName());
        addRow(doc, labelFont, valueFont, "Father's Name:", a.getFatherName());
        addRow(doc, labelFont, valueFont, "Guardian Address:", a.getGuardianAddress());
        doc.add(Chunk.NEWLINE);

        if (a.getPreviousPalaroRecords() != null && !a.getPreviousPalaroRecords().isEmpty()) {
            doc.add(new Paragraph("B. Previous Palarong Pambansa", sectionFont));
            PdfPTable t = new PdfPTable(4); t.setWidthPercentage(100);
            addTableHeader(t, "Year", "Sports Event", "Venue", "Remarks");
            for (Athlete.PalaroPrevious r : a.getPreviousPalaroRecords()) {
                t.addCell(ns(r.year)); t.addCell(ns(r.sportsEvent));
                t.addCell(ns(r.venue)); t.addCell(ns(r.remarks));
            }
            doc.add(t); doc.add(Chunk.NEWLINE);
        }

        if (a.getLowerMeetRecords() != null && !a.getLowerMeetRecords().isEmpty()) {
            doc.add(new Paragraph("C. Lower Meets (Current School Year)", sectionFont));
            PdfPTable t = new PdfPTable(4); t.setWidthPercentage(100);
            addTableHeader(t, "Inclusive Dates", "Sports Event", "Athletic Meet", "Remarks");
            for (Athlete.LowerMeet r : a.getLowerMeetRecords()) {
                t.addCell(ns(r.inclusiveDates)); t.addCell(ns(r.sportsEvent));
                t.addCell(ns(r.athleticMeet)); t.addCell(ns(r.remarks));
            }
            doc.add(t); doc.add(Chunk.NEWLINE);
        }

        doc.add(new Paragraph("D. Certification", sectionFont));
        addRow(doc, labelFont, valueFont, "Meet:", a.getCertMeet());
        addRow(doc, labelFont, valueFont, "Coach:", a.getCertCoachName());
        addRow(doc, labelFont, valueFont, "DSO:", a.getCertDsoName());
        addRow(doc, labelFont, valueFont, "RSO:", a.getCertRsoName());
        doc.add(Chunk.NEWLINE);
        addSignatureLine(doc, valueFont, "Coach", "DSO", "RSO");

        doc.close();
        return filePath;
    }

    // ── Report: Official ──────────────────────────────────────────────────────

    public static String exportOfficialReport(Official o) throws DocumentException, IOException {
        Files.createDirectories(PDF_DIR);
        String filePath = PDF_DIR.resolve("REPORT_" + o.getOfficialId() + ".pdf").toString();

        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        PdfWriter.getInstance(doc, new FileOutputStream(filePath));
        doc.open();

        Font titleFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, DEPED_BLUE);
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, DEPED_BLUE);
        Font labelFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
        Font valueFont   = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        Paragraph title = new Paragraph("ATHLETE TRACK — OFFICIAL RECORD", titleFont);
        title.setAlignment(Element.ALIGN_CENTER); doc.add(title); doc.add(Chunk.NEWLINE);

        doc.add(new Paragraph("A. Personal Data", sectionFont));
        addRow(doc, labelFont, valueFont, "Official ID:", o.getOfficialId());
        addRow(doc, labelFont, valueFont, "Name:", o.getFullName());
        addRow(doc, labelFont, valueFont, "Sex:", o.getSex());
        addRow(doc, labelFont, valueFont, "Mobile:", o.getMobilePhone());
        addRow(doc, labelFont, valueFont, "Date of Birth:", o.getDateOfBirth() != null ? o.getDateOfBirth().toString() : "");
        addRow(doc, labelFont, valueFont, "Age:", String.valueOf(o.getAge()));
        addRow(doc, labelFont, valueFont, "Place of Birth:", o.getPlaceOfBirth());
        addRow(doc, labelFont, valueFont, "Position:", o.getCurrentPosition());
        addRow(doc, labelFont, valueFont, "Years in Service:", String.valueOf(o.getYearsInService()));
        addRow(doc, labelFont, valueFont, "School:", o.getSchool());
        addRow(doc, labelFont, valueFont, "Employee No.:", o.getEmployeeNumber());
        addRow(doc, labelFont, valueFont, "School Address:", o.getSchoolAddress());
        addRow(doc, labelFont, valueFont, "Present Address:", o.getPresentAddress());
        addRow(doc, labelFont, valueFont, "Emergency Contact:", o.getEmergencyContactName() + "  " + ns(o.getEmergencyContactNo()));
        doc.add(Chunk.NEWLINE);

        if (!o.getEducationalQualifications().isEmpty()) {
            doc.add(new Paragraph("B. Educational Qualifications", sectionFont));
            PdfPTable t = new PdfPTable(5); t.setWidthPercentage(100);
            addTableHeader(t, "Level", "Course", "School", "Year", "Awards");
            for (Official.EducationalQualification eq : o.getEducationalQualifications()) {
                t.addCell(ns(eq.level)); t.addCell(ns(eq.course)); t.addCell(ns(eq.school));
                t.addCell(ns(eq.yearGraduated)); t.addCell(ns(eq.awardsReceived));
            }
            doc.add(t); doc.add(Chunk.NEWLINE);
        }

        if (!o.getSportsTrainings().isEmpty()) {
            doc.add(new Paragraph("C. Sports Training (Last 3 Years)", sectionFont));
            PdfPTable t = new PdfPTable(4); t.setWidthPercentage(100);
            addTableHeader(t, "Title", "Date", "Hours", "Conducted By");
            for (Official.SportsTraining st : o.getSportsTrainings()) {
                t.addCell(ns(st.title)); t.addCell(ns(st.dateOfTraining));
                t.addCell(String.valueOf(st.numberOfHours)); t.addCell(ns(st.conductedBy));
            }
            doc.add(t); doc.add(Chunk.NEWLINE);
        }

        if (!o.getTrackRecords().isEmpty()) {
            doc.add(new Paragraph("D. Sports Track Record", sectionFont));
            PdfPTable t = new PdfPTable(4); t.setWidthPercentage(100);
            addTableHeader(t, "Meet Attended", "Inclusive Dates", "Event", "Awards");
            for (Official.TrackRecord tr : o.getTrackRecords()) {
                t.addCell(ns(tr.athleteMeetAttended)); t.addCell(ns(tr.inclusiveDates));
                t.addCell(ns(tr.event)); t.addCell(ns(tr.awardsReceived));
            }
            doc.add(t);
        }

        doc.close();
        return filePath;
    }

    // ── Report: Coach ─────────────────────────────────────────────────────────

    public static String exportCoachReport(Coach c) throws DocumentException, IOException {
        Files.createDirectories(PDF_DIR);
        String filePath = PDF_DIR.resolve("REPORT_" + c.getCoachId() + ".pdf").toString();

        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        PdfWriter.getInstance(doc, new FileOutputStream(filePath));
        doc.open();

        Font titleFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, DEPED_BLUE);
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, DEPED_BLUE);
        Font labelFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
        Font valueFont   = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        Paragraph title = new Paragraph("ATHLETE TRACK — COACH RECORD", titleFont);
        title.setAlignment(Element.ALIGN_CENTER); doc.add(title); doc.add(Chunk.NEWLINE);

        doc.add(new Paragraph("A. Personal Data", sectionFont));
        addRow(doc, labelFont, valueFont, "Coach ID:", c.getCoachId());
        addRow(doc, labelFont, valueFont, "Name:", c.getFullName());
        addRow(doc, labelFont, valueFont, "Sex:", c.getSex());
        addRow(doc, labelFont, valueFont, "Mobile:", c.getMobilePhone());
        addRow(doc, labelFont, valueFont, "Date of Birth:", c.getDateOfBirth() != null ? c.getDateOfBirth().toString() : "");
        addRow(doc, labelFont, valueFont, "Age:", String.valueOf(c.getAge()));
        addRow(doc, labelFont, valueFont, "Position:", c.getCurrentPosition());
        addRow(doc, labelFont, valueFont, "Years in Service:", String.valueOf(c.getYearsInService()));
        addRow(doc, labelFont, valueFont, "School:", c.getSchool());
        addRow(doc, labelFont, valueFont, "Employee No.:", c.getEmployeeNumber());
        addRow(doc, labelFont, valueFont, "School Address:", c.getSchoolAddress());
        addRow(doc, labelFont, valueFont, "Present Address:", c.getPresentAddress());
        addRow(doc, labelFont, valueFont, "Emergency Contact:", c.getEmergencyContactName() + "  " + ns(c.getEmergencyContactNo()));
        doc.add(Chunk.NEWLINE);

        if (!c.getEducationalQualifications().isEmpty()) {
            doc.add(new Paragraph("B. Educational Qualifications", sectionFont));
            PdfPTable t = new PdfPTable(5); t.setWidthPercentage(100);
            addTableHeader(t, "Level", "Course", "School", "Year", "Awards");
            for (Official.EducationalQualification eq : c.getEducationalQualifications()) {
                t.addCell(ns(eq.level)); t.addCell(ns(eq.course)); t.addCell(ns(eq.school));
                t.addCell(ns(eq.yearGraduated)); t.addCell(ns(eq.awardsReceived));
            }
            doc.add(t); doc.add(Chunk.NEWLINE);
        }

        if (!c.getSportsTrainings().isEmpty()) {
            doc.add(new Paragraph("C. Sports Training (Last 3 Years)", sectionFont));
            PdfPTable t = new PdfPTable(4); t.setWidthPercentage(100);
            addTableHeader(t, "Title", "Date", "Hours", "Conducted By");
            for (Official.SportsTraining st : c.getSportsTrainings()) {
                t.addCell(ns(st.title)); t.addCell(ns(st.dateOfTraining));
                t.addCell(String.valueOf(st.numberOfHours)); t.addCell(ns(st.conductedBy));
            }
            doc.add(t); doc.add(Chunk.NEWLINE);
        }

        if (!c.getTrackRecords().isEmpty()) {
            doc.add(new Paragraph("D. Sports Track Record", sectionFont));
            PdfPTable t = new PdfPTable(4); t.setWidthPercentage(100);
            addTableHeader(t, "Meet Attended", "Inclusive Dates", "Event", "Awards");
            for (Official.TrackRecord tr : c.getTrackRecords()) {
                t.addCell(ns(tr.athleteMeetAttended)); t.addCell(ns(tr.inclusiveDates));
                t.addCell(ns(tr.event)); t.addCell(ns(tr.awardsReceived));
            }
            doc.add(t);
        }

        doc.close();
        return filePath;
    }

    // ── Report: Equipment ─────────────────────────────────────────────────────

    public static String exportEquipmentReport(Equipment e) throws DocumentException, IOException {
        Files.createDirectories(PDF_DIR);
        String filePath = PDF_DIR.resolve("REPORT_" + e.getEquipmentId() + ".pdf").toString();

        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        PdfWriter.getInstance(doc, new FileOutputStream(filePath));
        doc.open();

        Font titleFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, DEPED_BLUE);
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, DEPED_BLUE);
        Font labelFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
        Font valueFont   = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        Paragraph title = new Paragraph("ATHLETE TRACK — EQUIPMENT BORROWING RECORD", titleFont);
        title.setAlignment(Element.ALIGN_CENTER); doc.add(title); doc.add(Chunk.NEWLINE);

        doc.add(new Paragraph("Borrower Information", sectionFont));
        addRow(doc, labelFont, valueFont, "Record ID:", e.getEquipmentId());
        addRow(doc, labelFont, valueFont, "Borrower Name:", e.getBorrowerName());
        addRow(doc, labelFont, valueFont, "Designation:", e.getDesignation());
        addRow(doc, labelFont, valueFont, "School:", e.getSchool());
        addRow(doc, labelFont, valueFont, "Event:", e.getEvent());
        addRow(doc, labelFont, valueFont, "Mobile No.:", e.getMobileNo());
        addRow(doc, labelFont, valueFont, "Date:", e.getDate() != null ? e.getDate().toString() : "");
        doc.add(Chunk.NEWLINE);

        doc.add(new Paragraph("Insurance", sectionFont));
        addRow(doc, labelFont, valueFont, "Issued By:", e.getIssuedBy());
        addRow(doc, labelFont, valueFont, "Issued To:", e.getIssuedTo());
        doc.add(Chunk.NEWLINE);

        if (e.getItems() != null && !e.getItems().isEmpty()) {
            doc.add(new Paragraph("Equipment Items", sectionFont));
            PdfPTable t = new PdfPTable(6); t.setWidthPercentage(100);
            t.setWidths(new float[]{1f, 1.5f, 3f, 2f, 2f, 2f});
            addTableHeader(t, "QTY", "Unit", "Make & Description", "Date Borrowed", "Date Returned", "Remarks");
            for (Equipment.EquipmentItem item : e.getItems()) {
                t.addCell(String.valueOf(item.qty)); t.addCell(ns(item.unit));
                t.addCell(ns(item.makeAndDescription));
                t.addCell(item.dateBorrowed != null ? item.dateBorrowed.toString() : "");
                t.addCell(item.dateReturned != null ? item.dateReturned.toString() : "");
                t.addCell(ns(item.remarks));
            }
            doc.add(t);
        }

        doc.add(Chunk.NEWLINE); doc.add(Chunk.NEWLINE);
        addSignatureLine(doc, valueFont, "Borrower", "Custodian", "Approving Authority");

        doc.close();
        return filePath;
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private static void drawCoverImage(PdfContentByte cb, Image image,
                                       float x, float y, float width, float height)
            throws DocumentException {
        float scale = Math.max(width / image.getWidth(), height / image.getHeight());
        float scaledWidth = image.getWidth() * scale;
        float scaledHeight = image.getHeight() * scale;
        float imageX = x + (width - scaledWidth) / 2f;
        float imageY = y + (height - scaledHeight) / 2f;

        cb.saveState();
        try {
            cb.rectangle(x, y, width, height);
            cb.clip();
            cb.newPath();
            cb.addImage(image, scaledWidth, 0, 0, scaledHeight, imageX, imageY);
        } finally {
            cb.restoreState();
        }
    }

    private static void addCardRow(PdfContentByte cb, Font lf, Font vf,
                                   String label, String value, float x, float y) throws DocumentException {
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(label + " ", lf), x, y, 0);
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(ns(value), vf), x + 30, y, 0);
    }

    private static void addRow(Document doc, Font lf, Font vf,
                                String label, String value) throws DocumentException {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "  ", lf));
        p.add(new Chunk(ns(value), vf));
        p.setSpacingBefore(2);
        doc.add(p);
    }

    private static void addTableHeader(PdfPTable t, String... headers) {
        Font hf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hf));
            cell.setBackgroundColor(DEPED_BLUE);
            cell.setPadding(4);
            t.addCell(cell);
        }
    }

    private static void addSignatureLine(Document doc, Font vf,
                                         String role1, String role2, String role3) throws DocumentException {
        doc.add(Chunk.NEWLINE);
        PdfPTable sig = new PdfPTable(3); sig.setWidthPercentage(100);
        Font sf = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
        Font lf2 = FontFactory.getFont(FontFactory.HELVETICA, 7, DARK_GRAY);
        for (String role : new String[]{role1, role2, role3}) {
            PdfPCell c = new PdfPCell();
            c.addElement(new Paragraph("\n\n_________________________", sf));
            c.addElement(new Paragraph(role, lf2));
            c.setBorder(Rectangle.NO_BORDER); c.setPadding(8);
            sig.addCell(c);
        }
        doc.add(sig);
    }

    private static String ns(String s) { return s != null ? s : ""; }

    // ── Combined batch export: Athletes + Officials + Coaches ─────────────────

    public static String exportBatchIdCards(
            java.util.List<Athlete> athletes,
            java.util.List<Official> officials,
            java.util.List<Coach> coaches) throws DocumentException, IOException {

        Files.createDirectories(PDF_DIR);
        String filePath = PDF_DIR.resolve("BATCH_ALL_IDS_" + System.currentTimeMillis() + ".pdf").toString();

        Document doc = new Document(PageSize.A4, 15, 15, 15, 15);
        PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(filePath));
        doc.open();

        float cardW = 243f, cardH = 153f, gapX = 8f, gapY = 8f;
        float pageW = PageSize.A4.getWidth()  - 30;
        float pageH = PageSize.A4.getHeight() - 30;
        float startX = 15, startY = PageSize.A4.getHeight() - 15 - cardH;
        int col = 0, row = 0, cardsPerRow = 2, rowsPerPage = 4;

        PdfContentByte cb = writer.getDirectContent();

        // Build unified list of card data
        record CardData(String type, String id, String name, String school, String extra, String contact, String photoPath, String qrPath) {}
        java.util.List<CardData> cards = new java.util.ArrayList<>();

        for (Athlete a : athletes)
            cards.add(new CardData("ATHLETE", a.getAthleteId(), a.getFullName(),
                a.getSchool(), "SEX: " + ns(a.getSex()), ns(a.getContactNo()), a.getPhotoPath(), a.getQrCodePath()));
        for (Official o : officials)
            cards.add(new CardData("OFFICIAL", o.getOfficialId(), o.getFullName(),
                o.getSchool(), ns(o.getCurrentPosition()), ns(o.getMobilePhone()), o.getPhotoPath(), o.getQrCodePath()));
        for (Coach c : coaches)
            cards.add(new CardData("COACH", c.getCoachId(), c.getFullName(),
                c.getSchool(), ns(c.getCurrentPosition()), ns(c.getMobilePhone()), c.getPhotoPath(), c.getQrCodePath()));

        Font hf  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6, Color.WHITE);
        Font nf  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.BLACK);
        Font lf  = FontFactory.getFont(FontFactory.HELVETICA, 5.5f, DARK_GRAY);
        Font vf  = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, Color.BLACK);
        Font ff  = FontFactory.getFont(FontFactory.HELVETICA, 5f, DARK_GRAY);

        for (CardData card : cards) {
            float x = startX + col * (cardW + gapX);
            float y = startY - row * (cardH + gapY);

            // Card border
            cb.setColorStroke(new Color(0xCC, 0xCC, 0xCC)); cb.setLineWidth(0.5f);
            cb.rectangle(x, y, cardW, cardH); cb.stroke();

            // Header bar
            cb.setColorFill(DEPED_BLUE);
            cb.rectangle(x, y + cardH - 28, cardW, 28); cb.fill();
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase("ATHLETE TRACK — PALARONG PAMBANSA", hf), x + cardW/2, y + cardH - 11, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase(card.type(), hf), x + cardW/2, y + cardH - 21, 0);

            // Photo box
            cb.setColorStroke(new Color(0xBB, 0xCC, 0xDD)); cb.setLineWidth(0.5f);
            cb.rectangle(x + 6, y + cardH - 90, 52, 62); cb.stroke();
            if (card.photoPath() != null && !card.photoPath().isBlank()) {
                try {
                    Image ph = Image.getInstance(card.photoPath());
                    ph.setAbsolutePosition(x + 6, y + cardH - 90); ph.scaleToFit(52, 62); doc.add(ph);
                } catch (Exception ignored) {}
            }

            // QR Code
            if (card.qrPath() != null && !card.qrPath().isBlank()) {
                try {
                    Image qr = Image.getInstance(card.qrPath());
                    // Keep the QR entirely above the 14-point footer bar.
                    qr.setAbsolutePosition(x + cardW - 52, y + 16); qr.scaleToFit(48, 48); doc.add(qr);
                } catch (Exception ignored) {}
            }

            // Text: preserve complete values by wrapping and shrinking within bounded areas.
            float tx = x + 64;
            float textRight = x + cardW - 6;
            drawFittedText(cb, card.name(), nf,
                tx, y + 108, textRight, y + 122, 5.5f);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase("ID: " + ns(card.id()), vf), tx, y + 98, 0);
            String details = ns(card.extra());
            if (!details.isBlank() && !ns(card.school()).isBlank()) details += "\n";
            details += ns(card.school());
            drawFittedText(cb, details, lf,
                tx, y + 66, textRight, y + 95, 4.5f);

            // Footer
            cb.setColorFill(LIGHT_GRAY); cb.rectangle(x, y, cardW, 14); cb.fill();
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase("Contact: " + card.contact(), ff), x + 6, y + 4, 0);

            col++;
            if (col >= cardsPerRow) { col = 0; row++; }
            if (row >= rowsPerPage) {
                doc.newPage(); cb = writer.getDirectContent(); row = 0;
            }
        }

        doc.close();
        return filePath;
    }

    private static void drawFittedText(PdfContentByte cb, String value, Font font,
            float left, float bottom, float right, float top, float minFontSize)
            throws DocumentException {
        String text = ns(value);
        float size = font.getSize();
        Font fitted = font;

        while (size >= minFontSize) {
            fitted = new Font(font.getBaseFont(), size, font.getStyle(), font.getColor());
            ColumnText simulation = new ColumnText(cb);
            simulation.setSimpleColumn(new Phrase(text, fitted), left, bottom, right, top,
                size + 1f, Element.ALIGN_LEFT);
            int status = simulation.go(true);
            if (!ColumnText.hasMoreText(status)) break;
            size -= 0.5f;
        }

        ColumnText output = new ColumnText(cb);
        output.setSimpleColumn(new Phrase(text, fitted), left, bottom, right, top,
            fitted.getSize() + 1f, Element.ALIGN_LEFT);
        output.go();
    }

    // ── Delegation Roster PDF ─────────────────────────────────────────────────

    public static String exportDelegationRoster(
            String schoolLabel,
            java.util.List<Athlete> athletes,
            java.util.List<Official> officials,
            java.util.List<Coach> coaches) throws DocumentException, IOException {

        Files.createDirectories(PDF_DIR);
        String safe = schoolLabel.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String filePath = PDF_DIR.resolve("ROSTER_" + safe + "_" + System.currentTimeMillis() + ".pdf").toString();

        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(filePath));

        // Page header/footer on every page
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override public void onEndPage(PdfWriter w, Document d) {
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Font f = FontFactory.getFont(FontFactory.HELVETICA, 8, DARK_GRAY);
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                        new Phrase("Athlete Track — Delegation Roster | " + schoolLabel, f),
                        d.getPageSize().getWidth()/2, 30, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                        new Phrase("Page " + w.getPageNumber(), f),
                        d.right(), 30, 0);
                } catch (Exception ignored) {}
            }
        });

        doc.open();

        Font titleFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, DEPED_BLUE);
        Font schoolFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, DEPED_BLUE);
        Font cellFont    = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        Font headerFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

        // Title
        Paragraph title = new Paragraph("PALARONG PAMBANSA — DELEGATION ROSTER", titleFont);
        title.setAlignment(Element.ALIGN_CENTER); doc.add(title);
        Paragraph sub = new Paragraph(schoolLabel, schoolFont);
        sub.setAlignment(Element.ALIGN_CENTER); sub.setSpacingBefore(4); doc.add(sub);
        doc.add(Chunk.NEWLINE);

        int totalMembers = athletes.size() + officials.size() + coaches.size();
        Font summaryFont = FontFactory.getFont(FontFactory.HELVETICA, 9, DARK_GRAY);
        doc.add(new Paragraph("Total Members: " + totalMembers +
            "   (Athletes: " + athletes.size() +
            "  |  Officials: " + officials.size() +
            "  |  Coaches: " + coaches.size() + ")", summaryFont));
        doc.add(Chunk.NEWLINE);

        // ── Athletes ──
        if (!athletes.isEmpty()) {
            doc.add(new Paragraph("ATHLETES (" + athletes.size() + ")", sectionFont));
            PdfPTable t = new PdfPTable(5);
            t.setWidthPercentage(100);
            t.setWidths(new float[]{1.2f, 3f, 1f, 1f, 2.5f});
            t.setSpacingBefore(4);
            addRosterHeader(t, headerFont, "Athlete ID", "Name", "Sex", "Age", "School");
            for (int i = 0; i < athletes.size(); i++) {
                Athlete a = athletes.get(i);
                Color bg = i % 2 == 0 ? Color.WHITE : new Color(0xF8, 0xFA, 0xFF);
                addRosterRow(t, cellFont, bg,
                    ns(a.getAthleteId()), a.getFullName(),
                    ns(a.getSex()), String.valueOf(a.getAge()), ns(a.getSchool()));
            }
            doc.add(t);
            doc.add(Chunk.NEWLINE);
        }

        // ── Officials ──
        if (!officials.isEmpty()) {
            doc.add(new Paragraph("OFFICIALS (" + officials.size() + ")", sectionFont));
            PdfPTable t = new PdfPTable(5);
            t.setWidthPercentage(100);
            t.setWidths(new float[]{1.2f, 3f, 1f, 2f, 2f});
            t.setSpacingBefore(4);
            addRosterHeader(t, headerFont, "Official ID", "Name", "Sex", "Position", "School");
            for (int i = 0; i < officials.size(); i++) {
                Official o = officials.get(i);
                Color bg = i % 2 == 0 ? Color.WHITE : new Color(0xF0, 0xFB, 0xF5);
                addRosterRow(t, cellFont, bg,
                    ns(o.getOfficialId()), o.getFullName(),
                    ns(o.getSex()), ns(o.getCurrentPosition()), ns(o.getSchool()));
            }
            doc.add(t);
            doc.add(Chunk.NEWLINE);
        }

        // ── Coaches ──
        if (!coaches.isEmpty()) {
            doc.add(new Paragraph("COACHES (" + coaches.size() + ")", sectionFont));
            PdfPTable t = new PdfPTable(5);
            t.setWidthPercentage(100);
            t.setWidths(new float[]{1.2f, 3f, 1f, 2f, 2f});
            t.setSpacingBefore(4);
            addRosterHeader(t, headerFont, "Coach ID", "Name", "Sex", "Position", "School");
            for (int i = 0; i < coaches.size(); i++) {
                Coach c = coaches.get(i);
                Color bg = i % 2 == 0 ? Color.WHITE : new Color(0xFF, 0xFB, 0xF0);
                addRosterRow(t, cellFont, bg,
                    ns(c.getCoachId()), c.getFullName(),
                    ns(c.getSex()), ns(c.getCurrentPosition()), ns(c.getSchool()));
            }
            doc.add(t);
        }

        doc.close();
        return filePath;
    }

    private static void addRosterHeader(PdfPTable t, Font f, String... cols) {
        for (String col : cols) {
            PdfPCell cell = new PdfPCell(new Phrase(col, f));
            cell.setBackgroundColor(DEPED_BLUE);
            cell.setPadding(5); cell.setBorderColor(new Color(0xCC, 0xCC, 0xCC));
            t.addCell(cell);
        }
    }

    private static void addRosterRow(PdfPTable t, Font f, Color bg, String... vals) {
        for (String val : vals) {
            PdfPCell cell = new PdfPCell(new Phrase(val, f));
            cell.setBackgroundColor(bg);
            cell.setPadding(4); cell.setBorderColor(new Color(0xDD, 0xDD, 0xDD));
            t.addCell(cell);
        }
    }
}
