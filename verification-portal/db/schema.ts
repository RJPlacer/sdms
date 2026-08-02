import { sql } from "drizzle-orm";
import { index, integer, sqliteTable, text } from "drizzle-orm/sqlite-core";

export const verificationRecords = sqliteTable("verification_records", {
  token: text("token").primaryKey(),
  personId: text("person_id").notNull().unique(),
  personType: text("person_type").notNull(),
  fullName: text("full_name").notNull(),
  school: text("school").notNull().default(""),
  descriptorLabel: text("descriptor_label").notNull().default(""),
  descriptorValue: text("descriptor_value").notNull().default(""),
  status: text("status").notNull().default("ACTIVE"),
  photoKey: text("photo_key"),
  issuedAt: text("issued_at").notNull().default(sql`CURRENT_TIMESTAMP`),
  updatedAt: text("updated_at").notNull().default(sql`CURRENT_TIMESTAMP`),
});

export const scannerSessions = sqliteTable("scanner_sessions", {
  sessionId: text("session_id").primaryKey(),
  desktopSecretHash: text("desktop_secret_hash").notNull(),
  phoneSecretHash: text("phone_secret_hash"),
  createdAt: text("created_at").notNull(),
  expiresAt: text("expires_at").notNull(),
  pairedAt: text("paired_at"),
  lastActivityAt: text("last_activity_at").notNull(),
}, table => [index("scanner_sessions_expires_at_idx").on(table.expiresAt)]);

export const scannerEvents = sqliteTable("scanner_events", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  sessionId: text("session_id").notNull()
    .references(() => scannerSessions.sessionId, { onDelete: "cascade" }),
  payload: text("payload").notNull(),
  createdAt: text("created_at").notNull(),
  consumedAt: text("consumed_at"),
}, table => [index("scanner_events_session_id_idx").on(table.sessionId, table.id)]);
