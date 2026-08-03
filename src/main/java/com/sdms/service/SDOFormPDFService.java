package com.sdms.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.sdms.model.Athlete;
import com.sdms.model.Equipment;
import com.sdms.sharing.SharedAssetService;
import com.sdms.util.AppPaths;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Official SDO City of Baliwag print forms with the approved letterhead. */
public final class SDOFormPDFService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final Color BLACK = Color.BLACK;
    private static final Color HEADER_FILL = new Color(242, 244, 247);
    private static final Color LIGHT_FILL = new Color(249, 250, 251);
    private static final List<String> MEET_LEVELS = List.of(
        "School Sports Meet",
        "District/Unit Meet",
        "Division/Provincial Meet",
        "Regional Meet");

    private SDOFormPDFService() {}

    public static String exportAthleteRecord(Athlete athlete) throws IOException, DocumentException {
        Path exports = AppPaths.exportsDir();
        Files.createDirectories(exports);
        String id = safeId(athlete.getAthleteId(), "ATHLETE");
        Path output = exports.resolve("ATHLETE_RECORD_" + id + ".pdf");

        Document document = new Document(PageSize.A4, 34, 34, 132, 100);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(output.toFile()));
        writer.setPageEvent(new SdoLetterhead());
        document.open();

        Font tiny = font(6.5f, Font.NORMAL);
        Font small = font(7.2f, Font.NORMAL);
        Font smallBold = font(7.2f, Font.BOLD);
        Font body = font(8f, Font.NORMAL);
        Font bodyBold = font(8f, Font.BOLD);
        Font section = font(8.5f, Font.BOLD);
        Font title = font(12f, Font.BOLD);

        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        meta.addCell(borderless("Revised as of September 2025", tiny, Element.ALIGN_LEFT));
        meta.addCell(borderless("AR (ATHLETE RECORD)", title, Element.ALIGN_RIGHT));
        meta.setSpacingAfter(4);
        document.add(meta);

        document.add(sectionTitle("A. PERSONAL DATA", section));

        PdfPTable identity = new PdfPTable(2);
        identity.setWidthPercentage(100);
        identity.setWidths(new float[]{5.2f, 1.25f});

        PdfPTable details = new PdfPTable(6);
        details.setWidthPercentage(100);
        addField(details, "Last Name", athlete.getLastName(), 2, bodyBold, body);
        addField(details, "First Name", athlete.getFirstName(), 2, bodyBold, body);
        addField(details, "M.I.", athlete.getMiddleInitial(), 2, bodyBold, body);
        addField(details, "Sex", athlete.getSex(), 2, bodyBold, body);
        addField(details, "Learner Reference Number (LRN)", athlete.getLearnerRefNumber(), 2, bodyBold, body);
        addField(details, "Contact Number", athlete.getContactNo(), 2, bodyBold, body);
        addField(details, "Date of Birth", date(athlete.getDateOfBirth()), 2, bodyBold, body);
        addField(details, "Age", athlete.getAge() > 0 ? Integer.toString(athlete.getAge()) : "", 1, bodyBold, body);
        addField(details, "Place of Birth", athlete.getPlaceOfBirth(), 3, bodyBold, body);
        addField(details, "School", athlete.getSchool(), 4, bodyBold, body);
        addField(details, "Grade Level", athlete.getGradeLevel(), 2, bodyBold, body);
        addField(details, "Address of School", athlete.getSchoolAddress(), 6, bodyBold, body);
        addField(details, "Present Address", athlete.getPresentAddress(), 6, bodyBold, body);
        addField(details, "Father's Name", athlete.getFatherName(), 3, bodyBold, body);
        addField(details, "Mother/Guardian", athlete.getMotherName(), 3, bodyBold, body);
        addField(details, "Address of Parents/Guardian", athlete.getGuardianAddress(), 6, bodyBold, body);

        PdfPCell detailCell = new PdfPCell();
        detailCell.setPadding(0);
        detailCell.addElement(details);
        identity.addCell(detailCell);
        identity.addCell(photoCell(athlete.getPhotoPath(), tiny));
        identity.setSpacingAfter(5);
        document.add(identity);

        Paragraph participation = sectionTitle(
            "B. Participation in the previous Palarong Pambansa:  Yes _____  No _____  "
                + "If Yes, kindly fill up the table below",
            section);
        document.add(participation);
        PdfPTable previous = dataTable(new float[]{1.1f, 2.1f, 2f, 1.5f}, smallBold,
            "Year of Participation", "Sports Event", "Venue", "Remarks");
        List<Athlete.PalaroPrevious> previousRows = athlete.getPreviousPalaroRecords();
        int previousCount = 4;
        for (int i = 0; i < previousCount; i++) {
            Athlete.PalaroPrevious row = previousRows != null && i < previousRows.size() ? previousRows.get(i) : null;
            addDataRow(previous, small, 15,
                row == null ? "" : row.year,
                row == null ? "" : row.sportsEvent,
                row == null ? "" : row.venue,
                row == null ? "" : row.remarks);
        }
        previous.setSpacingAfter(5);
        document.add(previous);

        document.add(sectionTitle("C. ATHLETE'S PARTICIPATION IN THE LOWER MEETS (CURRENT SCHOOL YEAR)", section));
        PdfPTable lower = dataTable(new float[]{1.4f, 2f, 2f, 1.4f}, smallBold,
            "Inclusive Dates", "Sports Event", "Athletic Meet", "Remarks");
        List<Athlete.LowerMeet> lowerRows = athlete.getLowerMeetRecords();
        for (String meetLevel : MEET_LEVELS) {
            Athlete.LowerMeet row = findLowerMeet(lowerRows, meetLevel);
            addDataRow(lower, small, 15,
                row == null ? "" : row.inclusiveDates,
                row == null ? "" : row.sportsEvent,
                meetLevel,
                row == null ? "" : row.remarks);
        }
        lower.setSpacingAfter(1);
        document.add(lower);

        PdfPTable athleteSignature = new PdfPTable(2);
        athleteSignature.setWidthPercentage(100);
        athleteSignature.setWidths(new float[]{1.2f, 1f});
        athleteSignature.addCell(borderless("(Use separate sheet if necessary.)", tiny, Element.ALIGN_LEFT));
        PdfPCell signature = borderless(
            "_______________________________\nAthlete's Signature over Printed Name",
            small, Element.ALIGN_CENTER);
        signature.setPaddingTop(2);
        signature.setPaddingBottom(3);
        athleteSignature.addCell(signature);
        document.add(athleteSignature);

        document.add(sectionTitle("D. CERTIFICATION ON ATHLETE'S PARTICIPATION", section));
        Paragraph certification = new Paragraph(
            "This is to certify that, based on our knowledge, the above-mentioned athlete has been a member "
                + "of a school-based sports club and has participated in the lower meets.", small);
        certification.setLeading(9f);
        certification.setSpacingAfter(3);
        document.add(certification);

        PdfPTable cert = dataTable(new float[]{1.3f, 2f, 1.6f, 1.6f}, smallBold,
            "Meet", "Name and Signature of Coach", "Division Sports Officer (DSO)", "Regional Sports Officer (RSO)");
        for (String meetLevel : MEET_LEVELS) {
            boolean selected = sameMeet(athlete.getCertMeet(), meetLevel);
            addDataRow(cert, small, 15, meetLevel,
                selected ? athlete.getCertCoachName() : "",
                selected ? athlete.getCertDsoName() : "",
                selected ? athlete.getCertRsoName() : "");
        }
        cert.setSpacingAfter(2);
        document.add(cert);

        Paragraph certificationNote = new Paragraph("(Use separate sheet if necessary.)", tiny);
        certificationNote.setSpacingAfter(2);
        document.add(certificationNote);

        document.add(new Paragraph("Screened by:", bodyBold));
        document.add(signatureTable(small,
            "Division Meet\n(Signature of DSAC over Printed Name)",
            "Regional Meet\n(Signature of RSAC over Printed Name)",
            "Palarong Pambansa\n(Signature of NSAC over Printed Name)"));

        document.close();
        return output.toString();
    }

    public static String exportEquipmentMemorandum(Equipment equipment) throws IOException, DocumentException {
        Path exports = AppPaths.exportsDir();
        Files.createDirectories(exports);
        String id = safeId(equipment.getEquipmentId(), "EQUIPMENT");
        Path output = exports.resolve("MEMORANDUM_RECEIPT_" + id + ".pdf");

        Document document = new Document(PageSize.A4, 34, 34, 132, 100);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(output.toFile()));
        writer.setPageEvent(new SdoLetterhead());
        document.open();

        Font tiny = font(6.5f, Font.NORMAL);
        Font small = font(7.2f, Font.NORMAL);
        Font smallBold = font(7.2f, Font.BOLD);
        Font body = font(8f, Font.NORMAL);
        Font bodyBold = font(8f, Font.BOLD);
        Font title = font(11f, Font.BOLD);

        Paragraph heading = new Paragraph(
            "MEMORANDUM RECEIPT FOR EQUIPMENT, SEMI-EXPENDABLE\nAND NON-EXPENDABLE PROPERTY", title);
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setLeading(13f);
        heading.setSpacingAfter(9);
        document.add(heading);

        PdfPTable borrower = new PdfPTable(3);
        borrower.setWidthPercentage(100);
        borrower.addCell(fieldCell("Name", equipment.getBorrowerName(), bodyBold, body));
        borrower.addCell(fieldCell("Designation", equipment.getDesignation(), bodyBold, body));
        borrower.addCell(fieldCell("School", equipment.getSchool(), bodyBold, body));
        borrower.addCell(fieldCell("Event", equipment.getEvent(), bodyBold, body));
        borrower.addCell(fieldCell("Mobile No.", equipment.getMobileNo(), bodyBold, body));
        borrower.addCell(fieldCell("Date", date(equipment.getDate()), bodyBold, body));
        borrower.setSpacingAfter(8);
        document.add(borrower);

        PdfPTable items = dataTable(new float[]{0.7f, 0.9f, 3.4f, 1.25f, 1.25f, 1.5f}, smallBold,
            "QTY", "UNIT", "MAKE AND DESCRIPTION", "DATE BORROWED", "DATE RETURNED", "REMARKS");
        List<Equipment.EquipmentItem> rows = equipment.getItems();
        int rowCount = Math.max(9, rows == null ? 0 : rows.size());
        for (int i = 0; i < rowCount; i++) {
            Equipment.EquipmentItem item = rows != null && i < rows.size() ? rows.get(i) : null;
            addDataRow(items, small, 26,
                item == null || item.qty == 0 ? "" : Integer.toString(item.qty),
                item == null ? "" : item.unit,
                item == null ? "" : item.makeAndDescription,
                item == null ? "" : date(item.dateBorrowed),
                item == null ? "" : date(item.dateReturned),
                item == null ? "" : item.remarks);
        }
        items.setSpacingAfter(8);
        document.add(items);

        Paragraph acknowledgement = new Paragraph(
            "I acknowledge having received the equipment, supplies, and items listed above in good condition.",
            body);
        acknowledgement.setAlignment(Element.ALIGN_CENTER);
        acknowledgement.setSpacingAfter(6);
        document.add(acknowledgement);

        PdfPTable signatures = new PdfPTable(2);
        signatures.setWidthPercentage(100);
        signatures.addCell(signatureCell("ISSUED BY", equipment.getIssuedBy(), smallBold, small));
        signatures.addCell(signatureCell("ISSUED TO", equipment.getIssuedTo(), smallBold, small));
        signatures.setSpacingAfter(5);
        document.add(signatures);

        PdfPTable remarks = new PdfPTable(1);
        remarks.setWidthPercentage(100);
        PdfPCell remarksCell = new PdfPCell(new Phrase("REMARKS:", bodyBold));
        remarksCell.setPadding(5);
        remarksCell.setMinimumHeight(48);
        remarks.addCell(remarksCell);
        document.add(remarks);

        Paragraph recordId = new Paragraph("Record ID: " + ns(equipment.getEquipmentId()), tiny);
        recordId.setAlignment(Element.ALIGN_RIGHT);
        recordId.setSpacingBefore(2);
        document.add(recordId);

        document.close();
        return output.toString();
    }

    private static Paragraph sectionTitle(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(2);
        paragraph.setSpacingAfter(3);
        return paragraph;
    }

    private static PdfPTable dataTable(float[] widths, Font headerFont, String... headings)
            throws DocumentException {
        PdfPTable table = new PdfPTable(headings.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setHeaderRows(1);
        table.setSplitLate(false);
        for (String heading : headings) {
            PdfPCell cell = new PdfPCell(new Phrase(heading, headerFont));
            cell.setBackgroundColor(HEADER_FILL);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(3);
            cell.setMinimumHeight(19);
            table.addCell(cell);
        }
        return table;
    }

    private static void addDataRow(PdfPTable table, Font font, float height, String... values) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(ns(value), font));
            cell.setPadding(3);
            cell.setMinimumHeight(height);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }
    }

    private static PdfPCell fieldCell(String label, String value, Font labelFont, Font valueFont) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label + ": ", labelFont));
        phrase.add(new Chunk(ns(value), valueFont));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setPadding(4);
        cell.setMinimumHeight(19);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private static void addField(PdfPTable table, String label, String value, int colspan,
                                 Font labelFont, Font valueFont) {
        PdfPCell cell = fieldCell(label, value, labelFont, valueFont);
        cell.setColspan(colspan);
        table.addCell(cell);
    }

    private static Athlete.LowerMeet findLowerMeet(List<Athlete.LowerMeet> rows, String meetLevel) {
        if (rows == null) return null;
        for (Athlete.LowerMeet row : rows) {
            if (row != null && sameMeet(row.athleticMeet, meetLevel)) return row;
        }
        return null;
    }

    private static boolean sameMeet(String value, String meetLevel) {
        String valueKey = meetKey(value);
        String levelKey = meetKey(meetLevel);
        if (valueKey.isBlank()) return false;
        if (valueKey.equals(levelKey)) return true;
        if (levelKey.startsWith("school")) return valueKey.contains("school");
        if (levelKey.startsWith("district")) {
            return valueKey.contains("district") || valueKey.contains("unit");
        }
        if (levelKey.startsWith("division")) {
            return valueKey.contains("division") || valueKey.contains("provincial");
        }
        return levelKey.startsWith("regional") && valueKey.contains("regional");
    }

    private static String meetKey(String value) {
        return ns(value).toLowerCase().replaceAll("[^a-z]", "");
    }

    private static PdfPCell photoCell(String photoPath, Font hintFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(4);
        cell.setMinimumHeight(114);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (photoPath != null && !photoPath.isBlank()) {
            try {
                Path local = SharedAssetService.localPath(photoPath);
                Image image = Image.getInstance(local.toString());
                image.scaleToFit(78, 96);
                image.setAlignment(Image.ALIGN_CENTER);
                cell.addElement(image);
                return cell;
            } catch (Exception ignored) {}
        }
        Paragraph hint = new Paragraph("Latest 1.5 x 1.5 inches picture", hintFont);
        hint.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(hint);
        return cell;
    }

    private static PdfPCell signatureCell(String heading, String name, Font headingFont, Font font) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(5);
        cell.setMinimumHeight(65);
        cell.addElement(new Paragraph(heading + ":", headingFont));
        Paragraph line = new Paragraph("\n" + ns(name) + "\n_______________________________", font);
        line.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(line);
        Paragraph caption = new Paragraph("PRINTED NAME OVER SIGNATURE / DATE", font(6f, Font.NORMAL));
        caption.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(caption);
        return cell;
    }

    private static PdfPTable signatureTable(Font font, String... labels) {
        PdfPTable table = new PdfPTable(labels.length);
        table.setWidthPercentage(100);
        for (String label : labels) {
            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(4);
            Paragraph line = new Paragraph("\n________________________\n" + label + "\nDate: __________", font);
            line.setAlignment(Element.ALIGN_CENTER);
            line.setLeading(8f);
            cell.addElement(line);
            table.addCell(cell);
        }
        return table;
    }

    private static PdfPCell borderless(String value, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(ns(value), font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(0);
        return cell;
    }

    private static Font font(float size, int style) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, BLACK);
    }

    private static String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    private static String safeId(String value, String fallback) {
        String safe = ns(value).replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isBlank() ? fallback : safe;
    }

    private static String ns(String value) {
        return value == null ? "" : value;
    }

    private static final class SdoLetterhead extends PdfPageEventHelper {
        private final byte[] kagawaran = resource("/images/kagawaran-ng-edukasyon-logo.png");
        private final byte[] deped = resource("/images/DepED-logo.png");
        private final byte[] bagongPilipinas = resource("/images/bagong-pilipinas-logo.png");
        private final byte[] sdoSeal = resource("/images/SDO-Seal.png");

        @Override public void onEndPage(PdfWriter writer, Document document) {
            try {
                drawHeader(writer.getDirectContent(), document);
                drawFooter(writer.getDirectContent(), document);
            } catch (Exception e) {
                throw new RuntimeException("Could not render the SDO letterhead", e);
            }
        }

        private void drawHeader(PdfContentByte canvas, Document document) throws Exception {
            float center = document.getPageSize().getWidth() / 2f;
            drawImage(canvas, kagawaran, center - 22, 789, 44, 44);

            show(canvas, "Republic of the Philippines", font(8.5f, Font.NORMAL), center, 782, Element.ALIGN_CENTER);
            show(canvas, "Department of Education", FontFactory.getFont(FontFactory.TIMES_BOLD, 14, BLACK), center, 768, Element.ALIGN_CENTER);
            show(canvas, "REGION III-CENTRAL LUZON", font(8f, Font.NORMAL), center, 756, Element.ALIGN_CENTER);
            show(canvas, "SCHOOLS DIVISION OF CITY OF BALIWAG", font(10.5f, Font.BOLD), center, 743, Element.ALIGN_CENTER);
            show(canvas, "City of Baliwag, Bulacan", font(9.5f, Font.BOLD), center, 731, Element.ALIGN_CENTER);

            canvas.setColorStroke(BLACK);
            canvas.setLineWidth(1.7f);
            canvas.moveTo(28, 720); canvas.lineTo(567, 720); canvas.stroke();
            canvas.setLineWidth(0.7f);
            canvas.moveTo(28, 716); canvas.lineTo(567, 716); canvas.stroke();
        }

        private void drawFooter(PdfContentByte canvas, Document document) throws Exception {
            canvas.setColorStroke(BLACK);
            canvas.setLineWidth(0.7f);
            canvas.moveTo(28, 94); canvas.lineTo(567, 94); canvas.stroke();
            canvas.setLineWidth(1.7f);
            canvas.moveTo(28, 90); canvas.lineTo(567, 90); canvas.stroke();

            drawImage(canvas, deped, 43, 43, 76, 39);
            drawImage(canvas, bagongPilipinas, 127, 39, 49, 49);
            drawImage(canvas, sdoSeal, 184, 39, 47, 47);

            Font address = font(7.2f, Font.NORMAL);
            Font addressBold = font(7.2f, Font.BOLD);
            show(canvas, "2nd Floor, Bayanihan Building, Doña Remedios Trinidad (DRT) Highway,", address, 552, 78, Element.ALIGN_RIGHT);
            show(canvas, "Barangay Pagala, City of Baliwag, Bulacan", address, 552, 68, Element.ALIGN_RIGHT);
            show(canvas, "Telephone No: (044) 762-2793", addressBold, 552, 58, Element.ALIGN_RIGHT);

            Font contact = font(7f, Font.NORMAL);
            show(canvas, "M", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(220, 55, 55)), 248, 34, Element.ALIGN_CENTER);
            show(canvas, "baliwag.city@deped.gov.ph", contact, 258, 32, Element.ALIGN_LEFT);

            canvas.setColorFill(new Color(24, 119, 242));
            canvas.circle(395, 35, 7); canvas.fill();
            show(canvas, "f", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE), 395, 32, Element.ALIGN_CENTER);
            show(canvas, "https://www.facebook.com/SDOCityofBaliwag", contact, 406, 32, Element.ALIGN_LEFT);
        }

        private static void show(PdfContentByte canvas, String text, Font font,
                                 float x, float y, int alignment) {
            ColumnText.showTextAligned(canvas, alignment, new Phrase(text, font), x, y, 0);
        }

        private static void drawImage(PdfContentByte canvas, byte[] data,
                                      float x, float y, float width, float height) throws Exception {
            if (data.length == 0) return;
            Image image = Image.getInstance(data);
            image.scaleToFit(width, height);
            float imageX = x + (width - image.getScaledWidth()) / 2f;
            float imageY = y + (height - image.getScaledHeight()) / 2f;
            image.setAbsolutePosition(imageX, imageY);
            canvas.addImage(image);
        }

        private static byte[] resource(String path) {
            try (InputStream input = SDOFormPDFService.class.getResourceAsStream(path)) {
                if (input == null) return new byte[0];
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                input.transferTo(output);
                return output.toByteArray();
            } catch (IOException e) {
                return new byte[0];
            }
        }
    }
}
