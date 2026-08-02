import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("../", import.meta.url);

test("build contains the deployable portal and hosting metadata", async () => {
  await Promise.all([
    access(new URL("dist/server/index.js", root)),
    access(new URL("dist/.openai/hosting.json", root)),
    access(new URL("dist/.openai/drizzle/0000_melodic_the_hood.sql", root)),
    access(new URL("public/og.png", root)),
  ]);
});

test("landing page has final portal content and no starter preview", async () => {
  const page = await readFile(new URL("app/page.tsx", root), "utf8");
  const layout = await readFile(new URL("app/layout.tsx", root), "utf8");
  assert.match(page, /Athlete Track Verification/);
  assert.match(page, /Privacy protected/);
  assert.match(layout, /og\.png/);
  assert.doesNotMatch(`${page}\n${layout}`, /codex-preview|SkeletonPreview/);
});

test("synchronization API requires authorization and limits public fields", async () => {
  const api = await readFile(new URL("app/api/records/route.ts", root), "utf8");
  const verificationPage = await readFile(new URL("app/verify/[token]/page.tsx", root), "utf8");
  const photoRoute = await readFile(new URL("app/photo/[token]/route.ts", root), "utf8");
  assert.match(api, /authorization/);
  assert.match(api, /SYNC_API_KEY/);
  assert.match(api, /2_000_000/);
  assert.match(api, /MAX_REQUEST_BYTES/);
  assert.match(api, /error: "Synchronization failed"/);
  assert.match(photoRoute, /record\.status !== "ACTIVE"/);
  assert.match(photoRoute, /cache-control", "no-store"/);
  assert.doesNotMatch(
    verificationPage,
    /record\.(date_of_birth|present_address|guardian|mobile_phone|contact_no)/,
  );
});
