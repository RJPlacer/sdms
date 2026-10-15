package com.sdms.service;

import com.sdms.model.*;
import com.sdms.sharing.SharingManager;
import com.sdms.sharing.RemoteDatabaseClient;
import com.sdms.util.PasswordUtil;
import com.sdms.util.AppPaths;

import java.sql.*;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseService {

    private static DatabaseService instance;
    private Connection connection;
    private boolean sharedDatabase;

    private DatabaseService() {}

    private boolean isRemote() { return SharingManager.getInstance().isClient(); }

    public synchronized User authenticate(String username, String password) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.authenticate(username, password);
        User user = findUserByUsername(username);
        if (user == null || !user.isActive() || !PasswordUtil.verify(password, user.getPasswordHash())) return null;
        if ("admin".equalsIgnoreCase(user.getUsername()) && "admin123".equals(password)) user.setMustChangePassword(true);
        if (PasswordUtil.needsRehash(user.getPasswordHash())) { user.setPasswordHash(PasswordUtil.hash(password)); saveUser(user); }
        else if (user.isMustChangePassword()) saveUser(user);
        return user;
    }

    public synchronized User changePassword(int userId, String currentPassword, String newPassword) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("changePassword", userId, currentPassword, newPassword);
        User user = getAllUsers().stream().filter(u -> u.getId() == userId).findFirst().orElse(null);
        if (user == null || !user.isActive() || !PasswordUtil.verify(currentPassword, user.getPasswordHash())) throw new SQLException("Current password is incorrect.");
        if (newPassword == null || newPassword.length() < 8 || newPassword.equals(currentPassword)) throw new SQLException("Choose a different password of at least eight characters.");
        user.setPasswordHash(PasswordUtil.hash(newPassword)); user.setMustChangePassword(false); saveUser(user); return user;
    }

    public synchronized String publishVerificationProfile(String type, int id) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("publishVerificationProfile", type, id);
        try {
            if (!OnlineVerificationService.isConfigured()) return null;
            String table = switch (type) { case "ATHLETE" -> "athletes"; case "OFFICIAL" -> "officials"; case "COACH" -> "coaches"; default -> throw new SQLException("Unsupported profile"); };
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE id=?")) {
                statement.setInt(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("Record not found");
                    return switch (type) {
                        case "ATHLETE" -> OnlineVerificationService.publishAthlete(mapAthlete(result)).orElse(null);
                        case "OFFICIAL" -> OnlineVerificationService.publishOfficial(mapOfficial(result)).orElse(null);
                        default -> OnlineVerificationService.publishCoach(mapCoach(result)).orElse(null);
                    };
                }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException("Online verification interrupted", e); }
        catch (java.io.IOException e) { throw new SQLException("Record saved, but online verification could not synchronize. Check the connection and retry.", e); }
    }

    public synchronized String[] findPersonByLegacyId(String value) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("findPersonByLegacyId", value);
        if (value == null || value.isBlank() || value.length() > 128) return null;
        String[] found = null;
        for (String[] type : List.of(new String[]{"ATHLETE", "athletes", "athlete_id", "learner_ref"},
                new String[]{"OFFICIAL", "officials", "official_id", "employee_number"},
                new String[]{"COACH", "coaches", "coach_id", "employee_number"})) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + type[2] + " FROM " + type[1] + " WHERE " + type[2] + "=? OR " + type[3] + "=?")) {
                statement.setString(1, value.trim()); statement.setString(2, value.trim());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        if (found != null) throw new SQLException("This older QR code matches multiple records. Use the current Athlete Track ID to select the correct record.");
                        found = new String[]{type[0], result.getString(1)};
                    }
                }
            }
        }
        return found;
    }

    private void revokeVerification(String table, String businessIdColumn, String type, int id) throws SQLException {
        if (!OnlineVerificationService.isConfigured()) return;
        try (PreparedStatement statement = connection.prepareStatement("SELECT verification_token," + businessIdColumn + ", first_name, last_name, school FROM " + table + " WHERE id=?")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) OnlineVerificationService.revoke(result.getString(1), result.getString(2), type,
                    result.getString("last_name") + ", " + result.getString("first_name"), result.getString("school"));
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException("Online revocation interrupted", e); }
        catch (java.io.IOException e) { throw new SQLException("Cannot delete until online ID revocation succeeds. Check the connection and retry.", e); }
    }

    @FunctionalInterface
    private interface SqlWork<T> { T run() throws SQLException; }

    private <T> T inTransaction(SqlWork<T> work) throws SQLException {
        if (!connection.getAutoCommit()) return work.run();
        connection.setAutoCommit(false);
        try {
            T result = work.run();
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try { connection.rollback(); } catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public static DatabaseService getInstance() {
        if (instance == null) instance = new DatabaseService();
        return instance;
    }

    public synchronized void initializeDatabase() {
        try {
            closeConnection();
            SharingManager sharing = SharingManager.getInstance();
            sharedDatabase = sharing.isShared();
            if (sharing.isClient()) { RemoteDatabaseClient.available(sharing.getConfig()); return; }
            Class.forName(sharedDatabase ? "org.h2.Driver" : "org.sqlite.JDBC");
            connection = sharedDatabase
                ? DriverManager.getConnection(sharing.jdbcUrl(), sharing.databaseUser(), sharing.databasePassword())
                : DriverManager.getConnection(sharing.jdbcUrl());
            if (sharedDatabase) {
                connection.createStatement().execute("SET DEFAULT_LOCK_TIMEOUT 10000");
            } else {
                connection.createStatement().execute("PRAGMA foreign_keys = ON");
            }
            createTables();
            cleanupOrphanedChildren();
            createUserTable();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to open database: " + e.getMessage(), e);
        }
    }

    public synchronized void closeConnection() {
        try { if (connection != null) connection.close(); } catch (SQLException ignored) {}
        connection = null;
        RemoteDatabaseClient.logout();
    }

    public synchronized void reinitialize() {
        initializeDatabase();
    }

    public synchronized Connection maintenanceConnection() {
        if (connection == null) throw new IllegalStateException("The database is not open.");
        return connection;
    }

    public synchronized void writeBackupScript(java.nio.file.Path destination) throws SQLException {
        if (isRemote()) throw new SQLException("Back up from the main computer.");
        String escaped = destination.toAbsolutePath().toString().replace("'", "''");
        try (Statement statement = connection.createStatement()) {
            statement.execute("SCRIPT TO '" + escaped + "' COMPRESSION ZIP");
        }
    }

    public synchronized int countTable(String table) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("countTable" , table);
        if (!List.of("athletes", "officials", "coaches", "equipment", "users").contains(table)) throw new SQLException("Unsupported table");
        ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) FROM " + table);
        return rs.next() ? rs.getInt(1) : 0;
    }

    private void createTables() throws SQLException {
        Statement st = connection.createStatement();
        String id = idColumn();
        st.execute("CREATE TABLE IF NOT EXISTS athletes (id " + id + ", athlete_id TEXT UNIQUE, last_name TEXT, first_name TEXT, middle_initial TEXT, sex TEXT, learner_ref TEXT, contact_no TEXT, date_of_birth TEXT, age INTEGER, place_of_birth TEXT, school TEXT, grade_level TEXT, school_address TEXT, present_address TEXT, photo_path TEXT, qr_code_path TEXT, verification_token TEXT UNIQUE, mother_name TEXT, father_name TEXT, guardian_address TEXT, cert_meet TEXT, cert_coach_name TEXT, cert_dso_name TEXT, cert_rso_name TEXT)");
        st.execute("CREATE TABLE IF NOT EXISTS athlete_palaro_previous (id " + id + ", athlete_id INTEGER REFERENCES athletes(id) ON DELETE CASCADE, year TEXT, sports_event TEXT, venue TEXT, remarks TEXT)");
        st.execute("CREATE TABLE IF NOT EXISTS athlete_lower_meets (id " + id + ", athlete_id INTEGER REFERENCES athletes(id) ON DELETE CASCADE, inclusive_dates TEXT, sports_event TEXT, athletic_meet TEXT, remarks TEXT)");
        st.execute("CREATE TABLE IF NOT EXISTS officials (id " + id + ", official_id TEXT UNIQUE, last_name TEXT, first_name TEXT, middle_initial TEXT, sex TEXT, mobile_phone TEXT, date_of_birth TEXT, age INTEGER, place_of_birth TEXT, current_position TEXT, years_in_service INTEGER, school TEXT, employee_number TEXT, school_address TEXT, present_address TEXT, emergency_contact_name TEXT, emergency_contact_no TEXT, photo_path TEXT, qr_code_path TEXT, verification_token TEXT UNIQUE)");
        st.execute("CREATE TABLE IF NOT EXISTS coaches (id " + id + ", coach_id TEXT UNIQUE, last_name TEXT, first_name TEXT, middle_initial TEXT, sex TEXT, mobile_phone TEXT, date_of_birth TEXT, age INTEGER, place_of_birth TEXT, current_position TEXT, years_in_service INTEGER, school TEXT, employee_number TEXT, school_address TEXT, present_address TEXT, emergency_contact_name TEXT, emergency_contact_no TEXT, photo_path TEXT, qr_code_path TEXT, verification_token TEXT UNIQUE)");
        for (String p : List.of("official", "coach")) {
            String parentTable = p.equals("official") ? "officials" : "coaches";
            String foreignKey = " REFERENCES " + parentTable + "(id) ON DELETE CASCADE";
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "_education (id " + id + ", parent_id INTEGER" + foreignKey + ", level TEXT, course TEXT, school TEXT, year_graduated TEXT, credits_earned TEXT, awards_received TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "_sports_training (id " + id + ", parent_id INTEGER" + foreignKey + ", title TEXT, date_of_training TEXT, num_hours INTEGER, conducted_by TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "_track_record (id " + id + ", parent_id INTEGER" + foreignKey + ", meet_attended TEXT, inclusive_dates TEXT, event TEXT, awards_received TEXT)");
        }
        st.execute("CREATE TABLE IF NOT EXISTS equipment (id " + id + ", equipment_id TEXT UNIQUE, borrower_name TEXT, designation TEXT, school TEXT, event TEXT, mobile_no TEXT, date TEXT, issued_by TEXT, issued_to TEXT)");
        st.execute("CREATE TABLE IF NOT EXISTS equipment_items (id " + id + ", equipment_id INTEGER REFERENCES equipment(id) ON DELETE CASCADE, qty INTEGER, unit TEXT, make_and_description TEXT, date_borrowed TEXT, date_returned TEXT, remarks TEXT)");
        st.execute("CREATE TABLE IF NOT EXISTS id_sequences (sequence_key TEXT PRIMARY KEY, next_value INTEGER NOT NULL)");
        st.execute("CREATE TABLE IF NOT EXISTS legacy_import_rows (source TEXT, source_table TEXT, source_key TEXT, record_json TEXT, PRIMARY KEY(source, source_table, source_key))");
        st.execute("CREATE TABLE IF NOT EXISTS legacy_import_map (source TEXT, source_table TEXT, source_key TEXT, target_table TEXT, target_id INTEGER, PRIMARY KEY(source, source_table, source_key))");
        st.close();

        if (!hasColumn("athletes", "grade_level")) {
            connection.createStatement().execute(
                "ALTER TABLE athletes ADD COLUMN grade_level TEXT");
        }

        for (String table : List.of("athletes", "officials", "coaches")) {
            if (!hasColumn(table, "verification_token")) {
                connection.createStatement().execute(
                    "ALTER TABLE " + table + " ADD COLUMN verification_token TEXT");
            }
            connection.createStatement().execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_" + table + "_verification_token ON "
                    + table + "(verification_token)");
        }
    }

    private String idColumn() {
        return sharedDatabase
            ? "INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY"
            : "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    public synchronized String getOrCreateVerificationToken(String personType, int databaseId) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getOrCreateVerificationToken" , personType, databaseId);
        String table = switch (personType.toUpperCase()) {
            case "ATHLETE" -> "athletes";
            case "OFFICIAL" -> "officials";
            case "COACH" -> "coaches";
            default -> throw new IllegalArgumentException("Unsupported person type: " + personType);
        };

        try (PreparedStatement select = connection.prepareStatement(
                "SELECT verification_token FROM " + table + " WHERE id=?")) {
            select.setInt(1, databaseId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) throw new SQLException("Record not found while creating verification token.");
                String existing = rs.getString(1);
                if (existing != null && !existing.isBlank()) return existing;
            }
        }

        String token = UUID.randomUUID().toString();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table + " SET verification_token=? WHERE id=?")) {
            update.setString(1, token);
            update.setInt(2, databaseId);
            update.executeUpdate();
        }
        return token;
    }

    public synchronized String[] findPersonByVerificationToken(String token) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("findPersonByVerificationToken" , token);
        if (token == null || token.isBlank()) return null;
        for (String[] type : List.of(
                new String[]{"ATHLETE", "athletes", "athlete_id"},
                new String[]{"OFFICIAL", "officials", "official_id"},
                new String[]{"COACH", "coaches", "coach_id"})) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT " + type[2] + " FROM " + type[1] + " WHERE verification_token=?")) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return new String[]{type[0], rs.getString(1)};
                }
            }
        }
        return null;
    }

    private void cleanupOrphanedChildren() throws SQLException {
        try (Statement st = connection.createStatement()) {
            for (String type : List.of("official", "coach")) {
                String parentTable = type.equals("official") ? "officials" : "coaches";
                for (String suffix : List.of("education", "sports_training", "track_record")) {
                    st.executeUpdate("DELETE FROM " + type + "_" + suffix
                        + " WHERE parent_id IS NULL OR parent_id NOT IN (SELECT id FROM " + parentTable + ")");
                }
            }
        }
    }

    private String generateId(String prefix, String table, String col) throws SQLException {
        int year = Year.now().getValue();
        if (sharedDatabase) return generateSharedId(prefix, table, col, year);
        String key = prefix + "-" + year;
        try (PreparedStatement select = connection.prepareStatement("SELECT next_value FROM id_sequences WHERE sequence_key=?")) {
            select.setString(1, key);
            try (ResultSet result = select.executeQuery()) {
                if (result.next()) {
                    int value = result.getInt(1);
                    try (PreparedStatement update = connection.prepareStatement("UPDATE id_sequences SET next_value=? WHERE sequence_key=?")) {
                        update.setInt(1, value + 1); update.setString(2, key); update.executeUpdate();
                    }
                    return "%s-%d-%04d".formatted(prefix, year, value);
                }
            }
        }
        int first = nextExistingSequence(prefix, table, col, year);
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO id_sequences (sequence_key,next_value) VALUES (?,?)")) {
            insert.setString(1, key); insert.setInt(2, first + 1); insert.executeUpdate();
        }
        return "%s-%d-%04d".formatted(prefix, year, first);
    }

    private String generateSharedId(String prefix, String table, String col, int year) throws SQLException {
        String key = prefix + "-" + year;
        for (int attempt = 0; attempt < 4; attempt++) {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT next_value FROM id_sequences WHERE sequence_key=? FOR UPDATE")) {
                select.setString(1, key);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        int value = rs.getInt(1);
                        try (PreparedStatement update = connection.prepareStatement(
                                "UPDATE id_sequences SET next_value=? WHERE sequence_key=?")) {
                            update.setInt(1, value + 1);
                            update.setString(2, key);
                            update.executeUpdate();
                        }
                        return "%s-%d-%04d".formatted(prefix, year, value);
                    }
                }
            }

            int first = nextExistingSequence(prefix, table, col, year);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO id_sequences (sequence_key, next_value) VALUES (?,?)")) {
                insert.setString(1, key);
                insert.setInt(2, first + 1);
                insert.executeUpdate();
                return "%s-%d-%04d".formatted(prefix, year, first);
            } catch (SQLIntegrityConstraintViolationException race) {
                // Another encoder created the sequence row first; retry under its row lock.
            }
        }
        throw new SQLException("Could not allocate a unique record number. Please try saving again.");
    }

    private int nextExistingSequence(String prefix, String table, String col, int year) throws SQLException {
        int maxSequence = 0;
        String marker = prefix + "-" + year + "-";
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + col + " FROM " + table + " WHERE " + col + " LIKE ?")) {
            ps.setString(1, marker + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String value = rs.getString(1);
                    if (value == null || !value.startsWith(marker)) continue;
                    try { maxSequence = Math.max(maxSequence, Integer.parseInt(value.substring(marker.length()))); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return maxSequence + 1;
    }

    // ── Athlete ───────────────────────────────────────────────────────────────

    public synchronized List<Athlete> getAllAthletes() throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getAllAthletes");
        List<Athlete> list = new ArrayList<>();
        ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM athletes ORDER BY last_name, first_name");
        while (rs.next()) list.add(mapAthlete(rs));
        return list;
    }

    public synchronized List<Athlete> searchAthletes(String q) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("searchAthletes" , q);
        List<Athlete> list = new ArrayList<>();
        String like = "%" + q.toLowerCase() + "%";
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM athletes WHERE lower(last_name) LIKE ? OR lower(first_name) LIKE ? OR lower(school) LIKE ? OR athlete_id LIKE ? ORDER BY last_name, first_name");
        ps.setString(1,like); ps.setString(2,like); ps.setString(3,like); ps.setString(4,like);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) list.add(mapAthlete(rs));
        return list;
    }

    public synchronized Athlete findAthleteByAthleteId(String athleteId) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("findAthleteByAthleteId" , athleteId);
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM athletes WHERE athlete_id=?");
        ps.setString(1, athleteId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            Athlete athlete = mapAthlete(rs);
            athlete.setPreviousPalaroRecords(getAthletePalaroRecords(athlete.getId()));
            athlete.setLowerMeetRecords(getAthleteLowerMeets(athlete.getId()));
            return athlete;
        }
        return null;
    }

    public synchronized int saveAthlete(Athlete a) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("saveAthlete" , a);
        return inTransaction(() -> saveAthleteInternal(a));
    }

    private int saveAthleteInternal(Athlete a) throws SQLException {
        if (a.getAthleteId() == null || a.getAthleteId().isBlank())
            a.setAthleteId(generateId("ATH", "athletes", "athlete_id"));
        if (a.getId() == 0) {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO athletes (athlete_id,last_name,first_name,middle_initial,sex,learner_ref,contact_no,date_of_birth,age,place_of_birth,school,grade_level,school_address,present_address,photo_path,qr_code_path,mother_name,father_name,guardian_address,cert_meet,cert_coach_name,cert_dso_name,cert_rso_name) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            bindAthlete(ps, a); ps.executeUpdate();
            ResultSet k = ps.getGeneratedKeys(); if (k.next()) a.setId(k.getInt(1));
        } else {
            PreparedStatement ps = connection.prepareStatement("UPDATE athletes SET last_name=?,first_name=?,middle_initial=?,sex=?,learner_ref=?,contact_no=?,date_of_birth=?,age=?,place_of_birth=?,school=?,grade_level=?,school_address=?,present_address=?,photo_path=?,qr_code_path=?,mother_name=?,father_name=?,guardian_address=?,cert_meet=?,cert_coach_name=?,cert_dso_name=?,cert_rso_name=? WHERE id=?");
            bindAthleteUpdate(ps, a); ps.executeUpdate();
        }
        return a.getId();
    }

    public synchronized void deleteAthlete(int id) throws SQLException {
        if (isRemote()) { RemoteDatabaseClient.call("deleteAthlete" , id); return; }
        revokeVerification("athletes", "athlete_id", "ATHLETE", id);
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM athletes WHERE id=?")) {
            ps.setInt(1,id);
            ps.executeUpdate();
        }
    }

    public synchronized void saveAthletePalaroRecords(int athleteId, java.util.List<Athlete.PalaroPrevious> records) throws SQLException {
        if (isRemote()) { RemoteDatabaseClient.call("saveAthletePalaroRecords" , athleteId, records); return; }
        try (PreparedStatement del = connection.prepareStatement("DELETE FROM athlete_palaro_previous WHERE athlete_id=?")) {
            del.setInt(1, athleteId);
            del.executeUpdate();
        }
        for (Athlete.PalaroPrevious r : records) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO athlete_palaro_previous (athlete_id, year, sports_event, venue, remarks) VALUES (?,?,?,?,?)")) {
                ps.setInt(1, athleteId); ps.setString(2, r.year); ps.setString(3, r.sportsEvent);
                ps.setString(4, r.venue); ps.setString(5, r.remarks); ps.executeUpdate();
            }
        }
    }

    public synchronized void saveAthleteLowerMeets(int athleteId, java.util.List<Athlete.LowerMeet> meets) throws SQLException {
        if (isRemote()) { RemoteDatabaseClient.call("saveAthleteLowerMeets" , athleteId, meets); return; }
        try (PreparedStatement del = connection.prepareStatement("DELETE FROM athlete_lower_meets WHERE athlete_id=?")) {
            del.setInt(1, athleteId);
            del.executeUpdate();
        }
        for (Athlete.LowerMeet m : meets) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO athlete_lower_meets (athlete_id, inclusive_dates, sports_event, athletic_meet, remarks) VALUES (?,?,?,?,?)")) {
                ps.setInt(1, athleteId); ps.setString(2, m.inclusiveDates); ps.setString(3, m.sportsEvent);
                ps.setString(4, m.athleticMeet); ps.setString(5, m.remarks); ps.executeUpdate();
            }
        }
    }

    public synchronized int saveAthleteWithRecords(Athlete athlete,
            List<Athlete.PalaroPrevious> records, List<Athlete.LowerMeet> meets) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("saveAthleteWithRecords" , athlete, records, meets);
        return inTransaction(() -> {
            int id = saveAthleteInternal(athlete);
            saveAthletePalaroRecords(id, records);
            saveAthleteLowerMeets(id, meets);
            return id;
        });
    }

    public synchronized java.util.List<Athlete.PalaroPrevious> getAthletePalaroRecords(int athleteId) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getAthletePalaroRecords" , athleteId);
        java.util.List<Athlete.PalaroPrevious> list = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM athlete_palaro_previous WHERE athlete_id=?");
        ps.setInt(1, athleteId); ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Athlete.PalaroPrevious r = new Athlete.PalaroPrevious();
            r.id = rs.getInt("id"); r.athleteId = athleteId;
            r.year = rs.getString("year"); r.sportsEvent = rs.getString("sports_event");
            r.venue = rs.getString("venue"); r.remarks = rs.getString("remarks");
            list.add(r);
        }
        return list;
    }

    public synchronized java.util.List<Athlete.LowerMeet> getAthleteLowerMeets(int athleteId) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getAthleteLowerMeets" , athleteId);
        java.util.List<Athlete.LowerMeet> list = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM athlete_lower_meets WHERE athlete_id=?");
        ps.setInt(1, athleteId); ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Athlete.LowerMeet m = new Athlete.LowerMeet();
            m.id = rs.getInt("id"); m.athleteId = athleteId;
            m.inclusiveDates = rs.getString("inclusive_dates"); m.sportsEvent = rs.getString("sports_event");
            m.athleticMeet = rs.getString("athletic_meet"); m.remarks = rs.getString("remarks");
            list.add(m);
        }
        return list;
    }


    private Athlete mapAthlete(ResultSet rs) throws SQLException {
        Athlete a = new Athlete();
        a.setId(rs.getInt("id")); a.setAthleteId(rs.getString("athlete_id"));
        a.setLastName(rs.getString("last_name")); a.setFirstName(rs.getString("first_name"));
        a.setMiddleInitial(rs.getString("middle_initial")); a.setSex(rs.getString("sex"));
        a.setLearnerRefNumber(rs.getString("learner_ref")); a.setContactNo(rs.getString("contact_no"));
        String dob = rs.getString("date_of_birth"); if (dob!=null) a.setDateOfBirth(LocalDate.parse(dob));
        a.setAge(rs.getInt("age")); a.setPlaceOfBirth(rs.getString("place_of_birth"));
        a.setSchool(rs.getString("school")); a.setGradeLevel(rs.getString("grade_level"));
        a.setSchoolAddress(rs.getString("school_address"));
        a.setPresentAddress(rs.getString("present_address")); a.setPhotoPath(rs.getString("photo_path"));
        a.setQrCodePath(rs.getString("qr_code_path")); a.setMotherName(rs.getString("mother_name"));
        a.setFatherName(rs.getString("father_name")); a.setGuardianAddress(rs.getString("guardian_address"));
        a.setCertMeet(rs.getString("cert_meet")); a.setCertCoachName(rs.getString("cert_coach_name"));
        a.setCertDsoName(rs.getString("cert_dso_name")); a.setCertRsoName(rs.getString("cert_rso_name"));
        return a;
    }

    private void bindAthlete(PreparedStatement ps, Athlete a) throws SQLException {
        ps.setString(1,a.getAthleteId()); ps.setString(2,a.getLastName()); ps.setString(3,a.getFirstName());
        ps.setString(4,a.getMiddleInitial()); ps.setString(5,a.getSex()); ps.setString(6,a.getLearnerRefNumber());
        ps.setString(7,a.getContactNo()); ps.setString(8,a.getDateOfBirth()!=null?a.getDateOfBirth().toString():null);
        ps.setInt(9,a.getAge()); ps.setString(10,a.getPlaceOfBirth()); ps.setString(11,a.getSchool());
        ps.setString(12,a.getGradeLevel()); ps.setString(13,a.getSchoolAddress()); ps.setString(14,a.getPresentAddress());
        ps.setString(15,a.getPhotoPath()); ps.setString(16,a.getQrCodePath()); ps.setString(17,a.getMotherName());
        ps.setString(18,a.getFatherName()); ps.setString(19,a.getGuardianAddress()); ps.setString(20,a.getCertMeet());
        ps.setString(21,a.getCertCoachName()); ps.setString(22,a.getCertDsoName()); ps.setString(23,a.getCertRsoName());
    }

    private void bindAthleteUpdate(PreparedStatement ps, Athlete a) throws SQLException {
        ps.setString(1,a.getLastName()); ps.setString(2,a.getFirstName()); ps.setString(3,a.getMiddleInitial());
        ps.setString(4,a.getSex()); ps.setString(5,a.getLearnerRefNumber()); ps.setString(6,a.getContactNo());
        ps.setString(7,a.getDateOfBirth()!=null?a.getDateOfBirth().toString():null); ps.setInt(8,a.getAge());
        ps.setString(9,a.getPlaceOfBirth()); ps.setString(10,a.getSchool()); ps.setString(11,a.getGradeLevel());
        ps.setString(12,a.getSchoolAddress()); ps.setString(13,a.getPresentAddress()); ps.setString(14,a.getPhotoPath());
        ps.setString(15,a.getQrCodePath()); ps.setString(16,a.getMotherName()); ps.setString(17,a.getFatherName());
        ps.setString(18,a.getGuardianAddress()); ps.setString(19,a.getCertMeet()); ps.setString(20,a.getCertCoachName());
        ps.setString(21,a.getCertDsoName()); ps.setString(22,a.getCertRsoName()); ps.setInt(23,a.getId());
    }

    // ── Official ──────────────────────────────────────────────────────────────

    public synchronized List<Official> getAllOfficials() throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getAllOfficials");
        List<Official> list = new ArrayList<>();
        ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM officials ORDER BY last_name, first_name");
        while (rs.next()) { Official o = mapOfficial(rs); loadOfficialChildren(o); list.add(o); }
        return list;
    }

    public synchronized List<Official> searchOfficials(String q) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("searchOfficials" , q);
        List<Official> list = new ArrayList<>();
        String like = "%" + q.toLowerCase() + "%";
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM officials WHERE lower(last_name) LIKE ? OR lower(first_name) LIKE ? OR lower(school) LIKE ? OR official_id LIKE ? ORDER BY last_name");
        ps.setString(1,like); ps.setString(2,like); ps.setString(3,like); ps.setString(4,like);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) { Official o = mapOfficial(rs); loadOfficialChildren(o); list.add(o); }
        return list;
    }

    public synchronized Official findOfficialByOfficialId(String officialId) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("findOfficialByOfficialId" , officialId);
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM officials WHERE official_id=?");
        ps.setString(1, officialId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            Official o = mapOfficial(rs);
            loadOfficialChildren(o);
            return o;
        }
        return null;
    }

    public synchronized int saveOfficial(Official o) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("saveOfficial" , o);
        return inTransaction(() -> saveOfficialInternal(o));
    }

    private int saveOfficialInternal(Official o) throws SQLException {
        if (o.getOfficialId() == null || o.getOfficialId().isBlank())
            o.setOfficialId(generateId("OFC", "officials", "official_id"));
        if (o.getId() == 0) {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO officials (official_id,last_name,first_name,middle_initial,sex,mobile_phone,date_of_birth,age,place_of_birth,current_position,years_in_service,school,employee_number,school_address,present_address,emergency_contact_name,emergency_contact_no,photo_path,qr_code_path) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            bindOfficial(ps, o); ps.executeUpdate();
            ResultSet k = ps.getGeneratedKeys(); if (k.next()) o.setId(k.getInt(1));
        } else {
            PreparedStatement ps = connection.prepareStatement("UPDATE officials SET last_name=?,first_name=?,middle_initial=?,sex=?,mobile_phone=?,date_of_birth=?,age=?,place_of_birth=?,current_position=?,years_in_service=?,school=?,employee_number=?,school_address=?,present_address=?,emergency_contact_name=?,emergency_contact_no=?,photo_path=?,qr_code_path=? WHERE id=?");
            bindOfficialUpdate(ps, o); ps.executeUpdate();
        }
        saveOfficialChildren(o);
        return o.getId();
    }

    public synchronized void deleteOfficial(int id) throws SQLException {
        if (isRemote()) { RemoteDatabaseClient.call("deleteOfficial" , id); return; }
        revokeVerification("officials", "official_id", "OFFICIAL", id);
        inTransaction(() -> {
            deleteChildren("official", id);
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM officials WHERE id=?")) {
                ps.setInt(1,id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public synchronized void updateOfficialQrCode(int id, String qrCodePath) throws SQLException {
        if (isRemote()) { RemoteDatabaseClient.call("updateOfficialQrCode" , id, qrCodePath); return; }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE officials SET qr_code_path=? WHERE id=?")) {
            ps.setString(1, qrCodePath);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private void saveOfficialChildren(Official o) throws SQLException {
        int id = o.getId();
        deleteChildren("official", id);
        for (Official.EducationalQualification eq : o.getEducationalQualifications()) {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO official_education (parent_id,level,course,school,year_graduated,credits_earned,awards_received) VALUES (?,?,?,?,?,?,?)")) {
                ps.setInt(1,id); ps.setString(2,eq.level); ps.setString(3,eq.course); ps.setString(4,eq.school);
                ps.setString(5,eq.yearGraduated); ps.setString(6,eq.creditsEarned); ps.setString(7,eq.awardsReceived);
                ps.executeUpdate();
            }
        }
        for (Official.SportsTraining st : o.getSportsTrainings()) {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO official_sports_training (parent_id,title,date_of_training,num_hours,conducted_by) VALUES (?,?,?,?,?)")) {
                ps.setInt(1,id); ps.setString(2,st.title); ps.setString(3,st.dateOfTraining);
                ps.setInt(4,st.numberOfHours); ps.setString(5,st.conductedBy); ps.executeUpdate();
            }
        }
        for (Official.TrackRecord tr : o.getTrackRecords()) {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO official_track_record (parent_id,meet_attended,inclusive_dates,event,awards_received) VALUES (?,?,?,?,?)")) {
                ps.setInt(1,id); ps.setString(2,tr.athleteMeetAttended); ps.setString(3,tr.inclusiveDates);
                ps.setString(4,tr.event); ps.setString(5,tr.awardsReceived); ps.executeUpdate();
            }
        }
    }

    private void loadOfficialChildren(Official o) throws SQLException {
        int id = o.getId();
        List<Official.EducationalQualification> eqs = new ArrayList<>();
        ResultSet r1 = connection.prepareStatement("SELECT * FROM official_education WHERE parent_id=" + id).executeQuery();
        while (r1.next()) { Official.EducationalQualification eq = new Official.EducationalQualification();
            eq.level=r1.getString("level"); eq.course=r1.getString("course"); eq.school=r1.getString("school");
            eq.yearGraduated=r1.getString("year_graduated"); eq.creditsEarned=r1.getString("credits_earned");
            eq.awardsReceived=r1.getString("awards_received"); eqs.add(eq); }
        o.setEducationalQualifications(eqs);
        List<Official.SportsTraining> sts = new ArrayList<>();
        ResultSet r2 = connection.prepareStatement("SELECT * FROM official_sports_training WHERE parent_id=" + id).executeQuery();
        while (r2.next()) { Official.SportsTraining st = new Official.SportsTraining();
            st.title=r2.getString("title"); st.dateOfTraining=r2.getString("date_of_training");
            st.numberOfHours=r2.getInt("num_hours"); st.conductedBy=r2.getString("conducted_by"); sts.add(st); }
        o.setSportsTrainings(sts);
        List<Official.TrackRecord> trs = new ArrayList<>();
        ResultSet r3 = connection.prepareStatement("SELECT * FROM official_track_record WHERE parent_id=" + id).executeQuery();
        while (r3.next()) { Official.TrackRecord tr = new Official.TrackRecord();
            tr.athleteMeetAttended=r3.getString("meet_attended"); tr.inclusiveDates=r3.getString("inclusive_dates");
            tr.event=r3.getString("event"); tr.awardsReceived=r3.getString("awards_received"); trs.add(tr); }
        o.setTrackRecords(trs);
    }

    private Official mapOfficial(ResultSet rs) throws SQLException {
        Official o = new Official();
        o.setId(rs.getInt("id")); o.setOfficialId(rs.getString("official_id"));
        o.setLastName(rs.getString("last_name")); o.setFirstName(rs.getString("first_name"));
        o.setMiddleInitial(rs.getString("middle_initial")); o.setSex(rs.getString("sex"));
        o.setMobilePhone(rs.getString("mobile_phone"));
        String dob = rs.getString("date_of_birth"); if (dob!=null) o.setDateOfBirth(LocalDate.parse(dob));
        o.setAge(rs.getInt("age")); o.setPlaceOfBirth(rs.getString("place_of_birth"));
        o.setCurrentPosition(rs.getString("current_position")); o.setYearsInService(rs.getInt("years_in_service"));
        o.setSchool(rs.getString("school")); o.setEmployeeNumber(rs.getString("employee_number"));
        o.setSchoolAddress(rs.getString("school_address")); o.setPresentAddress(rs.getString("present_address"));
        o.setEmergencyContactName(rs.getString("emergency_contact_name")); o.setEmergencyContactNo(rs.getString("emergency_contact_no"));
        o.setPhotoPath(rs.getString("photo_path")); o.setQrCodePath(rs.getString("qr_code_path"));
        return o;
    }

    private void bindOfficial(PreparedStatement ps, Official o) throws SQLException {
        ps.setString(1,o.getOfficialId()); ps.setString(2,o.getLastName()); ps.setString(3,o.getFirstName());
        ps.setString(4,o.getMiddleInitial()); ps.setString(5,o.getSex()); ps.setString(6,o.getMobilePhone());
        ps.setString(7,o.getDateOfBirth()!=null?o.getDateOfBirth().toString():null); ps.setInt(8,o.getAge());
        ps.setString(9,o.getPlaceOfBirth()); ps.setString(10,o.getCurrentPosition()); ps.setInt(11,o.getYearsInService());
        ps.setString(12,o.getSchool()); ps.setString(13,o.getEmployeeNumber()); ps.setString(14,o.getSchoolAddress());
        ps.setString(15,o.getPresentAddress()); ps.setString(16,o.getEmergencyContactName());
        ps.setString(17,o.getEmergencyContactNo()); ps.setString(18,o.getPhotoPath()); ps.setString(19,o.getQrCodePath());
    }

    private void bindOfficialUpdate(PreparedStatement ps, Official o) throws SQLException {
        ps.setString(1,o.getLastName()); ps.setString(2,o.getFirstName()); ps.setString(3,o.getMiddleInitial());
        ps.setString(4,o.getSex()); ps.setString(5,o.getMobilePhone());
        ps.setString(6,o.getDateOfBirth()!=null?o.getDateOfBirth().toString():null); ps.setInt(7,o.getAge());
        ps.setString(8,o.getPlaceOfBirth()); ps.setString(9,o.getCurrentPosition()); ps.setInt(10,o.getYearsInService());
        ps.setString(11,o.getSchool()); ps.setString(12,o.getEmployeeNumber()); ps.setString(13,o.getSchoolAddress());
        ps.setString(14,o.getPresentAddress()); ps.setString(15,o.getEmergencyContactName());
        ps.setString(16,o.getEmergencyContactNo()); ps.setString(17,o.getPhotoPath()); ps.setString(18,o.getQrCodePath());
        ps.setInt(19,o.getId());
    }

    // ── Coach (mirrors Official) ───────────────────────────────────────────────

    public synchronized List<Coach> getAllCoaches() throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getAllCoaches");
        List<Coach> list = new ArrayList<>();
        ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM coaches ORDER BY last_name, first_name");
        while (rs.next()) { Coach c = mapCoach(rs); loadCoachChildren(c); list.add(c); }
        return list;
    }

    public synchronized List<Coach> searchCoaches(String q) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("searchCoaches" , q);
        List<Coach> list = new ArrayList<>();
        String like = "%" + q.toLowerCase() + "%";
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM coaches WHERE lower(last_name) LIKE ? OR lower(first_name) LIKE ? OR lower(school) LIKE ? OR coach_id LIKE ? ORDER BY last_name");
        ps.setString(1,like); ps.setString(2,like); ps.setString(3,like); ps.setString(4,like);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) { Coach c = mapCoach(rs); loadCoachChildren(c); list.add(c); }
        return list;
    }

    public synchronized Coach findCoachByCoachId(String coachId) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("findCoachByCoachId" , coachId);
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM coaches WHERE coach_id=?");
        ps.setString(1, coachId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            Coach c = mapCoach(rs);
            loadCoachChildren(c);
            return c;
        }
        return null;
    }

    public synchronized int saveCoach(Coach c) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("saveCoach" , c);
        return inTransaction(() -> saveCoachInternal(c));
    }

    private int saveCoachInternal(Coach c) throws SQLException {
        if (c.getCoachId() == null || c.getCoachId().isBlank())
            c.setCoachId(generateId("CCH", "coaches", "coach_id"));
        if (c.getId() == 0) {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO coaches (coach_id,last_name,first_name,middle_initial,sex,mobile_phone,date_of_birth,age,place_of_birth,current_position,years_in_service,school,employee_number,school_address,present_address,emergency_contact_name,emergency_contact_no,photo_path,qr_code_path) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            bindCoach(ps, c); ps.executeUpdate();
            ResultSet k = ps.getGeneratedKeys(); if (k.next()) c.setId(k.getInt(1));
        } else {
            PreparedStatement ps = connection.prepareStatement("UPDATE coaches SET last_name=?,first_name=?,middle_initial=?,sex=?,mobile_phone=?,date_of_birth=?,age=?,place_of_birth=?,current_position=?,years_in_service=?,school=?,employee_number=?,school_address=?,present_address=?,emergency_contact_name=?,emergency_contact_no=?,photo_path=?,qr_code_path=? WHERE id=?");
            bindCoachUpdate(ps, c); ps.executeUpdate();
        }
        saveCoachChildren(c);
        return c.getId();
    }

    public synchronized void deleteCoach(int id) throws SQLException {
        if (isRemote()) { RemoteDatabaseClient.call("deleteCoach" , id); return; }
        revokeVerification("coaches", "coach_id", "COACH", id);
        inTransaction(() -> {
            deleteChildren("coach", id);
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM coaches WHERE id=?")) {
                ps.setInt(1,id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public synchronized void updateCoachQrCode(int id, String qrCodePath) throws SQLException {
        if (isRemote()) { RemoteDatabaseClient.call("updateCoachQrCode" , id, qrCodePath); return; }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE coaches SET qr_code_path=? WHERE id=?")) {
            ps.setString(1, qrCodePath);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private void deleteChildren(String type, int parentId) throws SQLException {
        for (String suffix : List.of("education", "sports_training", "track_record")) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + type + "_" + suffix + " WHERE parent_id=?")) {
                ps.setInt(1, parentId);
                ps.executeUpdate();
            }
        }
    }

    private void saveCoachChildren(Coach c) throws SQLException {
        int id = c.getId();
        deleteChildren("coach", id);
        for (Official.EducationalQualification eq : c.getEducationalQualifications()) {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO coach_education (parent_id,level,course,school,year_graduated,credits_earned,awards_received) VALUES (?,?,?,?,?,?,?)")) {
                ps.setInt(1,id); ps.setString(2,eq.level); ps.setString(3,eq.course); ps.setString(4,eq.school);
                ps.setString(5,eq.yearGraduated); ps.setString(6,eq.creditsEarned); ps.setString(7,eq.awardsReceived); ps.executeUpdate();
            }
        }
        for (Official.SportsTraining st : c.getSportsTrainings()) {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO coach_sports_training (parent_id,title,date_of_training,num_hours,conducted_by) VALUES (?,?,?,?,?)")) {
                ps.setInt(1,id); ps.setString(2,st.title); ps.setString(3,st.dateOfTraining);
                ps.setInt(4,st.numberOfHours); ps.setString(5,st.conductedBy); ps.executeUpdate();
            }
        }
        for (Official.TrackRecord tr : c.getTrackRecords()) {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO coach_track_record (parent_id,meet_attended,inclusive_dates,event,awards_received) VALUES (?,?,?,?,?)")) {
                ps.setInt(1,id); ps.setString(2,tr.athleteMeetAttended); ps.setString(3,tr.inclusiveDates);
                ps.setString(4,tr.event); ps.setString(5,tr.awardsReceived); ps.executeUpdate();
            }
        }
    }

    private void loadCoachChildren(Coach c) throws SQLException {
        int id = c.getId();
        List<Official.EducationalQualification> eqs = new ArrayList<>();
        ResultSet r1 = connection.prepareStatement("SELECT * FROM coach_education WHERE parent_id=" + id).executeQuery();
        while (r1.next()) { Official.EducationalQualification eq = new Official.EducationalQualification();
            eq.level=r1.getString("level"); eq.course=r1.getString("course"); eq.school=r1.getString("school");
            eq.yearGraduated=r1.getString("year_graduated"); eq.creditsEarned=r1.getString("credits_earned");
            eq.awardsReceived=r1.getString("awards_received"); eqs.add(eq); }
        c.setEducationalQualifications(eqs);
        List<Official.SportsTraining> sts = new ArrayList<>();
        ResultSet r2 = connection.prepareStatement("SELECT * FROM coach_sports_training WHERE parent_id=" + id).executeQuery();
        while (r2.next()) { Official.SportsTraining st = new Official.SportsTraining();
            st.title=r2.getString("title"); st.dateOfTraining=r2.getString("date_of_training");
            st.numberOfHours=r2.getInt("num_hours"); st.conductedBy=r2.getString("conducted_by"); sts.add(st); }
        c.setSportsTrainings(sts);
        List<Official.TrackRecord> trs = new ArrayList<>();
        ResultSet r3 = connection.prepareStatement("SELECT * FROM coach_track_record WHERE parent_id=" + id).executeQuery();
        while (r3.next()) { Official.TrackRecord tr = new Official.TrackRecord();
            tr.athleteMeetAttended=r3.getString("meet_attended"); tr.inclusiveDates=r3.getString("inclusive_dates");
            tr.event=r3.getString("event"); tr.awardsReceived=r3.getString("awards_received"); trs.add(tr); }
        c.setTrackRecords(trs);
    }

    private Coach mapCoach(ResultSet rs) throws SQLException {
        Coach c = new Coach();
        c.setId(rs.getInt("id")); c.setCoachId(rs.getString("coach_id"));
        c.setLastName(rs.getString("last_name")); c.setFirstName(rs.getString("first_name"));
        c.setMiddleInitial(rs.getString("middle_initial")); c.setSex(rs.getString("sex"));
        c.setMobilePhone(rs.getString("mobile_phone"));
        String dob = rs.getString("date_of_birth"); if (dob!=null) c.setDateOfBirth(LocalDate.parse(dob));
        c.setAge(rs.getInt("age")); c.setPlaceOfBirth(rs.getString("place_of_birth"));
        c.setCurrentPosition(rs.getString("current_position")); c.setYearsInService(rs.getInt("years_in_service"));
        c.setSchool(rs.getString("school")); c.setEmployeeNumber(rs.getString("employee_number"));
        c.setSchoolAddress(rs.getString("school_address")); c.setPresentAddress(rs.getString("present_address"));
        c.setEmergencyContactName(rs.getString("emergency_contact_name")); c.setEmergencyContactNo(rs.getString("emergency_contact_no"));
        c.setPhotoPath(rs.getString("photo_path")); c.setQrCodePath(rs.getString("qr_code_path"));
        return c;
    }

    private void bindCoach(PreparedStatement ps, Coach c) throws SQLException {
        ps.setString(1,c.getCoachId()); ps.setString(2,c.getLastName()); ps.setString(3,c.getFirstName());
        ps.setString(4,c.getMiddleInitial()); ps.setString(5,c.getSex()); ps.setString(6,c.getMobilePhone());
        ps.setString(7,c.getDateOfBirth()!=null?c.getDateOfBirth().toString():null); ps.setInt(8,c.getAge());
        ps.setString(9,c.getPlaceOfBirth()); ps.setString(10,c.getCurrentPosition()); ps.setInt(11,c.getYearsInService());
        ps.setString(12,c.getSchool()); ps.setString(13,c.getEmployeeNumber()); ps.setString(14,c.getSchoolAddress());
        ps.setString(15,c.getPresentAddress()); ps.setString(16,c.getEmergencyContactName());
        ps.setString(17,c.getEmergencyContactNo()); ps.setString(18,c.getPhotoPath()); ps.setString(19,c.getQrCodePath());
    }

    private void bindCoachUpdate(PreparedStatement ps, Coach c) throws SQLException {
        ps.setString(1,c.getLastName()); ps.setString(2,c.getFirstName()); ps.setString(3,c.getMiddleInitial());
        ps.setString(4,c.getSex()); ps.setString(5,c.getMobilePhone());
        ps.setString(6,c.getDateOfBirth()!=null?c.getDateOfBirth().toString():null); ps.setInt(7,c.getAge());
        ps.setString(8,c.getPlaceOfBirth()); ps.setString(9,c.getCurrentPosition()); ps.setInt(10,c.getYearsInService());
        ps.setString(11,c.getSchool()); ps.setString(12,c.getEmployeeNumber()); ps.setString(13,c.getSchoolAddress());
        ps.setString(14,c.getPresentAddress()); ps.setString(15,c.getEmergencyContactName());
        ps.setString(16,c.getEmergencyContactNo()); ps.setString(17,c.getPhotoPath()); ps.setString(18,c.getQrCodePath());
        ps.setInt(19,c.getId());
    }

    // ── Equipment ─────────────────────────────────────────────────────────────

    public synchronized List<Equipment> getAllEquipment() throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getAllEquipment");
        List<Equipment> list = new ArrayList<>();
        ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM equipment ORDER BY id DESC");
        while (rs.next()) {
            Equipment e = mapEquipment(rs);
            e.setItems(getEquipmentItems(e.getId()));
            list.add(e);
        }
        return list;
    }

    public synchronized List<Equipment> searchEquipment(String q) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("searchEquipment" , q);
        List<Equipment> list = new ArrayList<>();
        String like = "%" + q.toLowerCase() + "%";
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM equipment WHERE lower(borrower_name) LIKE ? OR lower(school) LIKE ? OR lower(event) LIKE ? OR equipment_id LIKE ? ORDER BY id DESC");
        ps.setString(1,like); ps.setString(2,like); ps.setString(3,like); ps.setString(4,like);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Equipment e = mapEquipment(rs);
            e.setItems(getEquipmentItems(e.getId()));
            list.add(e);
        }
        return list;
    }

    public synchronized int saveEquipment(Equipment e) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("saveEquipment" , e);
        return inTransaction(() -> saveEquipmentInternal(e));
    }

    private int saveEquipmentInternal(Equipment e) throws SQLException {
        if (e.getEquipmentId() == null || e.getEquipmentId().isBlank())
            e.setEquipmentId(generateId("EQP", "equipment", "equipment_id"));
        if (e.getId() == 0) {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO equipment (equipment_id,borrower_name,designation,school,event,mobile_no,date,issued_by,issued_to) VALUES (?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            bindEquipment(ps,e); ps.executeUpdate();
            ResultSet k = ps.getGeneratedKeys(); if (k.next()) e.setId(k.getInt(1));
        } else {
            PreparedStatement ps = connection.prepareStatement("UPDATE equipment SET borrower_name=?,designation=?,school=?,event=?,mobile_no=?,date=?,issued_by=?,issued_to=? WHERE id=?");
            bindEquipmentUpdate(ps,e); ps.executeUpdate();
        }
        saveEquipmentItems(e);
        return e.getId();
    }

    public synchronized void deleteEquipment(int id) throws SQLException {
        if (isRemote()) { RemoteDatabaseClient.call("deleteEquipment" , id); return; }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM equipment WHERE id=?")) {
            ps.setInt(1,id);
            ps.executeUpdate();
        }
    }

    public synchronized void updateAthleteQrCode(int id, String qrCodePath) throws SQLException {
        if (isRemote()) { RemoteDatabaseClient.call("updateAthleteQrCode" , id, qrCodePath); return; }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE athletes SET qr_code_path=? WHERE id=?")) {
            ps.setString(1, qrCodePath);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public synchronized List<Equipment.EquipmentItem> getEquipmentItems(int equipmentId) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getEquipmentItems" , equipmentId);
        List<Equipment.EquipmentItem> items = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM equipment_items WHERE equipment_id=?");
        ps.setInt(1, equipmentId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Equipment.EquipmentItem item = new Equipment.EquipmentItem();
            item.id = rs.getInt("id"); item.equipmentId = equipmentId;
            item.qty = rs.getInt("qty"); item.unit = rs.getString("unit");
            item.makeAndDescription = rs.getString("make_and_description");
            String db = rs.getString("date_borrowed"); if (db!=null) item.dateBorrowed = LocalDate.parse(db);
            String dr = rs.getString("date_returned"); if (dr!=null) item.dateReturned = LocalDate.parse(dr);
            item.remarks = rs.getString("remarks");
            items.add(item);
        }
        return items;
    }

    private void saveEquipmentItems(Equipment e) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM equipment_items WHERE equipment_id=?")) {
            delete.setInt(1, e.getId());
            delete.executeUpdate();
        }
        if (e.getItems() == null) return;
        for (Equipment.EquipmentItem item : e.getItems()) {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO equipment_items (equipment_id,qty,unit,make_and_description,date_borrowed,date_returned,remarks) VALUES (?,?,?,?,?,?,?)")) {
                ps.setInt(1,e.getId()); ps.setInt(2,item.qty); ps.setString(3,item.unit);
                ps.setString(4,item.makeAndDescription);
                ps.setString(5,item.dateBorrowed!=null?item.dateBorrowed.toString():null);
                ps.setString(6,item.dateReturned!=null?item.dateReturned.toString():null);
                ps.setString(7,item.remarks); ps.executeUpdate();
            }
        }
    }

    private Equipment mapEquipment(ResultSet rs) throws SQLException {
        Equipment e = new Equipment();
        e.setId(rs.getInt("id")); e.setEquipmentId(rs.getString("equipment_id"));
        e.setBorrowerName(rs.getString("borrower_name")); e.setDesignation(rs.getString("designation"));
        e.setSchool(rs.getString("school")); e.setEvent(rs.getString("event"));
        e.setMobileNo(rs.getString("mobile_no"));
        String d = rs.getString("date"); if (d!=null) e.setDate(LocalDate.parse(d));
        e.setIssuedBy(rs.getString("issued_by")); e.setIssuedTo(rs.getString("issued_to"));
        return e;
    }

    private void bindEquipment(PreparedStatement ps, Equipment e) throws SQLException {
        ps.setString(1,e.getEquipmentId()); ps.setString(2,e.getBorrowerName()); ps.setString(3,e.getDesignation());
        ps.setString(4,e.getSchool()); ps.setString(5,e.getEvent()); ps.setString(6,e.getMobileNo());
        ps.setString(7,e.getDate()!=null?e.getDate().toString():null);
        ps.setString(8,e.getIssuedBy()); ps.setString(9,e.getIssuedTo());
    }

    private void bindEquipmentUpdate(PreparedStatement ps, Equipment e) throws SQLException {
        ps.setString(1,e.getBorrowerName()); ps.setString(2,e.getDesignation());
        ps.setString(3,e.getSchool()); ps.setString(4,e.getEvent()); ps.setString(5,e.getMobileNo());
        ps.setString(6,e.getDate()!=null?e.getDate().toString():null);
        ps.setString(7,e.getIssuedBy()); ps.setString(8,e.getIssuedTo());
        ps.setInt(9,e.getId());
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    private void createUserTable() throws SQLException {
        connection.createStatement().execute(
            "CREATE TABLE IF NOT EXISTS users (id " + idColumn() + ", " +
            "username TEXT UNIQUE, password_hash TEXT, role TEXT, full_name TEXT, active INTEGER DEFAULT 1, " +
            "must_change_password INTEGER DEFAULT 0)");
        if (!hasColumn("users", "must_change_password")) {
            connection.createStatement().execute(
                "ALTER TABLE users ADD COLUMN must_change_password INTEGER DEFAULT 0");
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, column)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) return true;
            }
        }
        try (ResultSet rs = connection.getMetaData().getColumns(null, null,
                table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }

    public synchronized boolean hasUsers() throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.available(SharingManager.getInstance().getConfig());
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public synchronized java.util.List<com.sdms.model.User> getAllUsers() throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getAllUsers");
        java.util.List<com.sdms.model.User> list = new ArrayList<>();
        ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM users ORDER BY username");
        while (rs.next()) list.add(mapUser(rs));
        return list;
    }

    public synchronized com.sdms.model.User findUserByUsername(String username) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("findUserByUsername" , username);
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM users WHERE username=?");
        ps.setString(1, username);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? mapUser(rs) : null;
    }

    public synchronized void saveUser(com.sdms.model.User u) throws SQLException {
        if (isRemote()) { RemoteDatabaseClient.call("saveUser" , u); return; }
        if (u.getId() == 0) {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (username, password_hash, role, full_name, active, must_change_password) VALUES (?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,u.getUsername()); ps.setString(2,u.getPasswordHash());
            ps.setString(3,u.getRole()); ps.setString(4,u.getFullName());
            ps.setInt(5, u.isActive() ? 1 : 0);
            ps.setInt(6, u.isMustChangePassword() ? 1 : 0);
            ps.executeUpdate();
            ResultSet k = ps.getGeneratedKeys(); if (k.next()) u.setId(k.getInt(1));
        } else {
            PreparedStatement ps = connection.prepareStatement(
                "UPDATE users SET username=?, password_hash=?, role=?, full_name=?, active=?, must_change_password=? WHERE id=?");
            ps.setString(1,u.getUsername()); ps.setString(2,u.getPasswordHash());
            ps.setString(3,u.getRole()); ps.setString(4,u.getFullName());
            ps.setInt(5, u.isActive() ? 1 : 0);
            ps.setInt(6, u.isMustChangePassword() ? 1 : 0);
            ps.setInt(7, u.getId());
            ps.executeUpdate();
        }
    }

    public synchronized void deleteUser(int id) throws SQLException {
        if (isRemote()) { RemoteDatabaseClient.call("deleteUser" , id); return; }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM users WHERE id=?")) {
            ps.setInt(1,id);
            ps.executeUpdate();
        }
    }

    private com.sdms.model.User mapUser(ResultSet rs) throws SQLException {
        com.sdms.model.User user = new com.sdms.model.User(
            rs.getInt("id"), rs.getString("username"), rs.getString("password_hash"),
            rs.getString("role"), rs.getString("full_name"), rs.getInt("active") == 1);
        user.setMustChangePassword(rs.getInt("must_change_password") == 1);
        return user;
    }

    public synchronized int countActiveAdmins() throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("countActiveAdmins");
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE role='ADMIN' AND active=1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ── Global Search ─────────────────────────────────────────────────────────

    public synchronized java.util.List<com.sdms.model.SearchResult> globalSearch(String query) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("globalSearch" , query);
        java.util.List<com.sdms.model.SearchResult> results = new ArrayList<>();
        String like = "%" + query.toLowerCase() + "%";

        PreparedStatement a = connection.prepareStatement(
            "SELECT id, athlete_id, last_name, first_name, school FROM athletes " +
            "WHERE lower(last_name) LIKE ? OR lower(first_name) LIKE ? OR athlete_id LIKE ? OR lower(school) LIKE ? LIMIT 20");
        a.setString(1,like); a.setString(2,like); a.setString(3,like); a.setString(4,like);
        ResultSet ra = a.executeQuery();
        while (ra.next()) results.add(new com.sdms.model.SearchResult(
            com.sdms.model.SearchResult.Module.ATHLETE, ra.getInt("id"),
            ra.getString("athlete_id"),
            ra.getString("last_name") + ", " + ra.getString("first_name"),
            ra.getString("school")));

        PreparedStatement o = connection.prepareStatement(
            "SELECT id, official_id, last_name, first_name, current_position, school FROM officials " +
            "WHERE lower(last_name) LIKE ? OR lower(first_name) LIKE ? OR official_id LIKE ? OR lower(school) LIKE ? LIMIT 20");
        o.setString(1,like); o.setString(2,like); o.setString(3,like); o.setString(4,like);
        ResultSet ro = o.executeQuery();
        while (ro.next()) results.add(new com.sdms.model.SearchResult(
            com.sdms.model.SearchResult.Module.OFFICIAL, ro.getInt("id"),
            ro.getString("official_id"),
            ro.getString("last_name") + ", " + ro.getString("first_name"),
            ro.getString("current_position") + " — " + ro.getString("school")));

        PreparedStatement c = connection.prepareStatement(
            "SELECT id, coach_id, last_name, first_name, current_position, school FROM coaches " +
            "WHERE lower(last_name) LIKE ? OR lower(first_name) LIKE ? OR coach_id LIKE ? OR lower(school) LIKE ? LIMIT 20");
        c.setString(1,like); c.setString(2,like); c.setString(3,like); c.setString(4,like);
        ResultSet rc = c.executeQuery();
        while (rc.next()) results.add(new com.sdms.model.SearchResult(
            com.sdms.model.SearchResult.Module.COACH, rc.getInt("id"),
            rc.getString("coach_id"),
            rc.getString("last_name") + ", " + rc.getString("first_name"),
            rc.getString("current_position") + " — " + rc.getString("school")));

        PreparedStatement e = connection.prepareStatement(
            "SELECT id, equipment_id, borrower_name, school, event FROM equipment " +
            "WHERE lower(borrower_name) LIKE ? OR equipment_id LIKE ? OR lower(school) LIKE ? OR lower(event) LIKE ? LIMIT 20");
        e.setString(1,like); e.setString(2,like); e.setString(3,like); e.setString(4,like);
        ResultSet re = e.executeQuery();
        while (re.next()) results.add(new com.sdms.model.SearchResult(
            com.sdms.model.SearchResult.Module.EQUIPMENT, re.getInt("id"),
            re.getString("equipment_id"), re.getString("borrower_name"),
            re.getString("event") + " — " + re.getString("school")));

        return results;
    }

    // ── School filter helpers ─────────────────────────────────────────────────

    public synchronized java.util.List<String> getAllSchools() throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getAllSchools");
        java.util.List<String> schools = new ArrayList<>();
        ResultSet rs = connection.createStatement().executeQuery(
            "SELECT DISTINCT school FROM athletes WHERE school IS NOT NULL AND school != '' " +
            "UNION SELECT DISTINCT school FROM officials WHERE school IS NOT NULL AND school != '' " +
            "UNION SELECT DISTINCT school FROM coaches WHERE school IS NOT NULL AND school != '' " +
            "ORDER BY school");
        while (rs.next()) schools.add(rs.getString(1));
        return schools;
    }

    public synchronized java.util.List<Athlete> getAthletesBySchool(String school) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getAthletesBySchool" , school);
        java.util.List<Athlete> list = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement(
            "SELECT * FROM athletes WHERE school=? ORDER BY last_name, first_name");
        ps.setString(1, school);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) list.add(mapAthlete(rs));
        return list;
    }

    public synchronized java.util.List<Official> getOfficialsBySchool(String school) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getOfficialsBySchool" , school);
        java.util.List<Official> list = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement(
            "SELECT * FROM officials WHERE school=? ORDER BY last_name, first_name");
        ps.setString(1, school);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) { Official o = mapOfficial(rs); loadOfficialChildren(o); list.add(o); }
        return list;
    }

    public synchronized java.util.List<Coach> getCoachesBySchool(String school) throws SQLException {
        if (isRemote()) return RemoteDatabaseClient.call("getCoachesBySchool" , school);
        java.util.List<Coach> list = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement(
            "SELECT * FROM coaches WHERE school=? ORDER BY last_name, first_name");
        ps.setString(1, school);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) { Coach c = mapCoach(rs); loadCoachChildren(c); list.add(c); }
        return list;
    }
}
