# Ghi Chú

Android notes app for the owner only (sideloaded APK, never on the Play Store). UI is Vietnamese (default) or
English, chosen in Settings; code, names, comments and docs are English. The owner is a Rails developer new to
Android: keep code simple, explain Android concepts by comparison with Rails (Room ~ ActiveRecord, ViewModel ~
controller state, NavHost routes ~ routes.rb, WorkManager ~ cron/ActiveJob). Build rules (no Android Studio, no
emulator) are in `~/.claude/CLAUDE.md`.

## Build, test, install

```powershell
$env:JAVA_HOME='D:\dev\android-sdk\jbr'
.\gradlew.bat testDebugUnitTest                                   # JVM tests: text, Markdown, checklist, repeat, sync plan, Keep import
powershell -ExecutionPolicy Bypass -File tool\build_apk.ps1        # = Ctrl+Shift+B → build\apk\ghichu-<ver>-<stamp>.apk
```

From WSL redirect Gradle's output to a file and feed stdin from /dev/null, otherwise the call can hang after the
build (the daemon keeps the pipe):
`powershell.exe -NoProfile -Command "cd D:\dev\customized_apps\ghi_chu; .\gradlew.bat assembleRelease *> build\gradle.log" < /dev/null`.

Release is R8-shrunk and signed with the local debug key (SHA-1 `3A:F0:43:…:E3:6D`, registered for Google sign-in,
see README). Bump `versionCode` / `versionName` in `app/build.gradle.kts` for each APK given to the phone.

Stack (same versions as `chi_tieu`, see `gradle/libs.versions.toml`): Kotlin, Compose + Material 3 (+ icons
extended, shrunk by R8), Navigation Compose (string routes) with a shared-element card → editor transition, Room
(KSP, schemas in `app/schemas`), WorkManager, kotlinx.serialization, Glance widgets, Play services auth
(`AuthorizationClient`, Drive scope only). Drive REST is plain `HttpURLConnection` (`sync/Drive.kt`), no Google
client library. compile/target SDK 37, minSdk 29. No DI: `GhiChuApp` holds `db`, `prefs`, `repo`, `sync`.

## Layout

```
GhiChuApp     wiring: repo hooks (reminders, widgets, sync), lifecycle (sync on start, flush on stop), shortcuts
MainActivity  intents → ui/Launch (open note, new note/list/voice/photo, share-in), app lock
Reminders     AlarmManager + notification (Done / snooze), ReminderReceiver, BootReceiver
core/   I18n (object S, vi/en side by side), Text (fold = accent-free, same length → highlight ranges), Markdown
        (parser) + MdEdit (toolbar / list continuation), Checklist (body ↔ items), Repeat, AppLock (+ Prefs),
        Links ([[title]] links), Templates ({{date}} placeholders, + Daily = today's note title), Html (notes → HTML
        for PDF), Diff (line diff for history)
data/   Entities (Note, Label, Attachment, Revision), AppDatabase (+ DAOs), Repo (every change: stamps updatedAt,
        dirty=true, calls onChange), Files (attachment files, image shrink + cache), Export (Markdown), Backup,
        AutoBackup (+ worker: weekly zip into a SAF folder), KeepImport (Takeout zip)
sync/   Drive (REST), GoogleAuth, SyncPlan (pure decision + DTOs), SyncEngine (one pass), SyncManager (+ SyncWorker),
        Crypto (end-to-end encryption: PBKDF2 key, AES-GCM)
ui/     Theme (NoteColors, BrandBrush), MarkdownStyle (editor VisualTransformation + reading view), Components
        (NoteCard, shared bounds), AppNav (routes, snackbar, LockScreen), NotesScreen/ViewModel (drawer, grid,
        selection, swipe, FAB), EditorScreen/ViewModel, Dialogs (labels, reminder), Audio, SearchScreen,
        MoreScreens (labels, history, reorder), SettingsScreen, TemplatesScreen (+ TemplatePicker), DrawScreen,
        ShareImageScreen, Pdf (print screen → "Save as PDF"), CalendarScreen (reminders by month), Tools (ML Kit
        document Scanner + Ocr, AttachedFiles open/share, Speaker = text-to-speech)
widget/ NoteWidget (+ ToggleItemAction, config activity), NoteListWidget (ListWidget.kt: pinned / label / recent,
        + config activity), QuickWidget, QuickTile
```

## Data rules

- Ids are UUID strings. Labels on a note: `note.labels` = ",id1,id2,". Checklist items live in `note.body` as
  Markdown task lines (`- [ ] x` / `- [x] x`); order of lines = user order; "checked last" is display only.
- Nothing is hard-deleted once it may have reached Drive: "delete forever" turns the row into a tombstone
  (`deleted = true`, content emptied) so other devices delete it too. Only a never-uploaded empty note is removed.
