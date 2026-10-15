package com.sdms.sharing;

import com.google.gson.*;
import com.sdms.model.User;
import com.sdms.service.DatabaseService;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** The host enforces fresh account permissions on every record request. */
public final class DatabaseApi {
  private static final Set<String> READ =
      Set.of(
          "publishVerificationProfile",
          "findPersonByLegacyId",
          "countTable",
          "getOrCreateVerificationToken",
          "findPersonByVerificationToken",
          "getAllAthletes",
          "searchAthletes",
          "findAthleteByAthleteId",
          "getAthletePalaroRecords",
          "getAthleteLowerMeets",
          "getAllOfficials",
          "searchOfficials",
          "findOfficialByOfficialId",
          "getAllCoaches",
          "searchCoaches",
          "findCoachByCoachId",
          "getAllEquipment",
          "searchEquipment",
          "getEquipmentItems",
          "globalSearch",
          "getAllSchools",
          "getAthletesBySchool",
          "getOfficialsBySchool",
          "getCoachesBySchool");
  private static final Set<String> EDIT =
      Set.of(
          "saveAthlete",
          "saveAthletePalaroRecords",
          "saveAthleteLowerMeets",
          "saveAthleteWithRecords",
          "saveOfficial",
          "saveCoach",
          "saveEquipment");
  private static final Set<String> ADMIN =
      Set.of(
          "deleteAthlete",
          "deleteOfficial",
          "deleteCoach",
          "deleteEquipment",
          "getAllUsers",
          "findUserByUsername",
          "saveUser",
          "deleteUser",
          "countActiveAdmins");
  private static final Set<String> QR =
      Set.of("updateAthleteQrCode", "updateOfficialQrCode", "updateCoachQrCode");

  private record Session(int userId, String username, Instant expires) {}

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final Map<String, LoginAttempts> attempts = new ConcurrentHashMap<>();

  public void status(HttpExchange exchange) throws IOException {
    try {
      JsonObject result = new JsonObject();
      result.addProperty("hasUsers", DatabaseService.getInstance().hasUsers());
      send(exchange, 200, result);
    } catch (Exception e) {
      send(exchange, 500, error("Unable to check main computer"));
    }
  }

  public void login(HttpExchange exchange) throws IOException {
    try {
      JsonObject body = body(exchange);
      String username = body.get("username").getAsString(),
          password = body.get("password").getAsString();
      String client = exchange.getRemoteAddress().getAddress().getHostAddress();
      LoginAttempts window = attempts.computeIfAbsent(client, k -> new LoginAttempts());
      if (window.blocked()) {
        send(exchange, 429, error("Try again later"));
        return;
      }
      if (username.length() > 64 || password.length() > 1024) {
        send(exchange, 400, error("Invalid credentials"));
        return;
      }
      User user = DatabaseService.getInstance().authenticate(username, password);
      if (user == null) {
        window.fail();
        send(exchange, 401, error("Invalid credentials"));
        return;
      }
      attempts.remove(client);
      sessions.entrySet().removeIf(entry -> entry.getValue().expires().isBefore(Instant.now()));
      byte[] secret = new byte[32];
      new SecureRandom().nextBytes(secret);
      String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
      sessions.put(
          token,
          new Session(user.getId(), user.getUsername(), Instant.now().plus(Duration.ofHours(8))));
      JsonObject result = new JsonObject();
      result.addProperty("session", token);
      result.add("user", publicUser(user));
      send(exchange, 200, result);
    } catch (IllegalArgumentException | NullPointerException e) {
      send(exchange, 400, error("Invalid request"));
    } catch (Exception e) {
      send(exchange, 500, error("Unable to sign in"));
    }
  }

  public User principal(HttpExchange exchange) throws Exception {
    String token = exchange.getRequestHeaders().getFirst("X-Athlete-Track-Session");
    Session session = token == null ? null : sessions.get(token);
    if (session == null || session.expires().isBefore(Instant.now())) return null;
    User user = DatabaseService.getInstance().findUserByUsername(session.username());
    return user != null && user.getId() == session.userId() && user.isActive() ? user : null;
  }

  public void logout(HttpExchange exchange) throws IOException {
    String token = exchange.getRequestHeaders().getFirst("X-Athlete-Track-Session");
    if (token != null) sessions.remove(token);
    send(exchange, 200, new JsonObject());
  }

  public boolean mayUpload(HttpExchange exchange, String folder) {
    try {
      User user = principal(exchange);
      return user != null
          && !user.isMustChangePassword()
          && ("qrcodes".equals(folder) || user.canEdit());
    } catch (Exception e) {
      return false;
    }
  }

