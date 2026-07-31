# Athlete Track Verification Portal

Mobile-friendly public verification for Athlete Track ID cards.

## Public data

The portal intentionally exposes only the person's photo, name, registration
ID, athlete/official/coach type, school or office, sex or position, record
status, and last-updated date. Private contact, birth, guardian, emergency, and
residential information remains in the desktop database.

## Architecture

- Cloudflare D1 stores public verification records.
- Cloudflare R2 stores optional public ID photos.
- `/verify/[token]` displays a record using an unguessable token.
- `/api/records` accepts authenticated synchronization from the SDMS desktop app.
- `SYNC_API_KEY` must be configured as a hosted secret.

## Local validation

```bash
npm install
npm run db:generate
npm run build
node --test tests/rendered-html.test.mjs
```

## Independent hosting

For instructions to migrate this portal to a personal Cloudflare account,
another provider, or a stable custom domain, see **Moving the verification
portal to another host** in the repository's main `README.md`.
