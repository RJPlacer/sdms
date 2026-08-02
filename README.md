# Sports Delegation Management System (SDMS)

JavaFX + SQLite desktop application for Palarong Pambansa delegation management.

## Documentation

For full system documentation (features, architecture, setup, packaging, troubleshooting, backup/restore), see:

- docs/SYSTEM_DOCUMENTATION.md
- docs/USER_MANUAL.md
- docs/DEPLOYMENT_CHECKLIST.md
- docs/DOCUMENTATION_PACK.md

## Quick Start

### Prerequisites

- JDK 17+ (Java 8 is not supported)
- Maven 3.8+

### Run in development

```bash
mvn javafx:run
```

Do not launch `MainApp` directly or open the plain/original JAR. The Maven
command supplies JavaFX on the runtime path and starts the application through
`AppLauncher`.

On a new database, the login screen opens in first-time setup mode. Create the
initial administrator account with a unique username and a password of at least
12 characters before entering the main window.

### Build shaded JAR

```bash
mvn -DskipTests package
```

Output:

- target/sports-delegation-ms-1.2.0-shaded.jar

Run the packaged JAR with JDK 17+:

```bash
java -jar target/sports-delegation-ms-1.2.0-shaded.jar
```

## Mobile QR Verification

The public verification portal is:

- https://athlete-track-verify-2026.renielplacer123456.chatgpt.site

The desktop app reads its synchronization settings from
`verification.properties` in the active application data directory. Start from
`verification.properties.example`; never commit the real API key. System
properties (`sdms.verification.baseUrl`, `sdms.verification.apiKey`) or the
environment variables `SDMS_VERIFICATION_BASE_URL` and
`SDMS_VERIFICATION_API_KEY` can be used instead.

When configured, saving or exporting an ID publishes only the approved public
profile and regenerates its QR code as an unguessable `/verify/{token}` URL.
Private contact, birth, guardian, emergency, and address fields are not sent.

### Moving the verification portal to another host

The desktop application is independent of ChatGPT, but the current public
verification URL is hosted by ChatGPT Sites. Keep the source in
`verification-portal/` so the portal can be redeployed elsewhere if the current
hosting or subscription changes.

#### Recommended option: your own Cloudflare account

