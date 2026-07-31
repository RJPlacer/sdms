# SDMS Deployment Checklist (Windows)

Use this checklist when preparing and installing SDMS in a school/division office.

## 1. Build Machine Prerequisites

- JDK 17 installed
- Maven installed
- jpackage available
- Project builds successfully

Verification commands:
- java -version
- mvn -v
- jpackage --version

## 2. Pre-Build Checks

- Confirm logo icon file exists:
  - src/main/resources/images/app-logo.ico
- Confirm launcher class is AppLauncher in packaging settings
- Confirm shaded JAR builds:
  - target/sports-delegation-ms-1.0-SNAPSHOT-shaded.jar

## 3. Build Installer

Recommended command:
- ./build-installer.ps1 -CleanOutput

Expected output:
- dist/SDMS-1.0.exe

## 4. Install Validation (Clean PC)

1. Run dist/SDMS-1.0.exe.
2. Complete installation.
3. Launch SDMS from Start menu or desktop shortcut.
4. Verify login screen appears.

## 5. Post-Install Functional Smoke Test

- Login using default admin (first run only):
  - admin / admin123
- Open each module:
  - Dashboard, Athletes, Officials, Coaches, Equipment
- Create one test record in each core module
- Export one ID card and one report
- Test batch print and roster export

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

- Change default admin password immediately after deployment.
- Create role-specific user accounts.
- Deactivate unused accounts.
- Restrict installer distribution to authorized staff only.

## 10. Release Handover Package

Include these files for delivery:
- dist/SDMS-1.0.exe
- docs/USER_MANUAL.md
- docs/DEPLOYMENT_CHECKLIST.md
- docs/SYSTEM_DOCUMENTATION.md
- Optional: docs/DOCUMENTATION_PACK.md
