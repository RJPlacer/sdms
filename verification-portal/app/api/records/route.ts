import { runtimeEnv, upsertVerificationRecord } from "../../../db/verification";

export const dynamic = "force-dynamic";
const MAX_PHOTO_BYTES = 2_000_000;
const MAX_REQUEST_BYTES = 3_000_000;

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

class RequestError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

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

async function readPayload(request: Request): Promise<SyncPayload> {
  const declaredLength = Number(request.headers.get("content-length") ?? "0");
  if (Number.isFinite(declaredLength) && declaredLength > MAX_REQUEST_BYTES) {
    throw new RequestError("Request exceeds the 3 MB limit", 413);
  }
  if (!request.body) throw new RequestError("A JSON request body is required", 400);

  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > MAX_REQUEST_BYTES) {
      await reader.cancel();
      throw new RequestError("Request exceeds the 3 MB limit", 413);
    }
    chunks.push(value);
  }

  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try {
    return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)) as SyncPayload;
  } catch {
    throw new RequestError("Request body must be valid UTF-8 JSON", 400);
  }
}

function decodePhoto(value: string): Uint8Array {
  const base64 = value.trim();
  const decodedSize =
    (base64.length / 4) * 3 -
    (base64.endsWith("==") ? 2 : base64.endsWith("=") ? 1 : 0);
  if (
    base64.length % 4 !== 0 ||
    !/^[A-Za-z0-9+/]*={0,2}$/.test(base64) ||
    decodedSize > MAX_PHOTO_BYTES
  ) {
    throw new RequestError("Photo exceeds the 2 MB limit or is not valid Base64", 413);
  }
  try {
    return Uint8Array.from(atob(base64), character => character.charCodeAt(0));
  } catch {
    throw new RequestError("Photo is not valid Base64", 400);
  }
}

export async function POST(request: Request) {
  try {
    if (!authorized(request)) {
      return Response.json({ error: "Unauthorized" }, { status: 401 });
    }

    const payload = await readPayload(request);
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
      const bytes = decodePhoto(payload.photoBase64);
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
    if (error instanceof RequestError) {
      return Response.json({ error: error.message }, { status: error.status });
    }
    console.error("Verification synchronization failed", error);
    return Response.json({ error: "Synchronization failed" }, { status: 500 });
  }
}
