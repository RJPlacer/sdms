package com.sdms.service;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.sdms.model.*;
import com.sdms.sharing.SharedAssetService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerificationPublishTest {
  @Test
  void configuredIdsPublishUrlPayloadsAndDeletionRevokesThem(@TempDir Path appDir)
      throws Exception {
    System.setProperty("sdms.appDir", appDir.toString());
    ArrayList<JsonObject> published = new ArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/records",
        exchange -> {
          assertEquals(
              "Bearer mock-test-key", exchange.getRequestHeaders().getFirst("Authorization"));
          published.add(
              JsonParser.parseString(
                      new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                  .getAsJsonObject());
          exchange.sendResponseHeaders(200, 0);
          exchange.close();
        });
    server.start();
    System.setProperty(
        "sdms.verification.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
    System.setProperty("sdms.verification.apiKey", "mock-test-key");
    DatabaseService db = DatabaseService.getInstance();
    try {
      db.initializeDatabase();
      Athlete athlete = new Athlete();
      athlete.setLastName("Athlete");
      athlete.setFirstName("Test");
      db.saveAthlete(athlete);
      Official official = new Official();
      official.setLastName("Official");
      official.setFirstName("Test");
      db.saveOfficial(official);
      Coach coach = new Coach();
      coach.setLastName("Coach");
      coach.setFirstName("Test");
      db.saveCoach(coach);
      for (String path :
          new String[] {
            QRCodeService.refreshAthleteQRCode(athlete),
            QRCodeService.refreshOfficialQRCode(official),
            QRCodeService.refreshCoachQRCode(coach)
          }) {
        String payload =
            new MultiFormatReader()
                .decode(
                    new BinaryBitmap(
                        new HybridBinarizer(
                            new BufferedImageLuminanceSource(
                                ImageIO.read(SharedAssetService.localPath(path).toFile())))),
                    java.util.Map.of(DecodeHintType.PURE_BARCODE, true))
                .getText();
        assertTrue(
            payload.startsWith(System.getProperty("sdms.verification.baseUrl") + "/verify/"));
      }
      assertEquals(3, published.size());
      assertTrue(published.stream().allMatch(p -> p.get("status").getAsString().equals("ACTIVE")));
      db.deleteAthlete(athlete.getId());
      db.deleteOfficial(official.getId());
      db.deleteCoach(coach.getId());
      assertEquals(6, published.size());
      for (int i = 3; i < 6; i++)
        assertEquals("REVOKED", published.get(i).get("status").getAsString());
    } finally {
      db.closeConnection();
      server.stop(0);
      System.clearProperty("sdms.appDir");
      System.clearProperty("sdms.verification.baseUrl");
      System.clearProperty("sdms.verification.apiKey");
    }
  }
}
