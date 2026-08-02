import {
  database,
  hashSecret,
  isExpired,
  loadSession,
  randomSecret,
  textResponse,
} from "../../../_shared";

export const dynamic = "force-dynamic";

export async function POST(_request: Request, context: { params: Promise<{ sessionId: string }> }) {
  const { sessionId } = await context.params;
  const session = await loadSession(sessionId);
  if (!session) return textResponse("Session not found", 404);
  if (isExpired(session)) return textResponse("Session expired", 410);
  if (session.phone_secret_hash) return textResponse("This session is already paired", 409);

  const phoneSecret = randomSecret();
  const now = new Date().toISOString();
  const result = await database().prepare(
    `UPDATE scanner_sessions
        SET phone_secret_hash = ?, paired_at = ?, last_activity_at = ?
      WHERE session_id = ? AND phone_secret_hash IS NULL`
  ).bind(await hashSecret(phoneSecret), now, now, sessionId).run();

  if (result.meta.changes !== 1) return textResponse("This session is already paired", 409);
  return textResponse(phoneSecret);
}
