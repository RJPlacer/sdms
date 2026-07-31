# SDMS Documentation Pack

This file is a printable consolidated pack for turnover and deployment.

## Included Documents

- System Documentation: docs/SYSTEM_DOCUMENTATION.md
- User Manual: docs/USER_MANUAL.md
- Deployment Checklist: docs/DEPLOYMENT_CHECKLIST.md

## Quick Index

### A. System Summary
SDMS is a JavaFX + SQLite desktop system for athlete delegation management.

Core modules:
- Athletes
- Officials
- Coaches
- Equipment
- Global Search
- Batch ID Export
- Delegation Roster
- User Management

### B. Runtime Data Paths (Windows)
- %LOCALAPPDATA%/SDMS/sdms.db
- %LOCALAPPDATA%/SDMS/data/photos
- %LOCALAPPDATA%/SDMS/data/qrcodes
- %LOCALAPPDATA%/SDMS/data/exports
- %LOCALAPPDATA%/SDMS/logs/startup-error.log

### C. Build and Packaging
Recommended installer build command:
- ./build-installer.ps1 -CleanOutput

Installer output:
- dist/SDMS-1.0.exe

### D. First-Time Operations
1. Install SDMS installer.
2. Login using default admin account.
3. Change admin password.
4. Create role-based users.
5. Verify exports and backups.

### E. Troubleshooting Snapshot
If app does not open:
1. Check %LOCALAPPDATA%/SDMS/logs/startup-error.log
2. Rebuild installer from latest source
3. Reinstall and retest

## Printable PDF (No Extra Tools Needed)

Option 1: VS Code print to PDF
1. Open this file in VS Code preview.
2. Use Print command.
3. Select Microsoft Print to PDF.

Option 2: Browser print to PDF
1. Open markdown preview in browser.
2. Press Ctrl+P.
3. Select Microsoft Print to PDF.

## Notes

For complete technical details, always refer to docs/SYSTEM_DOCUMENTATION.md.
