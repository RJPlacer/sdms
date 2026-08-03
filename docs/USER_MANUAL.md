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
- Multi-PC Sharing
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
3. Fill the personal details, including Grade Level.
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
- Export SDO PDF: creates the official one-page Athlete Record with split name
  fields, Grade Level, fixed meet rows, the athlete signature line, and the SDO
  City of Baliwag header and footer.

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
- Export SDO PDF creates the official Memorandum Receipt for Equipment,
  Semi-Expendable and Non-Expendable Property with the current SDO City of
  Baliwag letterhead, equipment table, acknowledgement, and signature areas.

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

## 13. Multi-PC Sharing

### Start the main computer

1. Sign in as an ADMIN on the primary computer.
2. Open **Multi-PC Sharing** from the left sidebar.
3. Enter a name for the computer and click **Start Sharing**.
4. Allow access on private networks if Windows asks.
5. Give other users the displayed computer address and six-digit connection code.

### Connect an encoder or viewer computer

1. Open Athlete Track and click **Connect to Main Computer**.
2. Click **Find Computers** and select the main computer.
3. Enter the six-digit connection code and click **Connect**.
4. Sign in with the account supplied by the administrator.

The main computer must stay powered on and connected to the same Wi-Fi. If it is
unavailable, Athlete Track offers **Try Again** or **Use This Computer Only**.
Using the local database does not delete shared records.

### Back up or replace the main computer

- On the main computer, open Multi-PC Sharing and click **Back Up**.
- Athlete Track also creates one automatic backup per day and keeps the latest 14.
- Store the `.atbackup` file in a protected external drive.
- On a replacement computer, open Multi-PC Sharing, click **Restore**, and select
  the backup. The replacement becomes the new main computer.

## 14. Data and Exports Location (Windows)

SDMS stores files in:
- `%LOCALAPPDATA%/Athlete Track`

Important subfolders:
- Local database: `%LOCALAPPDATA%/Athlete Track/sdms.db`
- Shared database: `%LOCALAPPDATA%/Athlete Track/shared-database`
- Shared images: `%LOCALAPPDATA%/Athlete Track/shared-assets`
- Exports: `%LOCALAPPDATA%/Athlete Track/data/exports`

## 15. Common Issues

### App opens but no records
- Confirm you are using the correct installed environment.
- Check the database mode shown on the login screen.
- For shared mode, confirm that the main computer is on and connected to the same Wi-Fi.

### Export not found
- Check `%LOCALAPPDATA%/Athlete Track/data/exports`.

### Invalid login
- Ask admin to reset your account password or reactivate account.
