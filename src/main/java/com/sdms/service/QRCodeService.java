package com.sdms.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.sdms.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates QR code PNG images and saves them to a local folder.
 */
public class QRCodeService {

    private static final int QR_SIZE = 300;          // pixels

    public static String generateQRCode(String content, String fileName) throws WriterException, IOException {
        Path qrDir = AppPaths.qrCodesDir();

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

        Path output = qrDir.resolve(fileName + ".png");
        MatrixToImageWriter.writeToPath(matrix, "PNG", output);

        return output.toAbsolutePath().toString();
    }

    /**
     * Builds a structured QR payload for an athlete.
     * The QR decodes to a JSON-like summary useful for scanning at venues.
     */
    public static String buildAthletePayload(String athleteId, String fullName, String school) {
        return "Athlete Track:ATHLETE|ID:%s|NAME:%s|SCHOOL:%s".formatted(athleteId, fullName, school);
    }

    public static String buildOfficialPayload(String officialId, String fullName, String school) {
        return "Athlete Track:OFFICIAL|ID:%s|NAME:%s|SCHOOL:%s".formatted(officialId, fullName, school);
    }

    public static String buildCoachPayload(String coachId, String fullName, String school) {
        return "Athlete Track:COACH|ID:%s|NAME:%s|SCHOOL:%s".formatted(coachId, fullName, school);
    }
}
