package com.sdms.service;

import com.sdms.model.Equipment;
import com.sdms.util.AppPaths;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.TextAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class WordExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String FONT_BODY = "Bookman Old Style";
    private static final String FONT_HEADER_SCRIPT = "Old English Text MT";
    private static final String FONT_HEADER_SANS = "Tahoma";
    private static final int TWIPS_PER_INCH = 1440;
    private static final int EMU_PER_INCH = 914400;
    // A4 width (11906 twips) - left/right margins (0.2in each = 288 twips each) = 11330 twips
    private static final int EQUIPMENT_TABLE_TOTAL_WIDTH = 11330;
    private static final int[] EQUIPMENT_TABLE_WIDTHS = {850, 850, 4430, 1733, 1733, 1734};
    private static final int[] INFO_TABLE_WIDTHS = {3777, 3777, 3776};
    private static final int HEAVY_LINE_SIZE = 24;

    private WordExportService() {}

    public static String exportEquipmentMemorandum(Equipment e) throws Exception {
        Path exportDir = AppPaths.exportsDir();
        Files.createDirectories(exportDir);

        String safeId = safeFilePart(ns(e.getEquipmentId()));
        if (safeId.isBlank()) {
            safeId = "UNKNOWN";
        }

        Path output = exportDir.resolve("EQUIPMENT_MEMORANDUM_" + safeId + "_" + System.currentTimeMillis() + ".docx");

        try (XWPFDocument doc = new XWPFDocument(); OutputStream os = Files.newOutputStream(output)) {
            configureA4Page(doc);

            addHeaderSection(doc);

            addCenteredTitle(doc,
                    "MEMORANDUM RECEIPT FOR EQUIPMENT, SEMI-EXPANDABLE",
                    "AND NON-EXPANDABLE PROPERTY");

            addBorrowerInfoTable(doc, e);
            addItemsTable(doc, e.getItems());
                addAcknowledgementIssuedRemarksTable(doc, e);
            addFooterBranding(doc);

            doc.write(os);
        }

        return output.toString();
    }

    private static void configureA4Page(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();

        CTPageSz pageSize = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(11906));
        pageSize.setH(BigInteger.valueOf(16838));
        pageSize.setOrient(STPageOrientation.PORTRAIT);

        CTPageMar margin = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        margin.setTop(BigInteger.valueOf(inchesToTwips(0.2)));
        margin.setBottom(BigInteger.valueOf(inchesToTwips(0.2)));
        margin.setLeft(BigInteger.valueOf(inchesToTwips(0.2)));
        margin.setRight(BigInteger.valueOf(inchesToTwips(0.2)));
        margin.setHeader(BigInteger.valueOf(inchesToTwips(0.05)));
        margin.setFooter(BigInteger.valueOf(inchesToTwips(0.05)));
    }

    private static void addHeaderSection(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
            ? doc.getDocument().getBody().getSectPr()
            : doc.getDocument().getBody().addNewSectPr();

        XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(doc, sectPr);
        XWPFHeader header = policy.createHeader(STHdrFtr.DEFAULT);

        XWPFParagraph imageParagraph;
        if (header.getParagraphs().isEmpty()) {
            imageParagraph = header.createParagraph();
        } else {
            imageParagraph = header.getParagraphArray(0);
        }
        imageParagraph.setAlignment(ParagraphAlignment.CENTER);
        imageParagraph.setSpacingBefore(0);
        imageParagraph.setSpacingAfter(0);
        addImageRun(imageParagraph, "/images/kagawaran-ng-edukasyon-logo.png", "kagawaran-ng-edukasyon-logo.png", 0.83, 0.83);

        XWPFParagraph text = header.createParagraph();
        text.setAlignment(ParagraphAlignment.CENTER);
        text.setSpacingBefore(0);
        text.setSpacingAfter(0);

        XWPFRun runScript = text.createRun();
        runScript.setFontFamily(FONT_HEADER_SCRIPT);
        runScript.setFontSize(11);
        runScript.setText("Republic of the Philippines");
        runScript.addBreak();
        runScript.setText("Department of Education");
        runScript.addBreak();

        XWPFRun runSans = text.createRun();
        runSans.setFontFamily(FONT_HEADER_SANS);
        runSans.setFontSize(10);
        runSans.setBold(false);
        runSans.setText("Region III-Central Luzon");
        runSans.addBreak();
        runSans.setText("SCHOOLS DIVISION OFFICE OF THE CITY OF BALIWAG");

        XWPFParagraph line1 = header.createParagraph();
        setParagraphBottomBorder(line1, HEAVY_LINE_SIZE);
        zeroSpacing(line1);

        XWPFParagraph line2 = header.createParagraph();
        setParagraphBottomBorder(line2, HEAVY_LINE_SIZE);
        zeroSpacing(line2);
    }

    private static void addCenteredTitle(XWPFDocument doc, String line1, String line2) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(180);
        p.setSpacingAfter(160);

        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontFamily(FONT_BODY);
        run.setFontSize(12);
        run.setText(line1);
        run.addBreak();
        run.setText(line2);
    }

    private static void addBorrowerInfoTable(XWPFDocument doc, Equipment e) {
        XWPFTable infoTable = doc.createTable(2, 3);
        configureFixedTableLayout(infoTable, INFO_TABLE_WIDTHS);
        infoTable.setTableAlignment(TableRowAlign.CENTER);

        fillInfoCell(infoTable.getRow(0).getCell(0), "Name:", e.getBorrowerName());
        fillInfoCell(infoTable.getRow(0).getCell(1), "Designation:", e.getDesignation());
        fillInfoCell(infoTable.getRow(0).getCell(2), "School:", e.getSchool());

        fillInfoCell(infoTable.getRow(1).getCell(0), "Event:", e.getEvent());
        fillInfoCell(infoTable.getRow(1).getCell(1), "Mobile No:", e.getMobileNo());
        fillInfoCell(infoTable.getRow(1).getCell(2), "Date:", formatDate(e));

        setRowWidths(infoTable.getRow(0), INFO_TABLE_WIDTHS);
        setRowWidths(infoTable.getRow(1), INFO_TABLE_WIDTHS);

        XWPFParagraph spacer = doc.createParagraph();
        spacer.setSpacingAfter(0);
    }

    private static void fillInfoCell(XWPFTableCell cell, String label, String value) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setSpacingBefore(40);
        p.setSpacingAfter(40);

        XWPFRun labelRun = p.createRun();
        labelRun.setBold(true);
        labelRun.setFontFamily(FONT_BODY);
        labelRun.setFontSize(10);
        labelRun.setText(label + " ");

        XWPFRun valueRun = p.createRun();
        valueRun.setFontFamily(FONT_BODY);
        valueRun.setFontSize(10);
        valueRun.setText(ns(value));
    }

    private static void addItemsTable(XWPFDocument doc, List<Equipment.EquipmentItem> items) {
        XWPFTable table = doc.createTable(1, 6);
        configureFixedTableLayout(table, EQUIPMENT_TABLE_WIDTHS);
        table.setTableAlignment(TableRowAlign.CENTER);

        String[] headers = {
                "QTY",
                "UNIT",
                "MAKE AND DESCRIPTION",
            "DATE\nBORROWED",
            "DATE\nRETURNED",
                "REMARKS"
        };

        XWPFTableRow headerRow = table.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            XWPFTableCell cell = headerRow.getCell(i);
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            cell.removeParagraph(0);
            XWPFParagraph p = cell.addParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            p.setSpacingBefore(0);
            p.setSpacingAfter(0);
            XWPFRun run = p.createRun();
            run.setBold(true);
            run.setFontFamily(FONT_BODY);
            run.setFontSize((i == 3 || i == 4) ? 8 : 10);
            String[] headerLines = headers[i].split("\\n", -1);
            for (int line = 0; line < headerLines.length; line++) {
                if (line > 0) {
                    run.addBreak();
                }
                run.setText(headerLines[line]);
            }
        }
        setRowWidths(headerRow, EQUIPMENT_TABLE_WIDTHS);

        int minRows = 10;
        int usedRows = 0;

        if (items != null) {
            for (Equipment.EquipmentItem item : items) {
                XWPFTableRow row = table.createRow();
                writeItemCell(row.getCell(0), String.valueOf(item.qty), ParagraphAlignment.CENTER);
                writeItemCell(row.getCell(1), ns(item.unit), ParagraphAlignment.CENTER);
                writeItemCell(row.getCell(2), ns(item.makeAndDescription), ParagraphAlignment.LEFT);
                writeItemCell(row.getCell(3), item.dateBorrowed != null ? item.dateBorrowed.format(DATE_FMT) : "", ParagraphAlignment.CENTER);
                writeItemCell(row.getCell(4), item.dateReturned != null ? item.dateReturned.format(DATE_FMT) : "", ParagraphAlignment.CENTER);
                writeItemCell(row.getCell(5), ns(item.remarks), ParagraphAlignment.LEFT);
                setRowWidths(row, EQUIPMENT_TABLE_WIDTHS);
                usedRows++;
            }
        }

        while (usedRows < minRows) {
            XWPFTableRow row = table.createRow();
            for (int i = 0; i < 6; i++) {
                writeItemCell(row.getCell(i), "", i < 2 || i == 3 || i == 4
                        ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
            }
            setRowWidths(row, EQUIPMENT_TABLE_WIDTHS);
            usedRows++;
        }
    }

    private static void writeItemCell(XWPFTableCell cell, String text, ParagraphAlignment align) {
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(align);
        p.setSpacingBefore(30);
        p.setSpacingAfter(30);
        XWPFRun run = p.createRun();
        run.setFontFamily(FONT_BODY);
        run.setFontSize(10);
        run.setText(ns(text));
    }

    private static void addAcknowledgementIssuedRemarksTable(XWPFDocument doc, Equipment e) {
        // 3 rows: acknowledgement, issued-by/to (combined label + signature), remarks
        XWPFTable table = doc.createTable(3, 2);
        configureFixedTableLayout(table, new int[] { EQUIPMENT_TABLE_TOTAL_WIDTH / 2, EQUIPMENT_TABLE_TOTAL_WIDTH / 2 });
        table.setTableAlignment(TableRowAlign.CENTER);

        // Row 0: Acknowledgement (full width, merged)
        XWPFTableRow ackRow = table.getRow(0);
        XWPFTableCell ackCell = ackRow.getCell(0);
        ackCell.removeParagraph(0);
        XWPFParagraph ack = ackCell.addParagraph();
        ack.setAlignment(ParagraphAlignment.CENTER);
        ack.setSpacingBefore(120);
        ack.setSpacingAfter(120);
        XWPFRun ackRun = ack.createRun();
        ackRun.setFontFamily(FONT_BODY);
        ackRun.setFontSize(10);
        ackRun.setText("I acknowledge to have received this _____ day of _______________, all sports");
        ackRun.addBreak();
        ackRun.setText("equipment/supplies/item in good condition.");
        setCellWidth(ackCell, EQUIPMENT_TABLE_TOTAL_WIDTH);
        setCellGridSpan(ackCell, 2);
        ackRow.removeCell(1);

        // Row 1: ISSUED BY (left) | ISSUED TO (right) — label + underline + caption all in one cell each
        XWPFTableRow issuedRow = table.getRow(1);
        setRowWidths(issuedRow, new int[] { EQUIPMENT_TABLE_TOTAL_WIDTH / 2, EQUIPMENT_TABLE_TOTAL_WIDTH / 2 });

        // --- ISSUED BY cell ---
        XWPFTableCell byCell = issuedRow.getCell(0);
        byCell.removeParagraph(0);

        // Label
        XWPFParagraph byLabel = byCell.addParagraph();
        byLabel.setAlignment(ParagraphAlignment.LEFT);
        byLabel.setSpacingAfter(60);
        byLabel.setIndentationRight(120);
        XWPFRun byLabelRun = byLabel.createRun();
        byLabelRun.setBold(true);
        byLabelRun.setItalic(true);
        byLabelRun.setFontFamily(FONT_BODY);
        byLabelRun.setFontSize(10);
        byLabelRun.setText("ISSUED BY:");

        // Name (if present, not italic)
        if (!ns(e.getIssuedBy()).isBlank()) {
            XWPFParagraph byName = byCell.addParagraph();
            byName.setAlignment(ParagraphAlignment.CENTER);
            byName.setSpacingAfter(0);
            byName.setSpacingBefore(0);
            byName.setIndentationRight(120);
            XWPFRun byNameRun = byName.createRun();
            byNameRun.setFontFamily(FONT_BODY);
            byNameRun.setFontSize(9);
            byNameRun.setText(ns(e.getIssuedBy()).toUpperCase(java.util.Locale.ROOT));
        }

        // Signature underline
        XWPFParagraph byLine = byCell.addParagraph();
        byLine.setAlignment(ParagraphAlignment.CENTER);
        byLine.setSpacingBefore(5);
        byLine.setSpacingAfter(0);
        byLine.setIndentationRight(120);
        XWPFRun byLineRun = byLine.createRun();
        byLineRun.setBold(true);
        byLineRun.setFontFamily(FONT_BODY);
        byLineRun.setFontSize(10);
        byLineRun.setText("____________________________");

        // Caption — tight to the underline
        XWPFParagraph byPrinted = byCell.addParagraph();
        byPrinted.setAlignment(ParagraphAlignment.CENTER);
        byPrinted.setSpacingBefore(0);
        byPrinted.setSpacingAfter(120);
        byPrinted.setIndentationRight(120);
        XWPFRun byPrintedRun = byPrinted.createRun();
        byPrintedRun.setFontFamily(FONT_BODY);
        byPrintedRun.setFontSize(9);
        byPrintedRun.setText("PRINTED NAME OVER SIGNATURE / DATE");

        // --- ISSUED TO cell ---
        XWPFTableCell toCell = issuedRow.getCell(1);
        toCell.removeParagraph(0);

        // Label
        XWPFParagraph toLabel = toCell.addParagraph();
        toLabel.setAlignment(ParagraphAlignment.LEFT);
        toLabel.setSpacingAfter(60);
        toLabel.setIndentationLeft(120);
        XWPFRun toLabelRun = toLabel.createRun();
        toLabelRun.setBold(true);
        toLabelRun.setItalic(true);
        toLabelRun.setFontFamily(FONT_BODY);
        toLabelRun.setFontSize(10);
        toLabelRun.setText("ISSUED TO:");

        // Name (if present, not italic)
        if (!ns(e.getIssuedTo()).isBlank()) {
            XWPFParagraph toName = toCell.addParagraph();
            toName.setAlignment(ParagraphAlignment.CENTER);
            toName.setSpacingAfter(0);
            toName.setSpacingBefore(0);
            toName.setIndentationLeft(120);
            XWPFRun toNameRun = toName.createRun();
            toNameRun.setFontFamily(FONT_BODY);
            toNameRun.setFontSize(9);
            toNameRun.setText(ns(e.getIssuedTo()).toUpperCase(java.util.Locale.ROOT));
        }

        // Signature underline
        XWPFParagraph toLine = toCell.addParagraph();
        toLine.setAlignment(ParagraphAlignment.CENTER);
        toLine.setSpacingBefore(5);
        toLine.setSpacingAfter(0);
        toLine.setIndentationLeft(120);
        XWPFRun toLineRun = toLine.createRun();
        toLineRun.setBold(true);
        toLineRun.setFontFamily(FONT_BODY);
        toLineRun.setFontSize(10);
        toLineRun.setText("____________________________");

        // Caption — tight to the underline
        XWPFParagraph toPrinted = toCell.addParagraph();
        toPrinted.setAlignment(ParagraphAlignment.CENTER);
        toPrinted.setSpacingBefore(0);
        toPrinted.setSpacingAfter(120);
        toPrinted.setIndentationLeft(120);
        XWPFRun toPrintedRun = toPrinted.createRun();
        toPrintedRun.setFontFamily(FONT_BODY);
        toPrintedRun.setFontSize(9);
        toPrintedRun.setText("PRINTED NAME OVER SIGNATURE / DATE");

        // Row 2: REMARKS (full width, merged)
        XWPFTableRow remarksRow = table.getRow(2);
        setRowWidths(remarksRow, new int[] { EQUIPMENT_TABLE_TOTAL_WIDTH / 2, EQUIPMENT_TABLE_TOTAL_WIDTH / 2 });
        XWPFTableCell remarksCell = remarksRow.getCell(0);
        remarksCell.removeParagraph(0);
        XWPFParagraph remarks = remarksCell.addParagraph();
        remarks.setAlignment(ParagraphAlignment.LEFT);
        remarks.setSpacingBefore(120);
        remarks.setSpacingAfter(180);
        XWPFRun remarksRun = remarks.createRun();
        remarksRun.setBold(true);
        remarksRun.setFontFamily(FONT_BODY);
        remarksRun.setFontSize(10);
        remarksRun.setText("REMARKS:");

        XWPFParagraph remarksBlank = remarksCell.addParagraph();
        remarksBlank.setAlignment(ParagraphAlignment.LEFT);
        remarksBlank.setSpacingBefore(120);
        remarksBlank.setSpacingAfter(200);
        XWPFRun remarksBlankRun = remarksBlank.createRun();
        remarksBlankRun.setFontFamily(FONT_BODY);
        remarksBlankRun.setFontSize(10);
        remarksBlankRun.setText(" ");
        setCellWidth(remarksCell, EQUIPMENT_TABLE_TOTAL_WIDTH);
        setCellGridSpan(remarksCell, 2);
        remarksRow.removeCell(1);
    }

    private static void addFooterBranding(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
            ? doc.getDocument().getBody().getSectPr()
            : doc.getDocument().getBody().addNewSectPr();

        XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(doc, sectPr);
        XWPFFooter footerContainer = policy.createFooter(STHdrFtr.DEFAULT);

        XWPFParagraph firstLine;
        if (footerContainer.getParagraphs().isEmpty()) {
            firstLine = footerContainer.createParagraph();
        } else {
            firstLine = footerContainer.getParagraphArray(0);
        }
        setParagraphTopBorder(firstLine, HEAVY_LINE_SIZE);
        zeroSpacing(firstLine);

        XWPFParagraph secondLine = footerContainer.createParagraph();
        setParagraphTopBorder(secondLine, HEAVY_LINE_SIZE);
        zeroSpacing(secondLine);

        XWPFTable footer = footerContainer.createTable(1, 2);
        footer.setWidth("100%");
        footer.setTableAlignment(TableRowAlign.CENTER);
        footer.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        footer.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        footer.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        footer.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        footer.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        footer.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");

        XWPFTableCell logosCell = footer.getRow(0).getCell(0);
        setCellWidth(logosCell, 4200);
        logosCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);
        logosCell.removeParagraph(0);
        XWPFParagraph logos = logosCell.addParagraph();
        logos.setAlignment(ParagraphAlignment.LEFT);
        logos.setVerticalAlignment(TextAlignment.TOP);
        logos.setSpacingBefore(0);
        logos.setSpacingAfter(0);

        addImageRun(logos, "/images/DepED-logo.png", "DepED-logo.png", 1.02, 0.66);
        addSpacer(logos, " ");
        addImageRun(logos, "/images/bagong-pilipinas-logo.png", "bagong-pilipinas-logo.png", 0.73, 0.66);
        addSpacer(logos, " ");
        addImageRun(logos, "/images/SDO-Seal.png", "SDO-Seal.png", 0.68, 0.66);

        XWPFTableCell textCell = footer.getRow(0).getCell(1);
        setCellWidth(textCell, 7130);
        textCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);
        textCell.removeParagraph(0);
        XWPFParagraph address = textCell.addParagraph();
        address.setAlignment(ParagraphAlignment.LEFT);
        address.setVerticalAlignment(TextAlignment.TOP);
        address.setSpacingBefore(0);
        address.setSpacingAfter(0);

        XWPFRun aLabel = address.createRun();
        aLabel.setFontFamily(FONT_BODY);
        aLabel.setFontSize(8);
        aLabel.setBold(false);
        aLabel.setText("Address: ");

        XWPFRun aValue = address.createRun();
        aValue.setFontFamily(FONT_BODY);
        aValue.setFontSize(8);
        aValue.setBold(true);
        aValue.setText("Baliwag North District Compound, J. Buizon St. Poblacion, City of Baliwag, Bulacan");
        aValue.addBreak();

        XWPFRun cLabel = address.createRun();
        cLabel.setFontFamily(FONT_BODY);
        cLabel.setFontSize(8);
        cLabel.setBold(false);
        cLabel.setText("Contact Number: ");

        XWPFRun cValue = address.createRun();
        cValue.setFontFamily(FONT_BODY);
        cValue.setFontSize(8);
        cValue.setBold(true);
        cValue.setText("(044) 816-6041");
        cValue.addBreak();

        XWPFRun eLabel = address.createRun();
        eLabel.setFontFamily(FONT_BODY);
        eLabel.setFontSize(8);
        eLabel.setBold(false);
        eLabel.setText("Email Address: ");

        XWPFRun eValue = address.createRun();
        eValue.setFontFamily(FONT_BODY);
        eValue.setFontSize(8);
        eValue.setBold(true);
        eValue.setText("baliwag.city@deped.gov.ph");
    }

    private static void addImageRun(XWPFParagraph p, String resourcePath, String fileName,
                                    double widthInches, double heightInches) {
        XWPFRun run = p.createRun();
        try (InputStream is = WordExportService.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return;
            }
            run.addPicture(is, Document.PICTURE_TYPE_PNG, fileName,
                    inchesToEmu(widthInches), inchesToEmu(heightInches));
        } catch (Exception ignored) {
            // Keep export generation resilient even when a branding image is invalid/missing.
        }
    }

    private static void addSpacer(XWPFParagraph p, String text) {
        XWPFRun spacer = p.createRun();
        spacer.setText(text);
    }

    private static void setCellWidth(XWPFTableCell cell, int widthTwips) {
        if (cell.getCTTc().getTcPr() == null) {
            cell.getCTTc().addNewTcPr();
        }
        if (!cell.getCTTc().getTcPr().isSetTcW()) {
            cell.getCTTc().getTcPr().addNewTcW();
        }
        cell.getCTTc().getTcPr().getTcW().setW(BigInteger.valueOf(widthTwips));
        cell.getCTTc().getTcPr().getTcW().setType(STTblWidth.DXA);
    }

    private static void setRowWidths(XWPFTableRow row, int[] widthsTwips) {
        for (int i = 0; i < widthsTwips.length && i < row.getTableCells().size(); i++) {
            setCellWidth(row.getCell(i), widthsTwips[i]);
        }
    }

    private static void configureFixedTableLayout(XWPFTable table, int[] widthsTwips) {
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr() != null ? ctTbl.getTblPr() : ctTbl.addNewTblPr();

        int total = 0;
        for (int w : widthsTwips) {
            total += w;
        }

        if (!tblPr.isSetTblW()) {
            tblPr.addNewTblW();
        }
        tblPr.getTblW().setType(STTblWidth.DXA);
        tblPr.getTblW().setW(BigInteger.valueOf(total));

        if (!tblPr.isSetTblInd()) {
            tblPr.addNewTblInd();
        }
        tblPr.getTblInd().setType(STTblWidth.DXA);
        tblPr.getTblInd().setW(BigInteger.ZERO);

        CTTblLayoutType layout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);

        CTTblGrid grid = ctTbl.getTblGrid() != null ? ctTbl.getTblGrid() : ctTbl.addNewTblGrid();
        while (grid.sizeOfGridColArray() > 0) {
            grid.removeGridCol(0);
        }
        for (int w : widthsTwips) {
            grid.addNewGridCol().setW(BigInteger.valueOf(w));
        }

        if (table.getNumberOfRows() > 0) {
            setRowWidths(table.getRow(0), widthsTwips);
        }
    }

    private static void setCellGridSpan(XWPFTableCell cell, int span) {
        if (cell.getCTTc().getTcPr() == null) {
            cell.getCTTc().addNewTcPr();
        }
        if (!cell.getCTTc().getTcPr().isSetGridSpan()) {
            cell.getCTTc().getTcPr().addNewGridSpan();
        }
        cell.getCTTc().getTcPr().getGridSpan().setVal(BigInteger.valueOf(span));
    }

    private static void mergeCells(XWPFTableRow row, int fromCol, int toCol) {
        setCellGridSpan(row.getCell(fromCol), toCol - fromCol + 1);
        for (int col = toCol; col > fromCol; col--) {
            row.removeCell(col);
        }
    }

    private static void setParagraphBottomBorder(XWPFParagraph paragraph, int size) {
        CTPPr pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTPBdr bdr = pPr.isSetPBdr() ? pPr.getPBdr() : pPr.addNewPBdr();
        CTBorder bottom = bdr.isSetBottom() ? bdr.getBottom() : bdr.addNewBottom();
        bottom.setVal(STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(size));
        bottom.setColor("000000");
        bottom.setSpace(BigInteger.ZERO);
    }

    private static void setParagraphTopBorder(XWPFParagraph paragraph, int size) {
        CTPPr pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTPBdr bdr = pPr.isSetPBdr() ? pPr.getPBdr() : pPr.addNewPBdr();
        CTBorder top = bdr.isSetTop() ? bdr.getTop() : bdr.addNewTop();
        top.setVal(STBorder.SINGLE);
        top.setSz(BigInteger.valueOf(size));
        top.setColor("000000");
        top.setSpace(BigInteger.ZERO);
    }

    private static void zeroSpacing(XWPFParagraph p) {
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();

        CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
        spacing.setBefore(BigInteger.ZERO);
        spacing.setAfter(BigInteger.ZERO);
        spacing.setLine(BigInteger.valueOf(1));
        spacing.setLineRule(STLineSpacingRule.EXACT);

        if (!pPr.isSetContextualSpacing()) {
            pPr.addNewContextualSpacing();
        }
        pPr.getContextualSpacing().setVal(true);
    }

    private static String formatDate(Equipment e) {
        return e.getDate() != null ? e.getDate().format(DATE_FMT) : "";
    }

    private static int inchesToTwips(double inches) {
        return (int) Math.round(inches * TWIPS_PER_INCH);
    }

    private static int inchesToEmu(double inches) {
        return (int) Math.round(inches * EMU_PER_INCH);
    }

    private static String safeFilePart(String v) {
        return v.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private static String ns(String s) {
        return s != null ? s : "";
    }
}
