package com.sdms.sharing;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.sdms.model.*;
import com.sdms.service.DatabaseService;
import com.sdms.util.PasswordUtil;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecureSharingTest {
  @Test
  void permissionsAreCheckedByHostAndChangedRolesTakeEffect(@TempDir Path appDir) throws Exception {
    System.setProperty("sdms.appDir", appDir.toString());
    System.setProperty("sdms.disableAutoBackup", "true");
    DatabaseService db = DatabaseService.getInstance();
    SharingManager sharing = SharingManager.getInstance();
    try {
      db.initializeDatabase();
      User admin = user("test-admin", "ADMIN"),
          viewer = user("test-viewer", "VIEWER"),
          encoder = user("test-encoder", "ENCODER");
      db.saveUser(admin);
      db.saveUser(viewer);
      db.saveUser(encoder);
      sharing.enableHost("Secure Test Host");
      SharingConfig paired =
          PairingClient.pair(
              "127.0.0.1",
              sharing.getConfig().getPairingPort(),
              sharing.getConfig().getPairingCode());
      assertTrue(paired.getDatabasePassword().isBlank());
      assertTrue(paired.getDatabaseUser().isBlank());
      JsonObject read = request("getAllAthletes", new JsonArray());
      assertEquals(401, post(paired, "/rpc", read, "").statusCode());
      String viewerSession = login(paired, viewer.getUsername());
      assertEquals(200, post(paired, "/rpc", read, viewerSession).statusCode());
      Athlete athlete = new Athlete();
      athlete.setFirstName("Secure");
      athlete.setLastName("Record");
      JsonArray saveArgs = new JsonArray();
      saveArgs.add(WireCodec.encode(athlete));
      assertEquals(
          403, post(paired, "/rpc", request("saveAthlete", saveArgs), viewerSession).statusCode());
      JsonArray elevate = new JsonArray();
      viewer.setRole("ADMIN");
      elevate.add(WireCodec.encode(viewer));
      assertEquals(
          403, post(paired, "/rpc", request("saveUser", elevate), viewerSession).statusCode());
      assertEquals(
          403,
          post(paired, "/rpc", request("maintenanceConnection", new JsonArray()), viewerSession)
              .statusCode());
      JsonArray table = new JsonArray();
      table.add("users; DROP TABLE athletes");
      assertEquals(
          403, post(paired, "/rpc", request("countTable", table), viewerSession).statusCode());
      String encoderSession = login(paired, encoder.getUsername());
      athlete.setPhotoPath("shared:photos/../../private.png");
      JsonArray unsafeImage = new JsonArray();
      unsafeImage.add(WireCodec.encode(athlete));
      assertEquals(
          400,
          post(paired, "/rpc", request("saveAthlete", unsafeImage), encoderSession).statusCode());
      athlete.setPhotoPath(null);
      HttpResponse<String> saved =
          post(paired, "/rpc", request("saveAthlete", saveArgs), encoderSession);
      assertEquals(200, saved.statusCode(), saved.body());
      int id = JsonParser.parseString(saved.body()).getAsJsonObject().get("result").getAsInt();
      JsonArray deleteArgs = new JsonArray();
      deleteArgs.add(id);
      assertEquals(
          403,
          post(paired, "/rpc", request("deleteAthlete", deleteArgs), encoderSession).statusCode());
      encoder.setActive(false);
      db.saveUser(encoder);
      assertEquals(401, post(paired, "/rpc", read, encoderSession).statusCode());
      String adminSession = login(paired, admin.getUsername());
      assertEquals(
          200,
          post(paired, "/rpc", request("deleteAthlete", deleteArgs), adminSession).statusCode());
      assertTrue(db.getAllAthletes().isEmpty());
      assertThrows(
          java.io.IOException.class,
          () ->
              TlsSupport.client("0".repeat(64))
                  .send(
                      HttpRequest.newBuilder(
                              URI.create(
                                  "https://127.0.0.1:" + paired.getPairingPort() + "/health"))
                          .GET()
                          .build(),
                      HttpResponse.BodyHandlers.ofString()));
      Athlete initial = new Athlete();
      initial.setFirstName("Host");
      initial.setLastName("Record");
      db.saveAthlete(initial);
      encoder.setActive(true);
      db.saveUser(encoder);
      Process client =
          new ProcessBuilder(
                  Path.of(
                          System.getProperty("java.home"),
                          "bin",
                          System.getProperty("os.name").toLowerCase().contains("win")
                              ? "java.exe"
                              : "java")
                      .toString(),
                  "-cp",
                  System.getProperty("java.class.path"),
                  SharingClientProbe.class.getName(),
                  appDir.resolve("client").toString(),
                  Integer.toString(paired.getPairingPort()),
                  sharing.getConfig().getPairingCode())
              .redirectErrorStream(true)
              .start();
      assertTrue(client.waitFor(40, java.util.concurrent.TimeUnit.SECONDS));
      String output =
          new String(
              client.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      assertEquals(0, client.exitValue(), output);
      assertTrue(output.contains("CLIENT_ROUNDTRIP_OK"), output);
      assertEquals(2, db.getAllAthletes().size());
    } finally {
      sharing.useLocalDatabase();
      db.closeConnection();
      sharing.close();
      System.clearProperty("sdms.appDir");
      System.clearProperty("sdms.disableAutoBackup");
    }
  }

  @Test
  void initialMigrationFailureCanBeRetried(@TempDir Path appDir) throws Exception {
    System.setProperty("sdms.appDir", appDir.toString());
    System.setProperty("sdms.disableAutoBackup", "true");
    DatabaseService db = DatabaseService.getInstance();
    SharingManager sharing = SharingManager.getInstance();
    try {
      db.initializeDatabase();
      db.saveUser(user("retry-admin", "ADMIN"));
      db.maintenanceConnection().createStatement().execute("DROP TABLE equipment_items");
      assertThrows(Exception.class, () -> sharing.enableHost("Retry Host"));
      assertFalse(Files.exists(appDir.resolve("shared-database/athlete-track.mv.db")));
      sharing.enableHost("Retry Host");
      assertTrue(sharing.isHost());
      sharing.useLocalDatabase();
      sharing.enableHost("Retry Host Again");
      assertTrue(sharing.isHost());
      assertNotNull(db.findUserByUsername("retry-admin"));
    } finally {
      sharing.useLocalDatabase();
      db.closeConnection();
      sharing.close();
      System.clearProperty("sdms.appDir");
      System.clearProperty("sdms.disableAutoBackup");
    }
  }

  private static User user(String username, String role) {
    User u = new User();
    u.setUsername(username);
    u.setFullName(username);
    u.setRole(role);
    u.setActive(true);
    u.setPasswordHash(PasswordUtil.hash("secure-test-password"));
    return u;
  }

  static String login(SharingConfig config, String username) throws Exception {
    JsonObject body = new JsonObject();
    body.addProperty("username", username);
    body.addProperty("password", "secure-test-password");
    HttpResponse<String> response = post(config, "/auth", body, "");
    assertEquals(200, response.statusCode(), response.body());
    JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
    assertFalse(result.getAsJsonObject("user").has("passwordHash"));
    return result.get("session").getAsString();
  }

  static JsonObject request(String operation, JsonArray arguments) {
    JsonObject body = new JsonObject();
    body.addProperty("operation", operation);
    body.add("arguments", arguments);
    return body;
  }

  static HttpResponse<String> post(
      SharingConfig config, String path, JsonObject body, String session) throws Exception {
    return TlsSupport.client(config.getCertificatePin())
        .send(
            HttpRequest.newBuilder(
                    URI.create("https://127.0.0.1:" + config.getPairingPort() + path))
                .header("X-Athlete-Track-Token", config.getAssetToken())
                .header("X-Athlete-Track-Session", session)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }
}
