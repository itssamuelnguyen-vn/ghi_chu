# Ghi Chú

Personal notes app for Android (sideloaded APK, never on the Play Store). Offline first: everything lives in a
local database; Google Drive sync is optional and keeps several phones/tablets in step.

- Text notes in Markdown (headings, bold/italic/strike, lists, tasks, quotes, code, links), styled while typing,
  with a format bar, automatic list continuation, a reading mode and undo/redo.
- Checklists: tick, drag to reorder, done items fold away at the bottom, convert text ↔ checklist.
- Colours, pin, labels, archive, trash (emptied after 30 days), swipe to archive/delete, multi-select actions.
- Search without accents ("ghi chu" finds "ghi chú") with highlights; filters for checklists, photos, recordings,
  reminders, locked notes, labels and colours. Grid or list; sort by edit date, creation date, title or by hand.
- Reminders at an exact time, repeating daily / weekdays / weekly / monthly / yearly, with "Done" and snooze.
- Photos (camera or gallery), voice recordings, speech-to-text dictation.
- Share into the app from any app; share notes out; home-screen widgets (one note with tickable items, and a quick
  capture bar); Quick Settings tile; launcher shortcuts.
- App lock and per-note lock (fingerprint / face / screen-lock PIN); version history per note.
- Backup / restore (`.zip`), export everything to Markdown, import from Google Keep (Takeout).
- Note templates with {{date}} / {{time}} / {{weekday}}; `[[links]]` between notes with suggestions while typing
  and a "linked from" list; drawing / handwriting (pen, highlighter, eraser).
- Share a note as a picture; export one note or a selection to PDF (print screen → "Save as PDF").
- Optional end-to-end encryption of everything on Drive with a passphrase (Settings → Sync).
- Today's note (one per day, from a template), reminder calendar, read a note aloud, see what changed between
  versions, Markdown tables, any file attached (PDF, Word…), scan documents and copy text out of photos.
- Note-list widget (pinned notes, a label, or the latest); weekly automatic backup into a folder you choose.
- Light / dark / system theme, four text sizes, Vietnamese or English.

## Build

In VS Code press **Ctrl+Shift+B**. It runs `tool/build_apk.ps1` → `build\apk\ghichu-<version>-<yyyyMMdd-HHmm>.apk`.
Copy that APK to the phone and open it to install. Android Studio and the emulator are never used.

## Google Drive sync (one-time setup, about 10 minutes, free)

Google only lets an app use Drive if it is registered. The app needs no password or key inside it: Google
recognises it by its package name and the fingerprint of the key that signs the APK.

1. Open <https://console.cloud.google.com/>, sign in with the Google account you will sync with, and create a
   project (any name, e.g. "Ghi Chu").
2. **APIs & Services → Library**: search **Google Drive API** → **Enable**.
3. **APIs & Services → OAuth consent screen** (Google Auth Platform): User type **External**, app name "Ghi Chú",
   your email as support and developer contact. Save.
   - **Data access / Scopes**: add `.../auth/drive.appdata` (Drive: "See, create and delete its own configuration
     data in your Google Drive"). It is a non-sensitive scope, so no review by Google is needed.
   - **Audience**: press **Publish app** (status "In production"). In "Testing" Google signs you out every 7 days.
4. **APIs & Services → Credentials → Create credentials → OAuth client ID** → type **Android**:
   - Package name: `com.samuelcruz.ghichu`
   - SHA-1: `3A:F0:43:EA:FD:32:A7:DF:68:9E:9D:15:FB:46:33:7B:22:87:E3:6D` (the debug key in
     `C:\Users\Sammy\.android\debug.keystore` that signs the release APK; print it again with
     `D:\dev\android-sdk\jbr\bin\keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -storepass android`)
5. On the phone: Ghi Chú → Settings → **Connect Google account**, pick the account, allow.

"Error 10" when connecting = step 4 is missing or the package name / SHA-1 differ. "403" = the Drive API is not
enabled (step 2). Notes are stored in the hidden *app data* folder of that Drive (not visible in drive.google.com;
Drive → Settings → Manage apps shows its size and can delete it).

See `CLAUDE.md` for the code layout.
