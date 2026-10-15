package com.sdms.sharing;

import com.google.gson.*;
import com.sdms.model.User;
import com.sdms.service.DatabaseService;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.*;
import java.sql.SQLException;
import java.time.Duration;

/** Calls named, authorized record operations; clients never receive SQL credentials. */
public final class RemoteDatabaseClient {
  private static String sessionToken = "";
  private static SharingConfig sessionConfig;

  private RemoteDatabaseClient() {}

  public static void logout() {
    try {
      if (!sessionToken.isBlank() && sessionConfig != null)
        send(sessionConfig, "/logout", new JsonObject(), true);
    } catch (SQLException ignored) {
      // Local sign-out still completes when the main computer is unavailable.
    } finally {
      sessionToken = "";
      sessionConfig = null;
    }
  }

  public static User authenticate(String username, String password) throws SQLException {
    logout();
    JsonObject request = new JsonObject();
    request.addProperty("username", username);
    request.addProperty("password", password);
    JsonObject response = send(SharingManager.getInstance().getConfig(), "/auth", request, true);
    sessionToken = response.get("session").getAsString();
    sessionConfig = SharingManager.getInstance().getConfig();
    return (User) WireCodec.decode(response.get("user"), User.class);
  }

  public static boolean available(SharingConfig config) throws SQLException {
    return send(config, "/status", new JsonObject(), false).get("hasUsers").getAsBoolean();
  }

  @SuppressWarnings("unchecked")
  public static <T> T call(String operation, Object... arguments) throws SQLException {
    Method method =
        java.util.Arrays.stream(DatabaseService.class.getMethods())
            .filter(m -> m.getName().equals(operation))
            .findFirst()
            .orElseThrow();
    JsonObject request = new JsonObject();
    request.addProperty("operation", operation);
    request.add("arguments", WireCodec.encode(arguments));
    JsonObject response = send(SharingManager.getInstance().getConfig(), "/rpc", request, true);
    JsonArray updated = response.getAsJsonArray("arguments");
    try {
      for (int i = 0; i < arguments.length; i++)
        if (arguments[i] != null
            && arguments[i].getClass().getPackageName().equals("com.sdms.model"))
          WireCodec.fill(arguments[i], updated.get(i).getAsJsonObject());
    } catch (ReflectiveOperationException e) {
      throw new SQLException("Invalid main computer response", e);
    }
    return (T) WireCodec.decode(response.get("result"), method.getGenericReturnType());
  }

  public static String session() {
    return sessionToken;
  }

  private static JsonObject send(
      SharingConfig config, String path, JsonObject payload, boolean authenticated)
      throws SQLException {
    if (config.getCertificatePin().isBlank())
      throw new SQLException(
          "This connection uses an older sharing protocol. Pair with the updated main computer"
              + " again.");
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(
                  URI.create("https://" + config.getHost() + ":" + config.getPairingPort() + path))
              .timeout(Duration.ofSeconds(path.equals("/logout") ? 3 : 25))
              .header("Content-Type", "application/json")
              .header("X-Athlete-Track-Token", config.getAssetToken());
      if (authenticated) builder.header("X-Athlete-Track-Session", sessionToken);
      HttpResponse<String> response =
          TlsSupport.client(config.getCertificatePin())
              .send(
                  builder.POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build(),
                  HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw new SQLException(
            response.statusCode() == 401
                ? "Please sign in again."
                : response.statusCode() == 403
                    ? "Your account does not have permission for this operation."
                    : "The main computer could not complete the operation.");
      return JsonParser.parseString(response.body()).getAsJsonObject();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SQLException("Sharing request interrupted", e);
    } catch (java.io.IOException | RuntimeException e) {
      throw new SQLException("Could not communicate securely with the main computer", e);
    }
  }
}
