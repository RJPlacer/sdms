import { getVerificationRecord, runtimeEnv } from "../../../db/verification";

export const dynamic = "force-dynamic";

export async function GET(_request: Request, context: { params: Promise<{ token: string }> }) {
  const { token } = await context.params;
  if (!/^[A-Za-z0-9_-]{32,128}$/.test(token)) {
    return new Response("Not found", { status: 404 });
  }

  const record = await getVerificationRecord(token);
  if (!record?.photo_key || record.status !== "ACTIVE") {
    return new Response("Not found", { status: 404 });
  }

  const image = await runtimeEnv().PHOTOS.get(record.photo_key);
  if (!image) return new Response("Not found", { status: 404 });

  const headers = new Headers();
  image.writeHttpMetadata(headers);
  headers.set("etag", image.httpEtag);
  headers.set("cache-control", "no-store");
  headers.set("x-content-type-options", "nosniff");
  return new Response(image.body, { headers });
}
