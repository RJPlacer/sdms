# Sports Delegation Management System (SDMS)

Comprehensive documentation for the SDMS desktop application.

## 1. System Overview

SDMS is a JavaFX desktop system for managing Palarong Pambansa delegation data, including:
- Athletes
- Officials
- Coaches
- Equipment borrowing records
- User accounts and role-based access
- QR code generation
- ID card and report PDF export
- Batch ID export and school-based delegation rosters

The system uses SQLite for local persistence and runs as a standalone Windows application via jpackage installer.

## 2. Technology Stack

- Java 17
- JavaFX 21.0.2
- Maven
- SQLite (xerial sqlite-jdbc)
- ZXing (QR generation)
- OpenPDF (PDF generation)
- ControlsFX

Main build file:
- pom.xml

## 3. Architecture

SDMS follows a JavaFX MVC-style desktop architecture:
- Model: data classes in src/main/java/com/sdms/model
- View: FXML layouts in src/main/resources/fxml
- Controller: JavaFX controllers in src/main/java/com/sdms/controller
- Service: data and export services in src/main/java/com/sdms/service
- Utility: shared helpers in src/main/java/com/sdms/util

Entry points:
- com.sdms.MainApp: JavaFX Application
- com.sdms.AppLauncher: bootstrap main class for packaged EXE launchers

## 4. Functional Modules

### 4.1 Authentication and Session

- Login via username/password
- Password hash verification
- Session state management for current user
- Change password dialog for logged-in user

Default first-run account:
- Username: admin
- Password: admin123
- Role: ADMIN

### 4.2 Dashboard

Dashboard displays quick module counts:
- Athletes
- Officials
- Coaches
- Equipment records

### 4.3 Athlete Management

Capabilities:
- Create, edit, delete athlete records
- Search and school filter
- Record and save child tables:
  - Previous Palaro participation
  - Lower meets
- Photo upload
- QR code generation for athlete identity payload
- Export athlete ID card PDF
- Export athlete full report PDF

### 4.4 Official Management

Capabilities:
- Create, edit, delete official records
- Search and school filter
- Child tables:
  - Educational qualifications
  - Sports training
  - Track record
- Photo upload
- QR generation
- ID card and report export

### 4.5 Coach Management

Capabilities:
- Create, edit, delete coach records
- Search and school filter
- Child tables:
  - Educational qualifications
  - Sports training
  - Track record
- Photo upload
- QR generation
- ID card and report export

### 4.6 Equipment Management

Capabilities:
- Create, edit, delete equipment borrowing records
- Search by borrower/school/event/id
- Add/remove equipment items per record (qty, unit, description, borrowed/returned dates, remarks)
- Export equipment report PDF

### 4.7 Global Search

Cross-module lookup across:
- Athletes
- Officials
- Coaches
- Equipment records

Behavior:
- Triggers on at least 2 characters
- Module-colored rows for quick visual separation

### 4.8 Batch and Roster Exports

Batch ID export:
- Export combined ID cards for selected modules (athletes, officials, coaches)

Delegation roster export:
- Export roster for all schools or selected school
- Includes selected module groups

### 4.9 User Management and Roles

User management supports:
- Create user
- Edit user
- Activate/deactivate user
- Delete user

Roles:
- ADMIN: full access (add/edit/delete/user management)
- ENCODER: add/edit access
- VIEWER: read-only access

Permission model examples:
- Add/edit requires canEdit
- Delete requires canDelete
- User Management button is visible to admins only

## 5. Data Storage and Runtime Paths

SDMS now uses writable per-user application paths through AppPaths.

Windows base folder:
- %LOCALAPPDATA%/SDMS

Important files/folders:
- Database: %LOCALAPPDATA%/SDMS/sdms.db
- Photos: %LOCALAPPDATA%/SDMS/data/photos
- QR codes: %LOCALAPPDATA%/SDMS/data/qrcodes
- PDF exports: %LOCALAPPDATA%/SDMS/data/exports
- Startup logs: %LOCALAPPDATA%/SDMS/logs/startup-error.log

This avoids write failures in installed EXE environments.

## 6. Database Schema Summary

Core tables:
- athletes
- athlete_palaro_previous
- athlete_lower_meets
- officials
- official_education
- official_sports_training
- official_track_record
- coaches
- coach_education
- coach_sports_training
- coach_track_record
- equipment
- equipment_items
- users

ID generation patterns:
- Athlete: ATH-YYYY-####
- Official: OFC-YYYY-####
- Coach: CCH-YYYY-####
- Equipment: EQP-YYYY-####

## 7. Build and Run (Developer)

Prerequisites:
- JDK 17+
- Maven 3.8+

Run in development mode:
- mvn javafx:run

Package shaded JAR:
- mvn -DskipTests package

Main artifact:
- target/sports-delegation-ms-1.0-SNAPSHOT-shaded.jar

## 8. Windows Installer Build

### Recommended one-command script

Use:
- build-installer.ps1

Examples:
- Full rebuild and package:
  - ./build-installer.ps1 -CleanOutput
- Package only (skip Maven build):
  - ./build-installer.ps1 -SkipBuild -CleanOutput

Current script defaults:
- Main class: com.sdms.AppLauncher
- Main JAR: sports-delegation-ms-1.0-SNAPSHOT-shaded.jar
- Icon path: src/main/resources/images/app-logo.ico
- Output directory: dist

Installer output:
- dist/SDMS-1.0.exe

### Manual jpackage command

- jpackage --type exe --input target --dest dist --name SDMS --main-jar sports-delegation-ms-1.0-SNAPSHOT-shaded.jar --main-class com.sdms.AppLauncher --icon src/main/resources/images/app-logo.ico --win-menu --win-shortcut

## 9. Troubleshooting

### App installs but does not open

1. Check startup log:
   - %LOCALAPPDATA%/SDMS/logs/startup-error.log
2. Ensure installer was built with:
   - Main class: com.sdms.AppLauncher
3. Rebuild installer using build-installer.ps1

### PowerShell jpackage multiline command fails

Use single-line command or the script. Caret-based multiline commands can cause parsing issues in PowerShell.

### No output files after export

Check writable export path:
- %LOCALAPPDATA%/SDMS/data/exports

### Login issues

- Verify username/password and account is active
- On first run, default admin account is auto-created

## 10. Security Notes

- Passwords are stored as hashes, not plaintext
- Accounts can be deactivated
- Current user cannot delete own account

## 11. Suggested Operations Guide

### Backup

1. Close SDMS
2. Copy:
   - %LOCALAPPDATA%/SDMS/sdms.db
   - Optional: %LOCALAPPDATA%/SDMS/data

### Restore

1. Close SDMS
2. Replace sdms.db with backup copy
3. Reopen SDMS

## 12. Key Source Locations

- Main entry: src/main/java/com/sdms/MainApp.java
- Packaged launcher: src/main/java/com/sdms/AppLauncher.java
- DB service: src/main/java/com/sdms/service/DatabaseService.java
- PDF service: src/main/java/com/sdms/service/PDFExportService.java
- QR service: src/main/java/com/sdms/service/QRCodeService.java
- App paths: src/main/java/com/sdms/util/AppPaths.java
- Session/auth state: src/main/java/com/sdms/util/SessionManager.java
- Installer script: build-installer.ps1

## 13. Versioning Notes

Current artifact version from pom.xml:
- 1.0-SNAPSHOT

For release builds, consider changing to semantic versions (example: 1.0.0, 1.1.0).
