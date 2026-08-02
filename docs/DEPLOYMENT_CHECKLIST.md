# SDMS Deployment Checklist (Windows)

Use this checklist when preparing and installing SDMS in a school/division office.

## 1. Build Machine Prerequisites

- JDK 17 installed
- Maven installed
- jpackage available
- WiX Toolset CLI 5.0.2 installed
- Project builds successfully

Verification commands:
- java -version
- mvn -v
- jpackage --version
- wix --version

## 2. Pre-Build Checks

- Confirm logo icon file exists:
  - src/main/resources/images/app-logo.ico
- Confirm launcher class is AppLauncher in packaging settings
- Confirm shaded JAR builds:
  - target/sports-delegation-ms-1.5.0-shaded.jar

## 3. Build Installer

Recommended command:
- build-installer.cmd -CleanOutput

Expected output:
- dist/Athlete Track-1.5.0.exe

For later releases, increase the three-part version:
- build-installer.cmd -AppVersion 1.5.0

Do not change the upgrade UUID in `build-installer.ps1`. Users can close the
app and run the newer installer directly over the installed version. Their
database and files remain in the local application-data directory.

## 4. Install and Upgrade Validation

1. Run dist/SDMS-1.0.exe.
2. Complete installation.
3. Launch SDMS from Start menu or desktop shortcut.
4. Verify login screen appears.
5. Install a newer version over the existing version without uninstalling.
6. Verify the app version changed and existing records are still present.

## 5. Post-Install Functional Smoke Test

- Complete first-time setup by creating a unique administrator account
- Open each module:
  - Dashboard, Athletes, Officials, Coaches, Equipment
- Create one test record in each core module
- Export one ID card and one report
- Test batch print and roster export
- Start Multi-PC Sharing on a private test network
- Pair a second Windows computer and verify encoder changes are visible there
- Create and restore one `.atbackup` backup

## 6. Data Path Verification

Confirm these paths are created after first launch:
- %LOCALAPPDATA%/SDMS
- %LOCALAPPDATA%/SDMS/sdms.db
- %LOCALAPPDATA%/SDMS/data/photos
- %LOCALAPPDATA%/SDMS/data/qrcodes
- %LOCALAPPDATA%/SDMS/data/exports

## 7. Startup Failure Procedure

If app does not open:
1. Check log file:
   - %LOCALAPPDATA%/SDMS/logs/startup-error.log
2. Rebuild installer from latest source.
3. Reinstall and retest.

## 8. Backup and Restore Plan

### Backup
- Close SDMS
- Copy:
  - %LOCALAPPDATA%/SDMS/sdms.db
  - Optional: %LOCALAPPDATA%/SDMS/data

### Restore
- Close SDMS
- Replace sdms.db with backup file
- Reopen SDMS

## 9. Security and Operations

- Use a unique administrator password during first-time setup.
- Create role-specific user accounts.
- Deactivate unused accounts.
- Restrict installer distribution to authorized staff only.

## 10. Release Handover Package

Include these files for delivery:
- dist/Athlete Track-1.5.0.exe
- docs/USER_MANUAL.md
- docs/DEPLOYMENT_CHECKLIST.md
- docs/SYSTEM_DOCUMENTATION.md
- Optional: docs/DOCUMENTATION_PACK.md
