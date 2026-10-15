package com.sdms.sharing;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;

/** One-time copy from the existing local SQLite database to a new shared H2 database. */
public final class DatabaseMigrationService {

    private static final List<String> TABLES = List.of(
        "athletes", "athlete_palaro_previous", "athlete_lower_meets",
        "officials", "official_education", "official_sports_training", "official_track_record",
        "coaches", "coach_education", "coach_sports_training", "coach_track_record",
        "equipment", "equipment_items", "users", "id_sequences", "legacy_import_rows", "legacy_import_map"
    );

    private DatabaseMigrationService() {}

    public static void migrateLocalSqliteToShared(Path sqliteFile, Connection target) throws Exception {
        if (!java.nio.file.Files.isRegularFile(sqliteFile)) return;
        try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile)) {
            boolean previousAutoCommit = target.getAutoCommit();
            target.setAutoCommit(false);
            try (Statement control = target.createStatement()) {
                control.execute("SET REFERENTIAL_INTEGRITY FALSE");
                for (int i = TABLES.size() - 1; i >= 0; i--) {
                    control.executeUpdate("DELETE FROM " + TABLES.get(i));
                }
                for (String table : TABLES) copyTable(source, target, table);
                for (String table : TABLES) restartIdentity(target, table);
                control.execute("SET REFERENTIAL_INTEGRITY TRUE");
                target.commit();
            } catch (Exception e) {
                target.rollback();
                throw e;
            } finally {
                target.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private static void copyTable(Connection source, Connection target, String table) throws Exception {
        try (ResultSet available = source.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
            if (!available.next()) {
                if (List.of("id_sequences", "legacy_import_rows", "legacy_import_map").contains(table)) return;
                throw new java.sql.SQLException("Missing source table: " + table);
            }
        }
        try (Statement sourceStatement = source.createStatement();
             ResultSet rows = sourceStatement.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData meta = rows.getMetaData();
            int columns = meta.getColumnCount();
            StringBuilder names = new StringBuilder();
            StringBuilder values = new StringBuilder();
            for (int i = 1; i <= columns; i++) {
                if (i > 1) { names.append(','); values.append(','); }
                names.append(meta.getColumnName(i));
                values.append('?');
            }
            String sql = "INSERT INTO " + table + " (" + names + ") VALUES (" + values + ")";
            try (PreparedStatement insert = target.prepareStatement(sql)) {
                while (rows.next()) {
                    for (int i = 1; i <= columns; i++) {
                        Object value = rows.getObject(i);
                        String column = meta.getColumnName(i);
                        if (value instanceof String text && ("photo_path".equalsIgnoreCase(column)
                                || "qr_code_path".equalsIgnoreCase(column))) {
                            value = SharedAssetService.hostImportLegacy(text,
                                "photo_path".equalsIgnoreCase(column) ? "photos" : "qrcodes");
                        }
                        insert.setObject(i, value);
                    }
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }
    }

    private static void restartIdentity(Connection target, String table) {
        try (Statement statement = target.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM " + table)) {
            int next = rs.next() ? rs.getInt(1) : 1;
            statement.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + Math.max(1, next));
        } catch (Exception ignored) {
            // Older or empty databases may not expose an identity sequence yet.
        }
    }
}