This is the simplest migration because the portal already uses Cloudflare
Workers, D1, and R2. Cloudflare offers free-tier allowances, but check its
[current pricing](https://developers.cloudflare.com/workers/platform/pricing/)
before deployment. A purely static host such as GitHub Pages is **not** enough:
this portal needs a server-side synchronization API, database, object storage,
and a protected API secret.

Prerequisites:

- Node.js 22.13 or newer
- A Cloudflare account
- A password-manager-generated random secret of at least 32 characters
- Optional but strongly recommended: a domain you control, such as
  `verify.example.org`

From PowerShell, open the portal directory and prepare the deployment:

```powershell
cd verification-portal
npm install
npm run build
npx wrangler login
npx wrangler d1 create athlete-track-verification-db
npx wrangler r2 bucket create athlete-track-verification-photos
```

The D1 command prints a `database_id`. Create
`verification-portal/wrangler.deploy.jsonc` with the following contents and
replace `PASTE_DATABASE_ID_HERE` with that ID:

```jsonc
{
  "$schema": "./node_modules/wrangler/config-schema.json",
  "name": "athlete-track-verification",
  "main": "dist/server/index.js",
  "compatibility_date": "2026-05-15",
  "compatibility_flags": ["nodejs_compat"],
  "no_bundle": true,
  "assets": {
    "directory": "dist/client",
    "binding": "ASSETS"
  },
  "d1_databases": [
    {
      "binding": "DB",
      "database_name": "athlete-track-verification-db",
      "database_id": "PASTE_DATABASE_ID_HERE",
      "migrations_dir": "drizzle"
    }
  ],
  "r2_buckets": [
    {
      "binding": "PHOTOS",
      "bucket_name": "athlete-track-verification-photos"
    }
  ]
}
```

Do not copy the `project_id` from `.openai/hosting.json`; that identifier is for
the existing ChatGPT Sites deployment. Apply the database schema, store the
synchronization secret, and deploy:

```powershell
npx wrangler d1 migrations apply athlete-track-verification-db --remote --config wrangler.deploy.jsonc
npx wrangler secret put SYNC_API_KEY --config wrangler.deploy.jsonc
npx wrangler deploy --config wrangler.deploy.jsonc
```

When prompted for `SYNC_API_KEY`, enter the new secret. Do not put the secret in
the README, Git, `wrangler.deploy.jsonc`, or a QR code. Wrangler will display the
new public URL after deployment.

#### Connect the desktop application to the new portal

Copy `verification.properties.example` to the active application data
directory as `verification.properties`. For an installed Windows build, this is
normally `%LOCALAPPDATA%\SDMS\verification.properties`. Set:

```properties
baseUrl=https://your-new-host.example
apiKey=the-same-value-entered-for-SYNC_API_KEY
```

Do not add a trailing `/` to `baseUrl`. Restart SDMS, then save or export each
person again to publish the public records and photos to the new host. The
secure verification tokens remain in the local SDMS database, but the QR URL's
hostname changes.

Test the migration before using it in production:

1. Open the new host URL in a browser.
2. Save or export one test person in SDMS and confirm no synchronization error
   appears.
3. Scan the newly generated QR code on a phone using mobile data, not only the
   office Wi-Fi.
4. Confirm the name, ID, type, school/office, role, photo, status, and updated
   date are correct.
5. Confirm that contact, birth, guardian, emergency, and residential details
   are not displayed.
6. Test an invalid token and verify that it shows “Record not found.”
7. Back up the local SDMS database and keep the portal source in Git.

#### Preserve already printed QR codes

QR codes contain the complete verification URL. Changing hosts does not update
cards that have already been printed. Those cards must be re-exported and
reprinted unless the old hostname can redirect every `/verify/{token}` request
to the new hostname while preserving the path.

For long-term use, configure a custom domain you control **before** printing a
large batch, for example `verify.example.org`. Point that domain to the current
provider and use it as `baseUrl`. If hosting changes later, move the same domain
to the new provider so the printed QR URLs remain unchanged. See Cloudflare's
[Custom Domains documentation](https://developers.cloudflare.com/workers/configuration/routing/custom-domains/).

#### Other hosting providers

The interface can also be hosted on Vercel, Netlify, Render, Railway, a VPS, or
another provider, but the current code is not a static website and is not a
drop-in deployment on those platforms. A migration must replace:

- Cloudflare D1 with a supported SQL database;
- Cloudflare R2 with object storage for photos;
- the `cloudflare:workers` runtime bindings in `db/verification.ts`; and
- the Worker build/deployment configuration.

Keep the same routes and request contract so the desktop app needs only a
`baseUrl` and API-key change:

- `POST /api/records` accepts the authenticated public-profile synchronization;
- `GET /verify/{token}` displays a public verification record; and
- `GET /photo/{token}` returns the optional public photo.

Whichever provider is used, store `SYNC_API_KEY` as a server-side secret, use
HTTPS, keep the verification tokens unguessable, and never expose the local
SQLite database or private person fields.

## Build Windows Installer

One-time build-machine prerequisite: install WiX Toolset CLI 5.0.2. Newer WiX
7 releases require separate licensing acceptance and are not used by this
build:

```powershell
winget install --id WiXToolset.WiXCLI --exact --version 5.0.2.0
```

Use the one-command script:

```powershell
build-installer.cmd -CleanOutput
```

Or package only:

```powershell
build-installer.cmd -SkipBuild -CleanOutput
```

Installer output:

- dist/Athlete Track-1.2.0.exe

### Updating an installed copy

The Windows package now has a permanent upgrade identity. To publish a newer
release, choose a version greater than the installed version and run:

```powershell
build-installer.cmd -AppVersion 1.2.0
```

Give users the resulting `Athlete Track-1.2.0.exe`. They should close Athlete
Track and run the new installer normally. Windows replaces the installed
program in place; users do not uninstall the old version first.

The SQLite database, photos, QR codes, exports, logs, and verification settings
are stored under the user's local application-data directory, outside the
installation directory, so an in-place upgrade preserves them.

Version numbers must increase and use three numeric parts, such as `1.1.0`,
`1.1.1`, then `1.2.0`. The script passes the same version to Maven, the app's
About screen, and the Windows installer.

## Current Runtime Data Paths (Windows)

- New installs: `%LOCALAPPDATA%/Athlete Track`
- Legacy installs continue using: `%LOCALAPPDATA%/SDMS`
- Database: `<app-data>/sdms.db`
- Photos, QR codes, and exports: `<app-data>/data`
- Startup logs: `<app-data>/logs/startup-error.log`
