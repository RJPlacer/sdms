import { runtimeEnv, upsertVerificationRecord } from "../../../db/verification";

export const dynamic = "force-dynamic";

type SyncPayload = {
  token?: string;
  personId?: string;
  personType?: string;
  fullName?: string;
  school?: string;
  descriptorLabel?: string;
  descriptorValue?: string;
  status?: string;
  photoBase64?: string;
  photoContentType?: string;
};

function authorized(request: Request) {
  const configured = runtimeEnv().SYNC_API_KEY ?? "";
  const supplied = request.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ?? "";
  if (!configured || configured.length !== supplied.length) return false;
  let difference = 0;
  for (let i = 0; i < configured.length; i++) {
    difference |= configured.charCodeAt(i) ^ supplied.charCodeAt(i);
  }
  return difference === 0;
}

function clean(value: string | undefined, limit: number) {
  return (value ?? "").trim().slice(0, limit);
}

export async function POST(request: Request) {
  try {
    if (!authorized(request)) {
      return Response.json({ error: "Unauthorized" }, { status: 401 });
    }

    const payload = await request.json() as SyncPayload;
    const token = clean(payload.token, 128);
    const personId = clean(payload.personId, 64);
    const personType = clean(payload.personType, 24).toUpperCase();
    const fullName = clean(payload.fullName, 180);

    if (!/^[A-Za-z0-9_-]{32,128}$/.test(token)) {
      return Response.json({ error: "A valid secure token is required" }, { status: 400 });
    }
    if (!personId || !fullName || !["ATHLETE", "OFFICIAL", "COACH"].includes(personType)) {
      return Response.json({ error: "Invalid verification record" }, { status: 400 });
    }

    let photoKey: string | null = null;
    if (payload.photoBase64) {
      const contentType = clean(payload.photoContentType, 64).toLowerCase();
      if (!["image/jpeg", "image/png"].includes(contentType)) {
        return Response.json({ error: "Photo must be JPEG or PNG" }, { status: 400 });
      }
      const bytes = Uint8Array.from(atob(payload.photoBase64), character => character.charCodeAt(0));
      if (bytes.byteLength > 2_000_000) {
        return Response.json({ error: "Photo exceeds the 2 MB limit" }, { status: 413 });
      }
      photoKey = `verification/${token}`;
      await runtimeEnv().PHOTOS.put(photoKey, bytes, {
        httpMetadata: { contentType },
      });
    }

    await upsertVerificationRecord({
      token,
      personId,
      personType,
      fullName,
      school: clean(payload.school, 240),
      descriptorLabel: clean(payload.descriptorLabel, 40),
      descriptorValue: clean(payload.descriptorValue, 180),
      status: clean(payload.status, 16).toUpperCase() === "REVOKED" ? "REVOKED" : "ACTIVE",
      photoKey,
    });

    return Response.json({
      ok: true,
      verificationPath: `/verify/${token}`,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unexpected synchronization error";
    return Response.json({ error: message }, { status: 500 });
  }
}