  public void invoke(HttpExchange exchange) throws IOException {
    try {
      DatabaseService database = DatabaseService.getInstance();
      synchronized (database) {
        User user = principal(exchange);
        if (user == null) {
          send(exchange, 401, error("Sign-in required"));
          return;
        }
        JsonObject body = body(exchange);
        String operation = body.get("operation").getAsString();
        boolean changePassword = operation.equals("changePassword");
        if (user.isMustChangePassword() && !changePassword) {
          send(exchange, 403, error("Change your temporary password first"));
          return;
        }
        boolean allowed =
            READ.contains(operation)
                || (EDIT.contains(operation) && user.canEdit())
                || (ADMIN.contains(operation) && user.isAdmin())
                || QR.contains(operation)
                || changePassword;
        if (!allowed) {
          send(exchange, 403, error("Permission denied"));
          return;
        }
        Method method =
            Arrays.stream(DatabaseService.class.getMethods())
                .filter(m -> m.getName().equals(operation))
                .findFirst()
                .orElseThrow();
        JsonArray values = body.getAsJsonArray("arguments");
        if (values.size() != method.getParameterCount())
          throw new IllegalArgumentException("Argument count");
        Object[] arguments = new Object[values.size()];
        for (int i = 0; i < arguments.length; i++)
          arguments[i] = WireCodec.decode(values.get(i), method.getGenericParameterTypes()[i]);
        if (operation.equals("countTable")
            && !Set.of("athletes", "officials", "coaches", "equipment").contains(arguments[0])) {
          send(exchange, 403, error("Table not permitted"));
          return;
        }
        if (Set.of("saveAthlete", "saveAthleteWithRecords", "saveOfficial", "saveCoach")
            .contains(operation)) {
          JsonObject person = WireCodec.encode(arguments[0]).getAsJsonObject();
          requireSharedAsset(person, "photoPath", "photos");
          requireSharedAsset(person, "qrCodePath", "qrcodes");
        }
        if (QR.contains(operation)
            && (arguments[1] == null
                || !arguments[1].toString().matches("shared:qrcodes/[A-Za-z0-9._-]+")))
          throw new IllegalArgumentException("Invalid QR asset");
        if (changePassword) arguments[0] = user.getId();
        if (operation.equals("saveUser")) {
          User edited = (User) arguments[0];
          if (!Set.of("ADMIN", "ENCODER", "VIEWER").contains(edited.getRole()))
            throw new IllegalArgumentException("Invalid role");
          User existing =
              database.getAllUsers().stream()
                  .filter(u -> u.getId() == edited.getId())
                  .findFirst()
                  .orElse(null);
          if (existing != null
              && existing.isAdmin()
              && existing.isActive()
              && (!edited.isAdmin() || !edited.isActive())
              && database.countActiveAdmins() <= 1)
            throw new IllegalArgumentException("Keep an active administrator");
          if (existing != null
              && existing.getId() == user.getId()
              && (!edited.isAdmin() || !edited.isActive()))
            throw new IllegalArgumentException("Cannot disable own account");
        }
        if (operation.equals("deleteUser")) {
          int id = (int) arguments[0];
          User existing =
              database.getAllUsers().stream().filter(u -> u.getId() == id).findFirst().orElse(null);
          if (id == user.getId()
              || (existing != null
                  && existing.isAdmin()
                  && existing.isActive()
                  && database.countActiveAdmins() <= 1))
            throw new IllegalArgumentException("Keep an active administrator");
        }
        Object result = method.invoke(database, arguments);
        JsonArray updatedModels = new JsonArray();
        for (Object argument : arguments)
          updatedModels.add(
              argument != null && argument.getClass().getPackageName().equals("com.sdms.model")
                  ? WireCodec.encode(argument)
                  : JsonNull.INSTANCE);
        JsonObject response = new JsonObject();
        response.add("result", result instanceof User u ? publicUser(u) : WireCodec.encode(result));
        response.add("arguments", updatedModels);
        if (changePassword)
          sessions
              .entrySet()
              .removeIf(
                  e ->
                      e.getValue().userId() == user.getId()
                          && !e.getKey()
                              .equals(
                                  exchange
                                      .getRequestHeaders()
                                      .getFirst("X-Athlete-Track-Session")));
        send(exchange, 200, response);
      }
    } catch (InvocationTargetException e) {
      send(exchange, 400, error("Operation could not be completed"));
    } catch (IllegalArgumentException | NullPointerException e) {
      send(exchange, 400, error("Invalid operation"));
    } catch (Exception e) {
      send(exchange, 500, error("Operation could not be completed"));
    }
  }

  private static JsonElement publicUser(User user) {
    JsonObject result = WireCodec.encode(user).getAsJsonObject();
    result.remove("passwordHash");
    return result;
  }

  private static void requireSharedAsset(JsonObject model, String field, String folder) {
    JsonElement path = model.get(field);
    if (path != null
        && !path.isJsonNull()
        && !path.getAsString().isBlank()
        && !path.getAsString().matches("shared:" + folder + "/[A-Za-z0-9._-]+")) {
      throw new IllegalArgumentException("Upload the image through the shared asset service");
    }
  }

  private static JsonObject body(HttpExchange exchange) throws IOException {
    if (!exchange.getRequestMethod().equals("POST"))
      throw new IllegalArgumentException("POST required");
    byte[] bytes = exchange.getRequestBody().readNBytes(2_000_001);
    if (bytes.length > 2_000_000) throw new IllegalArgumentException("Request too large");
    return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
  }

  private static JsonObject error(String text) {
    JsonObject result = new JsonObject();
    result.addProperty("error", text);
    return result;
  }

  private static void send(HttpExchange exchange, int status, JsonObject value) throws IOException {
    byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private static class LoginAttempts {
    long start = System.currentTimeMillis();
    int failures;

    synchronized boolean blocked() {
      reset();
      return failures >= 8;
    }

    synchronized void fail() {
      reset();
      failures++;
    }

    void reset() {
      if (System.currentTimeMillis() - start > 300_000) {
        start = System.currentTimeMillis();
        failures = 0;
      }
    }
  }
}
