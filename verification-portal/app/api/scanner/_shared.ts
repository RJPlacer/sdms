import { runtimeEnv } from "../../../db/verification";

export const SESSION_MINUTES = 15;
export const MAX_PAYLOAD_BYTES = 2_048;

export type ScannerSessionRow = {
  session_id: string;
  desktop_secret_hash: string;
  phone_secret_hash: string | null;
  expires_at: string;
  paired_at: string | null;
};

export function database() {
  return runtimeEnv().DB;
}

export function noStoreHeaders(extra: HeadersInit = {}) {
  return { "Cache-Control": "no-store", ...extra };
}

export function textResponse(body: string, status = 200, extra: HeadersInit = {}) {
  return new Response(body, {
    status,
    headers: noStoreHeaders({ "Content-Type": "text/plain; charset=utf-8", ...extra }),
  });
}

export function randomSecret(bytes = 32) {
  const value = crypto.getRandomValues(new Uint8Array(bytes));
  return btoa(String.fromCharCode(...value))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

export async function hashSecret(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}

export function bearerAuthorized(request: Request) {
  const configured = runtimeEnv().SYNC_API_KEY ?? "";
  const supplied = request.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ?? "";
  return configured.length > 0 && constantTimeEqual(configured, supplied);
}

export function constantTimeEqual(left: string, right: string) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index++) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
}

export async function loadSession(sessionId: string) {
  if (!/^[A-Za-z0-9_-]{24,64}$/.test(sessionId)) return null;
  return database().prepare(
    `SELECT session_id, desktop_secret_hash, phone_secret_hash, expires_at, paired_at
       FROM scanner_sessions WHERE session_id = ?`
  ).bind(sessionId).first<ScannerSessionRow>();
}

export function isExpired(session: ScannerSessionRow) {
  return Date.parse(session.expires_at) <= Date.now();
}

export async function headerSecretMatches(request: Request, name: string, expectedHash: string | null) {
  const supplied = request.headers.get(name) ?? "";
  if (!supplied || !expectedHash) return false;
  return constantTimeEqual(await hashSecret(supplied), expectedHash);
}
