# SDMS User Manual

This guide is for daily users of the Sports Delegation Management System (SDMS).

## 1. Login

1. Open SDMS.
2. Enter your username and password.
3. Click Login.

If login fails:
- Check username/password.
- Confirm your account is active.
- Contact the admin if your account is locked or inactive.

## 2. Main Navigation

Use the left sidebar to open:
- Dashboard
- Athletes
- Officials
- Coaches
- Equipment

Top actions include:
- Global Search
- Batch Print
- Delegation Roster
- Change Password
- About
- Logout

## 3. Dashboard

Dashboard shows summary counts for:
- Athletes
- Officials
- Coaches
- Equipment records

## 4. Athlete Module

### Add Athlete
1. Open Athletes.
2. Click Add.
3. Fill required details.
4. Add optional child records (Previous Palaro, Lower Meets).
5. Click Save.

### Edit Athlete
1. Select a row.
2. Click Edit.
3. Update details.
4. Click Save.

### Delete Athlete
1. Select a row.
2. Click Delete.
3. Confirm.

### Export
- Export ID Card: creates a card PDF and QR if needed.
- Export Report: creates full athlete report PDF.

## 5. Official Module

Same workflow as Athletes:
- Add / Edit / Delete
- Search and school filtering
- Export ID card and report
- Manage child records:
  - Educational Qualifications
  - Sports Training
  - Track Record

## 6. Coach Module

Same workflow as Officials:
- Add / Edit / Delete
- Search and school filtering
- Export ID card and report
- Manage child records

## 7. Equipment Module

### Add Equipment Record
1. Open Equipment.
2. Click Add.
3. Fill borrower details.
4. Add equipment items using + Add Item.
5. Click Save Record.

### Edit / Delete
- Select a record and use Edit or Delete.

### Export
- Export Report creates equipment PDF report.

## 8. Global Search

1. Open Global Search.
2. Enter at least 2 characters.
3. Review results across all modules.

## 9. Batch ID Export

1. Open Batch Print.
2. Select modules to include.
3. Click Export.
4. Generated PDF opens automatically.

## 10. Delegation Roster Export

1. Open Roster.
2. Select All Schools or one school.
3. Select modules.
4. Click Export.

## 11. Change Password

1. Open Change Password.
2. Enter current password.
3. Enter and confirm new password.
4. Save.

## 12. User Roles

- ADMIN: full access
- ENCODER: add/edit access
- VIEWER: view-only access

Note: only ADMIN can access user management and delete records.

## 13. Data and Exports Location (Windows)

SDMS stores files in:
- %LOCALAPPDATA%/SDMS

Important subfolders:
- Database: %LOCALAPPDATA%/SDMS/sdms.db
- Photos: %LOCALAPPDATA%/SDMS/data/photos
- QR: %LOCALAPPDATA%/SDMS/data/qrcodes
- Exports: %LOCALAPPDATA%/SDMS/data/exports

## 14. Common Issues

### App opens but no records
- Confirm you are using the correct installed environment.
- Check if database file exists at %LOCALAPPDATA%/SDMS/sdms.db.

### Export not found
- Check %LOCALAPPDATA%/SDMS/data/exports.

### Invalid login
- Ask admin to reset your account password or reactivate account.
