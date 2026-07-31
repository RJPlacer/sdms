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

On a new database, sign in with `admin` / `admin123`. The application requires
the temporary password to be changed before the main window opens.

### Build shaded JAR

```bash
mvn -DskipTests package
```

Output:

- target/sports-delegation-ms-1.0-SNAPSHOT-shaded.jar

Run the packaged JAR with JDK 17+:

```bash
java -jar target/sports-delegation-ms-1.0-SNAPSHOT-shaded.jar
```

## Build Windows Installer

Use the one-command script:

```powershell
./build-installer.ps1 -CleanOutput
```

Or package only:

```powershell
./build-installer.ps1 -SkipBuild -CleanOutput
```

Installer output:

- dist/SDMS-1.0.exe

## Current Runtime Data Paths (Windows)

- Database: %LOCALAPPDATA%/SDMS/sdms.db
- Photos: %LOCALAPPDATA%/SDMS/data/photos
- QR codes: %LOCALAPPDATA%/SDMS/data/qrcodes
- Exports: %LOCALAPPDATA%/SDMS/data/exports
- Startup logs: %LOCALAPPDATA%/SDMS/logs/startup-error.log
