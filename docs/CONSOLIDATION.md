# Athlete Track consolidation

Version 1.7.0 continues the RJPlacer/sdms codebase and preserves the equipment Word export from RJPlacer/athlete-track, including the existing signature-name formatting edit.

## Application changes

- Shared computers now use encrypted, authenticated record requests. The main computer retains its database credentials and does not expose H2 over TCP. Viewer, encoder, and administrator permissions are checked on the main computer on every request; deactivated accounts immediately lose access.
- Initial pairing uses trust on first use: pair on a trusted private network with the code shown by the administrator. The client saves the main computer's certificate fingerprint and rejects a changed identity. Existing 1.6.2 clients must update and pair again.
- Photos require account sign-in. Encoders and administrators can upload photos; viewers can generate/read ID QR images without editing personal records.
- Configured ID exports publish verification URLs. Deleting a published identity revokes its online status first; an unavailable configured portal prevents deletion until revocation can succeed. With verification unconfigured, local QR payloads remain available.
- Persistent local ID sequences prevent ID reuse after deleting the highest numbered record.
- The known legacy administrator password must be changed at sign-in. New databases use first-time administrator setup.
- Equipment records can export both SDO PDF and Word documents.
- Older learner-number and employee-number QR stickers remain readable. A code matching several imported records requires the current record ID, rather than guessing a person.
- A failed initial sharing migration can be retried without leaving an inaccessible database. Shared backups include preserved legacy import metadata and are serialized with record operations.

## Local data consolidation

The task's private `.consolidation` folder contains verified source archives, consistent SQLite snapshots, an offline shared-database copy, a migration report, and account mappings. It is excluded from Git because it contains personal records and account data.

The migrated copy combines the workspace database, the installed local/shared database, and both Desktop Swing databases. Exact supported-field duplicates are skipped. Conflicting business IDs and usernames receive an import suffix rather than overwriting another record. Original source rows and their target mappings are retained in `legacy_import_rows` and `legacy_import_map`; original plaintext account passwords are converted to hashes in the migrated database. Fields without a current form equivalent remain in that private archive and the source backups.

All imported accounts require a password change on first sign-in. The existing main-computer administrator account keeps its username. The older Desktop administrator account is renamed when its username conflicts; consult the private account mapping before signing into an imported account.

The original folders and runtime data are retained as recoverable archives. Keep those archives until the updated application has been used to check records, photos, and exported forms.

## Validation

Regression tests cover server-side roles, denial of raw SQL access, certificate pin mismatch, deactivation, a separate-JVM shared client, nested records, generated IDs, QR publishing and revocation, migration recovery, backup restore, and Word export. CI runs Java verification on Windows and Linux, plus the portal build, tests, and lint.

The consolidation also checks all 20 JavaFX screens against the migrated records and renders a representative Word export in Microsoft Word. The live public portal and Android APK are not redeployed by this change.