- Trash = `trashedAt` set; emptied after 30 days (`Repo.purgeOldTrash` on start).
- Reminders: `reminderAt` is the first time; `Repeat.next` counts from it (no drift), so devices agree. A fired
  one-time reminder stays (shown struck through) until "Done" or removed.
- Locked notes: body excluded from `searchText`, hidden in cards/widgets/notifications; one unlock lasts until the
  app has been in the background 30 s (`GhiChuApp.notesUnlockedAt`).
- Revisions are local only: the text at open is saved when the editor closes after a change, and before a sync
  download overwrites a different body. At most 50 per note.
- Templates: `note.template = true` (DB v2). Hidden from every list, search, widget, reminder and export
  (`observeAll` filters them); shown only in the templates screen. A note made from one copies it with
  `Templates.fill` ({{date}}, {{time}}, {{weekday}}). Synced like notes (`NoteDto.template`).
- `[[Title]]` links point by title (`Links.key`: accent/case/space-insensitive), not by id, so they survive sync.
  Tapping one opens the newest matching note, or creates it. "Linked from" = notes whose body links to this title.
- Drawings are PNG attachments (`AttachmentKind.DRAWING`); editing one replaces it with a new attachment
  (attachments never change, so each is uploaded once). `AttachmentKind.isPicture` = photo or drawing.
- Files (`AttachmentKind.FILE`, DB v3 `attachment.name`): any file up to 20 MB, stored as `<id>.<ext>` with the
  original name in `name` (synced in `AttachmentDto.name`); opened/shared through the FileProvider.
- Today's note: found by title (`Daily.title`: the daily template's title if it has {{date}}, else the date), so
  phones agree after sync; template chosen in Settings (`prefs.dailyTemplate`). Drawer item + launcher shortcut.
- Markdown tables (GitHub style) are parsed by `Markdown.table` and drawn in reading mode, PDF and plain previews.
- Schema changes: bump the `@Database` version and add a migration (AutoMigration when possible). Never
  destructive migrations: these are the owner's real notes.

## Sync rules (Drive `appDataFolder`)

- Files: `n_<id>.json` (NoteDto), `l_<id>.json` (LabelDto), `a_<id>.<ext>` (attachment, immutable, uploaded once).
- Each row keeps `remoteId` + `remoteVersion` (Drive's per-file `version`) + `dirty`. `SyncPlan.decide`: same
  version → upload if dirty; newer on Drive → download, or conflict if also dirty here.
- Conflict on a note: same content → just mark clean; deleted here later than Drive's edit → deletion wins; else
  this device's version becomes a new note "(bản trên máy này)" and the original takes Drive's. Labels: last
  writer wins.
- A note open in an editor is never overwritten (`SyncManager.noteOpened`/`noteClosed` count editors, since following a
  link stacks them); it syncs after closing.
- Order of a pass: labels → upload attachments → notes → download attachments → delete Drive attachments no note
  uses (older than a day, only if no note was deferred).
- Disconnect clears remote ids (`forgetRemote`); reconnecting matches files by name and merges.
- End-to-end encryption (optional): Drive holds `crypto.json` (salt + check, no key). When it exists every upload
  is encrypted (`GCE1` header) and tagged `appProperties.enc = 1`; files whose tag disagrees are re-uploaded, so
  switching on/off converts Drive on the next passes. The key lives only in prefs (`e2e_key`); a phone without
  the matching key gets `NeedsPassphrase` and syncs nothing until the passphrase is entered in Settings. A
  forgotten passphrase cannot be recovered (local notes stay).

## UI notes

- All UI text lives in `core/I18n.kt` (`S.xxx`, both languages on one line). Language change: save, `I18n.set`,
  `activity.recreate()`. ViewModels must not cache translated text.
- Widgets (Glance): read data **inside** `provideContent` (Room Flow + `collectAsState`) and keep each widget's
  choice in its Glance state (`currentState(key)`, written with `updateAppWidgetState` by the config activity).
  Glance re-runs only the content on `update()`, so values read before `provideContent` stay stale.
- Theme: `GhiChuTheme(themeMode, fontSize)`; use `LocalIsDark`, `LocalNoteScale`, `NoteColors.background(i)`.
- Each screen gets its ViewModel via `appViewModel { app -> … }` (scoped to its navigation entry).
- The editor saves 500 ms after the last change and on close (`EditorViewModel.close` in `onCleared`).

## Roadmap

Done 0.1.0 (2026-11-08): everything in the approved P1 + P2 list (see README).
Done 0.2.0 (2026-11-14): P3: drawing/handwriting, note templates, `[[links]]` between notes (+ "linked from"),
share note as image, PDF export (one note or a selection), end-to-end encryption of Drive files.
Done 0.3.0 (2026-11-14): today's note, note-list widget, read aloud, history diff, Markdown tables, file
attachments, document scan + text from images (ML Kit via Play services), reminder calendar, weekly auto backup.
Not done (ask first): two-pane tablet layout, location reminders, link graph.
