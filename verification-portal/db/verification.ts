import { env } from "cloudflare:workers";

export type VerificationRecord = {
  token: string;
  person_id: string;
  person_type: string;
  full_name: string;
  school: string;
  descriptor_label: string;
  descriptor_value: string;
  status: string;
  photo_key: string | null;
  issued_at: string;
  updated_at: string;
};

type RuntimeEnv = {
  DB: D1Database;
  PHOTOS: R2Bucket;
  SYNC_API_KEY?: string;
};

export function runtimeEnv() {
  return env as unknown as RuntimeEnv;
}

export async function getVerificationRecord(token: string) {
  return runtimeEnv().DB.prepare(
    `SELECT token, person_id, person_type, full_name, school,
            descriptor_label, descriptor_value, status, photo_key,
            issued_at, updated_at
       FROM verification_records
      WHERE token = ?`
  ).bind(token).first<VerificationRecord>();
}

export async function upsertVerificationRecord(record: {
  token: string;
  personId: string;
  personType: string;
  fullName: string;
  school: string;
  descriptorLabel: string;
  descriptorValue: string;
  status: string;
  photoKey: string | null;
}) {
  const database = runtimeEnv().DB;
  await database.prepare(
    `INSERT INTO verification_records
       (token, person_id, person_type, full_name, school,
        descriptor_label, descriptor_value, status, photo_key, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT(person_id) DO UPDATE SET
       token = excluded.token,
       person_type = excluded.person_type,
       full_name = excluded.full_name,
       school = excluded.school,
       descriptor_label = excluded.descriptor_label,
       descriptor_value = excluded.descriptor_value,
       status = excluded.status,
       photo_key = COALESCE(excluded.photo_key, verification_records.photo_key),
       updated_at = CURRENT_TIMESTAMP`
  ).bind(
    record.token,
    record.personId,
    record.personType,
    record.fullName,
    record.school,
    record.descriptorLabel,
    record.descriptorValue,
    record.status,
    record.photoKey,
  ).run();
}
