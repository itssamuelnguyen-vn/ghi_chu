package com.samuelcruz.ghichu.core

import java.time.LocalDate

/**
 * The UI language: Vietnamese (default) or English, chosen in Settings. Changing it saves the choice and
 * recreates the activity, so every screen is drawn again in the new language.
 */
object I18n {
    var lang: String = "vi"
        private set

    fun set(code: String) {
        lang = if (code == "en") "en" else "vi"
    }
}

/**
 * Every piece of UI text, Vietnamese and English side by side, so a missing translation cannot happen. Texts
 * with numbers are functions.
 */
object S {
    private val en get() = I18n.lang == "en"
    private fun t(vi: String, en: String) = if (this.en) en else vi

    // Common
    val appName get() = t("Ghi Chú", "Ghi Chú")
    val save get() = t("Lưu", "Save")
    val cancel get() = t("Hủy", "Cancel")
    val ok get() = t("OK", "OK")
    val done get() = t("Xong", "Done")
    val delete get() = t("Xóa", "Delete")
    val close get() = t("Đóng", "Close")
    val back get() = t("Quay lại", "Back")
    val add get() = t("Thêm", "Add")
    val edit get() = t("Sửa", "Edit")
    val more get() = t("Thêm tùy chọn", "More")
    val menu get() = t("Menu", "Menu")
    val clear get() = t("Xóa", "Clear")
    val rename get() = t("Đổi tên", "Rename")
    val undo get() = t("Hoàn tác", "Undo")
    val redo get() = t("Làm lại", "Redo")
    val share get() = t("Chia sẻ", "Share")
    val restore get() = t("Khôi phục", "Restore")
    val duplicate get() = t("Tạo bản sao", "Make a copy")
    val selectAll get() = t("Chọn tất cả", "Select all")
    val untitled get() = t("Không có tiêu đề", "Untitled")
    val working get() = t("Đang xử lý…", "Working…")
    fun failed(msg: String?) = t("Không thành công", "Failed") + (msg?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")

    // Dates
    val today get() = t("Hôm nay", "Today")
    val tomorrow get() = t("Mai", "Tomorrow")
    val yesterday get() = t("Hôm qua", "Yesterday")
    fun shortDate(d: LocalDate, withYear: Boolean): String {
        val wd = if (en) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[d.dayOfWeek.value - 1]
        else listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")[d.dayOfWeek.value - 1]
        val base = if (en) "$wd ${d.dayOfMonth} ${listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[d.monthValue - 1]}"
        else "$wd %02d/%02d".format(d.dayOfMonth, d.monthValue)
        return if (!withYear) base else if (en) "$base ${d.year}" else "$base/${d.year}"
    }
    fun editedAt(whenText: String) = t("Đã sửa $whenText", "Edited $whenText")
    fun createdEdited(created: String, edited: String) = t("Tạo $created · Sửa $edited", "Created $created · Edited $edited")
    fun words(n: Int) = t("$n từ", if (n == 1) "1 word" else "$n words")

    // Main screen
    val notes get() = t("Ghi chú", "Notes")
    val reminders get() = t("Nhắc nhở", "Reminders")
    val archive get() = t("Lưu trữ", "Archive")
    val trash get() = t("Thùng rác", "Trash")
    val labels get() = t("Nhãn", "Labels")
    val settings get() = t("Cài đặt", "Settings")
    val pinned get() = t("Đã ghim", "Pinned")
    val others get() = t("Khác", "Others")
    val searchHint get() = t("Tìm ghi chú", "Search notes")
    val listView get() = t("Xem dạng danh sách", "List view")
    val gridView get() = t("Xem dạng lưới", "Grid view")
    val sync get() = t("Đồng bộ", "Sync")
    val pin get() = t("Ghim", "Pin")
    val unpin get() = t("Bỏ ghim", "Unpin")
    val color get() = t("Màu nền", "Colour")
    val trashHint get() = t("Ghi chú trong thùng rác sẽ bị xóa sau 30 ngày.", "Notes in the trash are deleted after 30 days.")
    val emptyTrash get() = t("Dọn sạch", "Empty")
    val emptyTrashTitle get() = t("Dọn sạch thùng rác?", "Empty the trash?")
    val emptyTrashText get() = t("Mọi ghi chú trong thùng rác sẽ bị xóa vĩnh viễn, cả trên các máy khác.", "All notes in the trash will be deleted for good, also on your other devices.")
    val deleteForever get() = t("Xóa vĩnh viễn", "Delete forever")
    fun deleteForeverTitle(n: Int) = t("Xóa vĩnh viễn $n ghi chú?", "Delete $n note(s) forever?")
    val deleteForeverText get() = t("Không thể hoàn tác.", "This cannot be undone.")
    fun archived(n: Int) = t(if (n == 1) "Đã lưu trữ ghi chú" else "Đã lưu trữ $n ghi chú", if (n == 1) "Note archived" else "$n notes archived")
    fun unarchived(n: Int) = t(if (n == 1) "Đã bỏ lưu trữ" else "Đã bỏ lưu trữ $n ghi chú", if (n == 1) "Note unarchived" else "$n notes unarchived")
    fun trashed(n: Int) = t(if (n == 1) "Đã chuyển vào thùng rác" else "Đã chuyển $n ghi chú vào thùng rác", if (n == 1) "Note moved to trash" else "$n notes moved to trash")
    fun restored(n: Int) = t("Đã khôi phục $n ghi chú", "$n note(s) restored")
    val emptyDiscarded get() = t("Đã bỏ ghi chú trống", "Empty note discarded")
    val sortBy get() = t("Sắp xếp", "Sort by")
    val sortEdited get() = t("Ngày sửa gần nhất", "Last edited")
    val sortCreated get() = t("Ngày tạo", "Date created")
    val sortTitle get() = t("Tên (A → Z)", "Title (A → Z)")
    val sortManual get() = t("Thủ công (tự kéo thả)", "Manual (drag to order)")
    val reorderNotes get() = t("Sắp xếp thủ công", "Arrange notes")
    val reorderHint get() = t("Nhấn giữ một ghi chú rồi kéo lên/xuống. Ghi chú đã ghim luôn ở trên.", "Long-press a note and drag it up or down. Pinned notes stay on top.")
    val dragToMove get() = t("Kéo để di chuyển", "Drag to move")

    val emptyNotesTitle get() = t("Chưa có ghi chú nào", "No notes yet")
    val emptyNotesText get() = t("Bấm + để ghi lại ý tưởng, việc cần làm, ảnh hay giọng nói.", "Tap + to capture an idea, a to-do list, a photo or your voice.")
    val emptyRemindersTitle get() = t("Không có nhắc nhở", "No reminders")
    val emptyRemindersText get() = t("Ghi chú có nhắc nhở sẽ hiện ở đây.", "Notes with a reminder show up here.")
    val emptyArchiveTitle get() = t("Lưu trữ trống", "Archive is empty")
    val emptyArchiveText get() = t("Vuốt một ghi chú sang phải để cất vào đây.", "Swipe a note to the right to archive it.")
    val emptyTrashTitle2 get() = t("Thùng rác trống", "Trash is empty")
    val emptyTrashText2 get() = t("Ghi chú đã xóa nằm ở đây 30 ngày.", "Deleted notes stay here for 30 days.")
    val emptyLabelTitle get() = t("Chưa có ghi chú với nhãn này", "No notes with this label")
    val emptyLabelText get() = t("Bấm + để tạo ghi chú có sẵn nhãn này.", "Tap + to create a note with this label.")

    // New note
    val newNote get() = t("Ghi chú mới", "New note")
    val newChecklist get() = t("Danh sách", "Checklist")
    val newPhoto get() = t("Ảnh", "Photo")
    val newRecording get() = t("Ghi âm", "Recording")
    val newVoice get() = t("Nói để ghi", "Dictate")

    // Labels
    val createLabel get() = t("Tạo nhãn mới", "Create label")
    fun createLabelNamed(name: String) = t("Tạo nhãn \"$name\"", "Create label \"$name\"")
    val editLabels get() = t("Chỉnh sửa nhãn", "Edit labels")
    val labelSearchHint get() = t("Nhập tên nhãn", "Enter label name")
    val noLabels get() = t("Chưa có nhãn", "No labels")
    val noLabelsText get() = t("Nhãn giúp gom các ghi chú cùng chủ đề, ví dụ \"Công việc\", \"Nhà\".", "Labels group notes by topic, like \"Work\" or \"Home\".")
    fun deleteLabelTitle(name: String) = t("Xóa nhãn \"$name\"?", "Delete label \"$name\"?")
    val deleteLabelText get() = t("Nhãn sẽ được gỡ khỏi các ghi chú. Ghi chú không bị xóa.", "The label is removed from its notes. The notes stay.")

    // Editor
    val titleHint get() = t("Tiêu đề", "Title")
    val bodyHint get() = t("Ghi chú", "Note")
    val addItem get() = t("Mục mới", "List item")
    fun doneItems(n: Int) = t("$n mục đã xong", if (n == 1) "1 checked item" else "$n checked items")
    val readMode get() = t("Chế độ đọc", "Reading mode")
    val editMode get() = t("Chỉnh sửa", "Edit")
    val reminder get() = t("Nhắc nhở", "Reminder")
    val removeReminder get() = t("Xóa", "Remove")
    val laterToday get() = t("Chiều nay 18:00", "This evening 18:00")
    val tonight get() = t("Tối nay 20:00", "Tonight 20:00")
    val tomorrowMorning get() = t("Sáng mai 8:00", "Tomorrow 8:00")
    val nextWeek get() = t("Thứ Hai tới 8:00", "Next Monday 8:00")
    fun repeatName(r: Int) = when (r) {
        Repeat.DAILY -> t("Hằng ngày", "Daily")
        Repeat.WEEKDAYS -> t("Thứ Hai – Thứ Sáu", "Weekdays")
        Repeat.WEEKLY -> t("Hằng tuần", "Weekly")
        Repeat.MONTHLY -> t("Hằng tháng", "Monthly")
        Repeat.YEARLY -> t("Hằng năm", "Yearly")
        else -> t("Không lặp lại", "Does not repeat")
    }
    val saveMarkdown get() = t("Lưu file Markdown", "Save as Markdown")
    val noteFileName get() = t("ghi-chu", "note")
    val exported get() = t("Đã lưu file", "File saved")
    val toChecklist get() = t("Chuyển thành danh sách", "Turn into checklist")
    val toText get() = t("Chuyển thành văn bản", "Turn into text")
    val uncheckAll get() = t("Bỏ chọn tất cả", "Uncheck all")
    val deleteChecked get() = t("Xóa các mục đã xong", "Delete checked items")
    val lockNote get() = t("Khóa ghi chú", "Lock note")
    val unlockNote get() = t("Mở khóa ghi chú", "Unlock note")
    val noteLockedInfo get() = t("Đã khóa: cần vân tay để mở, nội dung bị ẩn ngoài danh sách", "Locked: needs your fingerprint, hidden in lists")
    val lockedNote get() = t("Ghi chú đã khóa", "Locked note")
    val lockedNotes get() = t("Đã khóa", "Locked")
    val unlock get() = t("Mở khóa", "Unlock")
    val history get() = t("Lịch sử phiên bản", "Version history")
    val noHistory get() = t("Chưa có phiên bản cũ", "No earlier versions")
    val noHistoryText get() = t("Mỗi lần sửa xong ghi chú, bản trước đó được giữ lại ở đây (tối đa 50 bản).", "Each time you finish editing, the previous version is kept here (up to 50).")
    val restoreVersion get() = t("Khôi phục bản này", "Restore this version")
    val versionRestored get() = t("Đã khôi phục phiên bản", "Version restored")
    val takePhoto get() = t("Chụp ảnh", "Take photo")
    val choosePhotos get() = t("Chọn ảnh", "Choose photos")
    val recordAudio get() = t("Ghi âm", "Record audio")
    val dictate get() = t("Nói để ghi (chuyển giọng nói thành chữ)", "Dictate (speech to text)")
    val speakNow get() = t("Nói nội dung ghi chú…", "Speak your note…")
    val noSpeech get() = t("Máy chưa có dịch vụ nhận giọng nói (cài app Google)", "No speech recognition on this phone (install the Google app)")
    val noCamera get() = t("Không mở được máy ảnh", "Could not open the camera")
    val photoFailed get() = t("Không đọc được ảnh", "Could not read the picture")
    val attachmentRemoved get() = t("Đã xóa tệp đính kèm", "Attachment removed")
    val notifDenied get() = t("Chưa cho phép thông báo: nhắc nhở sẽ không hiện", "Notifications are off: reminders will not show")
    val recording get() = t("Ghi âm", "Recording")
    val stopAndSave get() = t("Dừng & lưu", "Stop & save")
    val micDenied get() = t("Cần quyền micro để ghi âm", "Microphone permission is needed")
    val recordFailed get() = t("Không ghi âm được", "Could not record")
    val waitingDownload get() = t("Đang chờ tải", "Not downloaded")
    val fmtHeading get() = t("Tiêu đề", "Heading")
    val fmtBold get() = t("Đậm", "Bold")
    val fmtItalic get() = t("Nghiêng", "Italic")
    val fmtStrike get() = t("Gạch ngang", "Strikethrough")
    val fmtBullets get() = t("Danh sách", "Bullets")
    val fmtNumbers get() = t("Đánh số", "Numbers")
    val fmtTasks get() = t("Ô đánh dấu", "Checkboxes")
    val fmtQuote get() = t("Trích dẫn", "Quote")
    val fmtCode get() = t("Mã", "Code")
    val fmtLink get() = t("Liên kết", "Link")

    // Colours (index = NoteColors index)
    val colorNames get() = if (en) listOf("Default", "Coral", "Peach", "Sand", "Mint", "Sage", "Fog", "Storm", "Dusk", "Blossom", "Clay", "Chalk")
    else listOf("Mặc định", "San hô", "Đào", "Cát", "Bạc hà", "Ngọc", "Sương", "Biển", "Hoàng hôn", "Hoa", "Đất", "Phấn")
    val colors get() = t("Màu", "Colours")

    // Search
    val search get() = t("Tìm kiếm", "Search")
    val checklists get() = t("Danh sách", "Checklists")
    val photos get() = t("Ảnh", "Photos")
    val recordings get() = t("Ghi âm", "Recordings")
    val noResults get() = t("Không tìm thấy", "Nothing found")
    val noResultsText get() = t("Thử từ khác, hoặc bỏ bớt bộ lọc.", "Try other words or fewer filters.")
    fun resultCount(n: Int) = t("$n kết quả", if (n == 1) "1 result" else "$n results")

    // Reminders
    val reminderChannel get() = t("Nhắc nhở", "Reminders")
    val reminderTitle get() = t("Nhắc nhở", "Reminder")
    val snooze10 get() = t("Báo lại 10 phút", "Snooze 10 min")
    val snooze60 get() = t("Báo lại 1 giờ", "Snooze 1 h")

    // Sync
    val googleSync get() = t("Đồng bộ Google Drive", "Google Drive sync")
    val syncPitch get() = t("Sao lưu tự động và dùng trên nhiều máy. Ghi chú nằm trong thư mục ẩn của app.", "Automatic backup and all your devices in step. Notes live in the app's hidden Drive folder.")
    val connectGoogle get() = t("Kết nối tài khoản Google", "Connect Google account")
    val connecting get() = t("Đang kết nối…", "Connecting…")
    val connected get() = t("Đã kết nối", "Connected")
    val syncConnected get() = t("Đã kết nối Google Drive", "Google Drive connected")
    val syncing get() = t("Đang đồng bộ…", "Syncing…")
    val syncNow get() = t("Đồng bộ ngay", "Sync now")
    fun lastSynced(w: String) = t("Đồng bộ lần cuối: $w", "Last synced: $w")
    val notSyncedYet get() = t("Chưa đồng bộ lần nào", "Not synced yet")
    val disconnect get() = t("Ngắt kết nối", "Disconnect")
    val disconnectTitle get() = t("Ngắt kết nối Google Drive?", "Disconnect Google Drive?")
    val disconnectText get() = t("Ghi chú vẫn còn trên máy này và trên Drive. Kết nối lại sau sẽ gộp cả hai.", "Your notes stay on this phone and in Drive. Connecting again later merges both.")
    val signInAgain get() = t("Đăng nhập lại", "Sign in again")
    val syncOffline get() = t("Không có mạng: sẽ đồng bộ khi có mạng", "Offline: will sync when back online")
    val syncNeedsSignIn get() = t("Cần đăng nhập Google lại", "Google sign-in needed again")
    val syncForbidden get() = t("Google Drive từ chối (403): kiểm tra Drive API đã bật chưa", "Google Drive refused (403): check that the Drive API is enabled")
    fun syncError(msg: String) = t("Lỗi đồng bộ: $msg", "Sync error: $msg")
    fun syncSetupError(code: Int) = if (code == 10) t(
        "Chưa cấu hình Google Cloud cho app này (lỗi 10). Xem README, mục \"Đồng bộ Google Drive\".",
        "Google Cloud is not set up for this app (error 10). See README, \"Google Drive sync\".",
    ) else t("Lỗi đăng nhập Google ($code)", "Google sign-in error ($code)")
    val syncNotConnected get() = t("Chưa bật đồng bộ: vào Cài đặt để kết nối Google Drive", "Sync is off: connect Google Drive in Settings")
    val conflictCopy get() = t("bản trên máy này", "this device's copy")
    fun conflictsKept(n: Int) = t("$n ghi chú bị sửa ở 2 nơi cùng lúc: đã giữ cả hai bản", "$n note(s) were edited in two places: both versions kept")
    val wifiOnly get() = t("Ảnh & ghi âm chỉ qua Wi-Fi", "Photos & recordings on Wi-Fi only")
    val wifiOnlyText get() = t("Chữ luôn được đồng bộ; tệp lớn chờ có Wi-Fi", "Text always syncs; big files wait for Wi-Fi")
    fun attachmentsPending(n: Int) = t("$n ảnh/ghi âm đang chờ đồng bộ", "$n photo(s)/recording(s) waiting to sync")

    // Settings
    val appearance get() = t("Giao diện", "Appearance")
    val theme get() = t("Chủ đề", "Theme")
    val themeSystem get() = t("Theo máy", "System")
    val themeLight get() = t("Sáng", "Light")
    val themeDark get() = t("Tối", "Dark")
    val textSize get() = t("Cỡ chữ ghi chú", "Note text size")
    val language get() = t("Ngôn ngữ", "Language")
    val checkedLast get() = t("Mục đã xong xuống cuối", "Move checked items to bottom")
    val checkedLastText get() = t("Trong danh sách checklist", "In checklists")
    val security get() = t("Bảo mật", "Security")
    val appLock get() = t("Khóa ứng dụng", "App lock")
    val appLockText get() = t("Vân tay / khuôn mặt / mã PIN khi mở app", "Fingerprint / face / PIN when opening")
    val noScreenLock get() = t("Máy chưa đặt khóa màn hình", "Set a screen lock on the phone first")
    val appLocked get() = t("Ghi Chú đã khóa", "Ghi Chú is locked")
    val promptTitle get() = t("Mở khóa Ghi Chú", "Unlock Ghi Chú")
    val promptSubtitle get() = t("Dùng vân tay, khuôn mặt hoặc mã PIN", "Use fingerprint, face or PIN")
    val data get() = t("Dữ liệu", "Data")
    val backup get() = t("Sao lưu ra file", "Back up to a file")
    val backupText get() = t("Một file .zip gồm ghi chú, nhãn, ảnh và ghi âm", "One .zip with notes, labels, photos and recordings")
    fun backupDone(n: Int) = t("Đã sao lưu $n ghi chú", "$n notes backed up")
    val restoreBackup get() = t("Khôi phục từ file", "Restore from a file")
    val restoreText get() = t("Gộp vào ghi chú hiện có, không xóa gì", "Merged into your notes, nothing is deleted")
    fun restoreDone(n: Int) = t("Đã khôi phục $n ghi chú", "$n notes restored")
    val exportMarkdown get() = t("Xuất tất cả ra Markdown", "Export all as Markdown")
    val exportMarkdownText get() = t("File .zip, mỗi ghi chú một file .md (mở được bằng Obsidian…)", ".zip with one .md per note (opens in Obsidian…)")
    fun exportDone(n: Int) = t("Đã xuất $n ghi chú", "$n notes exported")
    val importKeep get() = t("Nhập từ Google Keep", "Import from Google Keep")
    val importKeepText get() = t("takeout.google.com → chỉ chọn Keep → tải .zip về → chọn file ở đây", "takeout.google.com → select only Keep → download the .zip → pick it here")
    val importing get() = t("Đang nhập…", "Importing…")
    fun keepDone(n: Int) = t("Đã nhập $n ghi chú từ Google Keep", "$n notes imported from Google Keep")
    val about get() = t("Giới thiệu", "About")
    val aboutText get() = t("App ghi chú cá nhân. Dữ liệu chỉ nằm trên máy và Google Drive của bạn.", "A personal notes app. Your data lives only on your phone and your Google Drive.")

    // Widgets
    val widgetNoNote get() = t("Ghi chú đã bị xóa. Chạm để chọn ghi chú khác.", "The note was deleted. Tap to pick another.")
    val widgetPick get() = t("Chọn ghi chú cho widget", "Pick a note for the widget")
    val widgetNotChosen get() = t("Chưa chọn ghi chú. Chạm để chọn.", "No note chosen. Tap to pick one.")
    val widgetSearch get() = t("Tìm ghi chú", "Search notes")

    // Templates
    val templates get() = t("Mẫu ghi chú", "Note templates")
    val templatesHint get() = t(
        "Mẫu là ghi chú dùng lại được. {{date}}, {{time}}, {{weekday}} trong mẫu được thay bằng ngày, giờ, thứ khi tạo ghi chú.",
        "A template is a note you reuse. {{date}}, {{time}}, {{weekday}} in it become the date, time and weekday when a note is made.",
    )
    val templatesText get() = t("Ghi chú làm sẵn để tạo nhanh (nhật ký, họp…)", "Ready-made notes to start from (journal, meeting…)")
    val newTemplate get() = t("Mẫu mới", "New template")
    val newChecklistTemplate get() = t("Mẫu danh sách", "Checklist template")
    val noTemplates get() = t("Chưa có mẫu nào", "No templates yet")
    val noTemplatesText get() = t("Tạo mẫu ở đây, hoặc mở một ghi chú → ⋮ → Lưu làm mẫu.", "Make one here, or open a note → ⋮ → Save as template.")
    val fromTemplate get() = t("Từ mẫu", "From template")
    val manageTemplates get() = t("Quản lý", "Manage")
    val templateDeleted get() = t("Đã xóa mẫu", "Template deleted")
    val templateSaved get() = t("Đã lưu làm mẫu", "Saved as a template")
    val saveAsTemplate get() = t("Lưu làm mẫu", "Save as template")
    val editingTemplate get() = t("Đây là mẫu: ghi chú mới tạo từ mẫu sẽ chép nội dung này.", "This is a template: new notes made from it copy this content.")
    fun weekday(isoDay: Int) = (if (en) listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    else listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"))[isoDay - 1]

    // Links between notes
    val fmtNoteLink get() = t("Liên kết ghi chú [[ ]]", "Link to a note [[ ]]")
    fun linkCreated(title: String) = t("Đã tạo ghi chú mới \"$title\"", "New note \"$title\" created")
    val backlinks get() = t("Được liên kết từ", "Linked from")
    fun newLinkedNote(title: String) = t("Tạo ghi chú \"$title\"", "Create note \"$title\"")

    // Share as image / PDF
    val shareAsImage get() = t("Chia sẻ dạng ảnh", "Share as image")
    val exportPdf get() = t("Xuất PDF / In", "Export PDF / Print")

    // Drawing
    val drawing get() = t("Hình vẽ", "Drawing")
    val draw get() = t("Vẽ / viết tay", "Draw / handwrite")
    val newDrawing get() = t("Hình vẽ", "Drawing")
    val pen get() = t("Bút", "Pen")
    val marker get() = t("Bút dạ quang", "Highlighter")
    val eraser get() = t("Tẩy", "Eraser")
    val clearPage get() = t("Xóa trang", "Clear page")
    val deleteDrawing get() = t("Xóa hình vẽ", "Delete drawing")
    val discard get() = t("Bỏ", "Discard")
    val editDrawing get() = t("Vẽ tiếp", "Edit drawing")

    // End-to-end encryption
    val e2e get() = t("Mã hóa đầu cuối", "End-to-end encryption")
    val e2eOffText get() = t("Tắt: Google có thể đọc nội dung trên Drive", "Off: Google can read what is on Drive")
    val e2eOnText get() = t("Bật: Drive chỉ chứa dữ liệu đã mã hóa", "On: Drive holds only encrypted data")
    val e2eLockedText get() = t("Máy khác đã bật mã hóa: nhập mật khẩu để đồng bộ", "Another device turned encryption on: enter the passphrase to sync")
    val e2eEnable get() = t("Bật mã hóa đầu cuối", "Turn on end-to-end encryption")
    val e2eDisable get() = t("Tắt mã hóa đầu cuối", "Turn off end-to-end encryption")
    val e2eUnlock get() = t("Nhập mật khẩu mã hóa", "Enter encryption passphrase")
    val e2eWarning get() = t(
        "Ghi chú, ảnh và ghi âm được mã hóa trên máy trước khi tải lên Drive. Mật khẩu không được lưu ở đâu khác: " +
            "quên mật khẩu thì máy mới sẽ không đọc được dữ liệu trên Drive (dữ liệu trên máy này vẫn còn). " +
            "Các máy khác phải nhập cùng mật khẩu.",
        "Notes, photos and recordings are encrypted on the phone before they go to Drive. The passphrase is stored " +
            "nowhere else: if you forget it, a new phone cannot read Drive (the notes on this phone stay). Other " +
            "devices need the same passphrase.",
    )
    val e2eDisableText get() = t("Mọi thứ trên Drive sẽ được lưu lại dạng không mã hóa.", "Everything on Drive will be stored unencrypted again.")
    val passphrase get() = t("Mật khẩu", "Passphrase")
    val passphraseRepeat get() = t("Nhập lại mật khẩu", "Repeat passphrase")
    val passphraseShort get() = t("Ít nhất 8 ký tự", "At least 8 characters")
    val passphraseMismatch get() = t("Hai mật khẩu không giống nhau", "The passphrases differ")
    val wrongPassphrase get() = t("Sai mật khẩu", "Wrong passphrase")
    val e2eAlreadyOn get() = t("Máy khác đã bật mã hóa: hãy nhập mật khẩu đã đặt ở máy đó", "Another device already turned it on: enter the passphrase set there")
    val e2eEnabled get() = t("Đã bật mã hóa: đang mã hóa dữ liệu trên Drive…", "Encryption on: encrypting what is on Drive…")
    val e2eDisabled get() = t("Đã tắt mã hóa", "Encryption off")
    val e2eUnlocked get() = t("Mật khẩu đúng: đang đồng bộ…", "Passphrase accepted: syncing…")
    val syncNeedsPassphrase get() = t("Drive đã được mã hóa: nhập mật khẩu trong Cài đặt", "Drive is encrypted: enter the passphrase in Settings")
    fun unreadableFiles(n: Int) = t("$n tệp trên Drive mã hóa bằng mật khẩu khác", "$n Drive files use another passphrase")

    // Today's note
    val todayNote get() = t("Ghi chú hôm nay", "Today's note")
    val dailyTemplate get() = t("Mẫu cho ghi chú hôm nay", "Template for today's note")
    val dailyNoTemplate get() = t("Không dùng mẫu (tiêu đề là ngày)", "No template (the title is the date)")
    fun dailyTemplateText(name: String?) =
        if (name == null) t("Chưa chọn: ghi chú mới chỉ có ngày làm tiêu đề", "None: a new note titled with the date")
        else t("Mẫu: $name", "Template: $name")
    val dailyHint get() = t(
        "Mỗi ngày một ghi chú. Nếu tiêu đề mẫu có {{date}} thì dùng tiêu đề đó, không thì tiêu đề là ngày.",
        "One note per day. If the template's title has {{date}} it is used, otherwise the title is the date.",
    )

    // Note-list widget
    val widgetListPick get() = t("Widget hiển thị gì?", "What should the widget show?")
    val widgetPinned get() = t("Ghi chú đã ghim", "Pinned notes")
    val widgetRecent get() = t("Ghi chú mới sửa", "Recently edited notes")
    fun widgetLabel(name: String) = t("Nhãn: $name", "Label: $name")
    val widgetListEmpty get() = t("Không có ghi chú nào", "No notes")

    // Read aloud
    val readAloud get() = t("Đọc to", "Read aloud")
    val stopReading get() = t("Dừng đọc", "Stop reading")
    val noTts get() = t("Máy chưa có bộ chuyển văn bản thành giọng nói", "This phone has no text-to-speech engine")
    val noTtsVoice get() = t(
        "Chưa có giọng đọc cho ngôn ngữ này: thêm trong Cài đặt Android → Chuyển văn bản thành giọng nói",
        "No voice for this language: add one in Android Settings → Text-to-speech",
    )
    val nothingToRead get() = t("Ghi chú trống, không có gì để đọc", "The note is empty")

    // History: what changed
    val versionContent get() = t("Nội dung", "Content")
    val versionChanges get() = t("So với hiện tại", "Compared with now")
    val noChanges get() = t("Giống hệt bản hiện tại", "Same as the current version")
    fun changesCount(added: Int, removed: Int) = t("+$added dòng thêm, −$removed dòng bỏ", "+$added lines added, −$removed removed")

    // Tables
    val fmtTable get() = t("Chèn bảng", "Insert table")
    val tableColumn get() = t("Cột", "Column")

    // Files
    val attachFile get() = t("Tệp (PDF, Word, Excel…)", "File (PDF, Word, Excel…)")
    val fileTooLarge get() = t("Tệp quá lớn (tối đa 20 MB)", "File too large (20 MB at most)")
    val fileFailed get() = t("Không thêm được tệp", "Could not add the file")
    val noAppForFile get() = t("Không có ứng dụng nào mở được tệp này", "No app can open this file")
    val files get() = t("Tệp đính kèm", "Files")

    // Scan / text from images
    val scanDocument get() = t("Quét tài liệu", "Scan document")
    val scanUnavailable get() = t("Máy không quét được (cần Google Play services)", "Scanning is not available (needs Google Play services)")
    fun scannedPages(n: Int) = t("Đã thêm $n trang", "$n pages added")
    val extractText get() = t("Lấy chữ trong ảnh", "Copy text from image")
    val extractTextAll get() = t("Lấy chữ", "Get text")
    val readingText get() = t("Đang đọc chữ trong ảnh…", "Reading the text…")
    val textAdded get() = t("Đã thêm chữ vào ghi chú", "Text added to the note")
    val noTextFound get() = t("Không thấy chữ nào trong ảnh", "No text found in the image")

    // Reminder calendar
    val calendar get() = t("Lịch nhắc nhở", "Reminder calendar")
    fun monthTitle(month: Int, year: Int) = if (en) java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " $year" else "Tháng $month, $year"
    fun weekdayShort(isoDay: Int) = (if (en) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") else listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN"))[isoDay - 1]
    val noRemindersDay get() = t("Không có nhắc nhở trong ngày này", "No reminders on this day")
    val prevMonth get() = t("Tháng trước", "Previous month")
    val nextMonth get() = t("Tháng sau", "Next month")

    // Automatic backup
    val autoBackup get() = t("Tự động sao lưu hằng tuần", "Weekly automatic backup")
    val autoBackupOffText get() = t(
        "Tắt · mỗi tuần một file .zip vào thư mục bạn chọn, giữ 5 bản mới nhất",
        "Off · a .zip every week into a folder you choose, the newest 5 kept",
    )
    fun autoBackupOnText(folder: String, last: String?) =
        t("Thư mục: $folder · lần cuối: ${last ?: "chưa có"}", "Folder: $folder · last: ${last ?: "not yet"}")
    val autoBackupFailedText get() = t("Lần trước không ghi được vào thư mục: hãy chọn lại", "Last time the folder could not be written: choose it again")
    val autoBackupNow get() = t("Sao lưu ngay", "Back up now")
    val changeFolder get() = t("Đổi thư mục", "Change folder")
    val turnOff get() = t("Tắt", "Turn off")
    val autoBackupEnabled get() = t("Đã bật tự động sao lưu", "Automatic backup on")
    val autoBackupDisabled get() = t("Đã tắt tự động sao lưu", "Automatic backup off")
}
