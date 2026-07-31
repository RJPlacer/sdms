import { sql } from "drizzle-orm";
import { sqliteTable, text } from "drizzle-orm/sqlite-core";

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
