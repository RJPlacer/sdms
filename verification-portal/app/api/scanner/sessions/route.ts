import {
  SESSION_MINUTES,
  bearerAuthorized,
  database,
  hashSecret,
  randomSecret,
  textResponse,
} from "../_shared";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  if (!bearerAuthorized(request)) return textResponse("Unauthorized", 401);

  const now = new Date();
  const expiresAt = new Date(now.getTime() + SESSION_MINUTES * 60_000);
  const sessionId = randomSecret(24);
  const desktopSecret = randomSecret();
  const db = database();

  await db.prepare("DELETE FROM scanner_sessions WHERE expires_at <= ?")
    .bind(now.toISOString()).run();
  await db.prepare(
    `INSERT INTO scanner_sessions
       (session_id, desktop_secret_hash, created_at, expires_at, last_activity_at)
     VALUES (?, ?, ?, ?, ?)`
  ).bind(
    sessionId,
    await hashSecret(desktopSecret),
    now.toISOString(),
    expiresAt.toISOString(),
    now.toISOString(),
  ).run();

  const pairUrl = new URL(`/scan/${sessionId}`, request.url).toString();
  return textResponse([sessionId, desktopSecret, pairUrl, expiresAt.toISOString()].join("\n"), 201);
}
