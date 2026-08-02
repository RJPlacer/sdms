import {
  MAX_PAYLOAD_BYTES,
  database,
  headerSecretMatches,
  isExpired,
  loadSession,
  noStoreHeaders,
  textResponse,
} from "../../../_shared";

export const dynamic = "force-dynamic";

type RouteContext = { params: Promise<{ sessionId: string }> };

async function activeSession(context: RouteContext) {
  const { sessionId } = await context.params;
  const session = await loadSession(sessionId);
  return { sessionId, session };
}

export async function POST(request: Request, context: RouteContext) {
  const { sessionId, session } = await activeSession(context);
  if (!session) return textResponse("Session not found", 404);
  if (isExpired(session)) return textResponse("Session expired", 410);
  if (!await headerSecretMatches(request, "x-phone-secret", session.phone_secret_hash)) {
    return textResponse("Unauthorized", 401);
  }

  const payload = await request.text();
  const size = new TextEncoder().encode(payload).byteLength;
  if (!payload.trim() || size > MAX_PAYLOAD_BYTES) {
    return textResponse("QR payload must be between 1 and 2048 bytes", 400);
  }

  const now = new Date().toISOString();
  await database().batch([
    database().prepare(
      "INSERT INTO scanner_events (session_id, payload, created_at) VALUES (?, ?, ?)"
    ).bind(sessionId, payload.trim(), now),
    database().prepare(
      "UPDATE scanner_sessions SET last_activity_at = ? WHERE session_id = ?"
    ).bind(now, sessionId),
  ]);
  return textResponse("Accepted", 202);
}

export async function GET(request: Request, context: RouteContext) {
  const { sessionId, session } = await activeSession(context);
  if (!session) return textResponse("Session not found", 404);
  if (isExpired(session)) return textResponse("Session expired", 410);
  if (!await headerSecretMatches(request, "x-desktop-secret", session.desktop_secret_hash)) {
    return textResponse("Unauthorized", 401);
  }

  const event = await database().prepare(
    `UPDATE scanner_events SET consumed_at = ?
      WHERE id = (
        SELECT id FROM scanner_events
         WHERE session_id = ? AND consumed_at IS NULL
         ORDER BY id LIMIT 1
      )
      RETURNING payload`
  ).bind(new Date().toISOString(), sessionId).first<{ payload: string }>();

  if (!event) {
    return new Response(null, {
      status: 204,
      headers: noStoreHeaders({ "X-Scanner-Paired": session.paired_at ? "true" : "false" }),
    });
  }
  return textResponse(event.payload, 200, { "X-Scanner-Paired": "true" });
}

export async function DELETE(request: Request, context: RouteContext) {
  const { sessionId, session } = await activeSession(context);
  if (!session) return new Response(null, { status: 204, headers: noStoreHeaders() });
  if (!await headerSecretMatches(request, "x-desktop-secret", session.desktop_secret_hash)) {
    return textResponse("Unauthorized", 401);
  }
  await database().prepare("DELETE FROM scanner_sessions WHERE session_id = ?").bind(sessionId).run();
  return new Response(null, { status: 204, headers: noStoreHeaders() });
}
