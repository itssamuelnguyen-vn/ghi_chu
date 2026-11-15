package com.samuelcruz.ghichu.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.samuelcruz.ghichu.UiEvent
import com.samuelcruz.ghichu.core.AppLock
import com.samuelcruz.ghichu.core.I18n
import com.samuelcruz.ghichu.core.Links
import com.samuelcruz.ghichu.core.MdEdit
import com.samuelcruz.ghichu.core.Repeat
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.core.fileNameOf
import com.samuelcruz.ghichu.core.wordCount
import com.samuelcruz.ghichu.data.Attachment
import com.samuelcruz.ghichu.data.AttachmentKind
import com.samuelcruz.ghichu.data.Files
import com.samuelcruz.ghichu.data.Note
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    id: String,
    start: String?,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    /** Open another note in place of this one (a duplicate). */
    onOpenNote: (String) -> Unit,
    /** Open another note on top of this one (a `[[link]]`): Back comes back here. */
    onFollowLink: (String) -> Unit,
    /** Open the drawing screen: a new drawing (null) or that drawing attachment. */
    onDraw: (String?) -> Unit,
    onShareImage: () -> Unit,
) {
    val vm = appViewModel(key = "edit-$id") { EditorViewModel(it, id) }
    val app = app()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val note = vm.note
    val attachments by vm.attachments.collectAsState()
    val labels by vm.labels.collectAsState()
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(NoteColors.background(note?.color ?: 0), label = "bg")
    val scale = LocalNoteScale.current

    var attachSheet by remember { mutableStateOf(false) }
    var colorSheet by remember { mutableStateOf(false) }
    var labelSheet by remember { mutableStateOf(false) }
    var reminderDialog by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Attachment?>(null) }
    var bodyFocused by remember { mutableStateOf(false) }
    var startHandled by rememberSaveable { mutableStateOf(false) }

    BackHandler { onBack() }

    // Launchers for the camera, the photo picker, dictation, notifications, and saving a .md file.
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile?.let { File(it) }
        if (ok && f != null && f.exists()) vm.addImage(Uri.fromFile(f))
    }
    fun openCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "shot-${System.currentTimeMillis()}.jpg")
        cameraFile = f.path
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
        runCatching { takePicture.launch(uri) }.onFailure { app.events.tryEmit(UiEvent(S.noCamera)) }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        uris.forEach { vm.addImage(it) }
    }
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.insertText(it) }
        }
    }
    fun startDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (I18n.lang == "en") "en-US" else "vi-VN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, S.speakNow)
        try { dictate.launch(intent) } catch (e: ActivityNotFoundException) { app.events.tryEmit(UiEvent(S.noSpeech)) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addFile(uri)
    }
    val scan = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.addScannedPages(Scanner.pages(r.data))
    }
    fun startScan() {
        val a = activity ?: return
        Scanner.start(
            a,
            onIntent = { scan.launch(IntentSenderRequest.Builder(it).build()) },
            onError = { app.events.tryEmit(UiEvent(S.scanUnavailable)) },
        )
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) app.events.tryEmit(UiEvent(S.notifDenied))
    }
    val saveMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) scope.launch {
            val names = note?.labelIds?.mapNotNull { lid -> labels.firstOrNull { it.id == lid }?.name }.orEmpty()
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(vm.markdown(names).toByteArray()) } }
                .onSuccess { app.events.emit(UiEvent(S.exported)) }
        }
    }

    // Started from "+ photo / recording / voice": open that right away.
    LaunchedEffect(note != null) {
        if (note == null || startHandled || start == null) return@LaunchedEffect
        startHandled = true
        when (start) {
            "photo" -> attachSheet = true
            "camera" -> openCamera()
            "record" -> recording = true
            "voice" -> startDictation()
            "draw" -> onDraw(null)
            "scan" -> startScan()
        }
    }

    val locked = note?.locked == true && app.notesUnlockedAt == 0L
    val readOnly = note?.inTrash == true

    Box(Modifier.fillMaxSize().noteBounds(id).background(bg)) {
        if (note == null) return@Box
        Column(Modifier.fillMaxSize().imePadding()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) }
                Spacer(Modifier.weight(1f))
                if (vm.speaking) {
                    IconButton(onClick = vm::toggleReadAloud) { Icon(Icons.Default.StopCircle, S.stopReading, tint = cs.primary) }
                }
                if (!readOnly && !locked) {
                    if (!vm.isChecklist) {
                        IconButton(onClick = { vm.reading = !vm.reading }) {
                            Icon(if (vm.reading) Icons.Default.Edit else Icons.AutoMirrored.Filled.MenuBook, if (vm.reading) S.editMode else S.readMode)
                        }
                    }
                    IconButton(onClick = vm::togglePin) {
                        Icon(if (note.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin, if (note.pinned) S.unpin else S.pin)
                    }
                    IconButton(onClick = { reminderDialog = true }) { Icon(Icons.Default.AddAlert, S.reminder) }
                    IconButton(onClick = { vm.toggleArchive(onBack) }) {
                        Icon(if (note.archived) Icons.Default.Unarchive else Icons.Default.Archive, S.archive)
                    }
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, S.more) }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        @Composable
                        fun item(text: String, icon: ImageVector, action: () -> Unit) =
                            DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = { moreMenu = false; action() })
                        if (readOnly) {
                            item(S.restore, Icons.Default.Restore) { vm.restoreFromTrash() }
                        } else {
                            item(S.delete, Icons.Default.Delete) { vm.trash(onBack) }
                            item(S.duplicate, Icons.Default.ContentCopy) { vm.duplicate(onOpenNote) }
                            item(S.share, Icons.Default.Share) { share(context, vm.shareText(), attachments) }
                            item(S.shareAsImage, Icons.Default.Image) { onShareImage() }
                            if (!locked) {
                                item(if (vm.speaking) S.stopReading else S.readAloud, if (vm.speaking) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp) {
                                    vm.toggleReadAloud()
                                }
                            }
                            item(S.exportPdf, Icons.Default.PictureAsPdf) { activity?.let { vm.exportPdf(it) } }
                            item(S.saveMarkdown, Icons.Default.FileDownload) {
                                saveMd.launch(fileNameOf(vm.title.text, S.noteFileName) + ".md")
                            }
                            item(if (vm.isChecklist) S.toText else S.toChecklist, if (vm.isChecklist) Icons.Default.TextFormat else Icons.Default.CheckBox) { vm.convertKind() }
                            if (vm.isChecklist) {
                                item(S.uncheckAll, Icons.Default.CheckBoxOutlineBlank) { vm.uncheckAll() }
                                item(S.deleteChecked, Icons.Default.Delete) { vm.deleteChecked() }
                            }
                            if (AppLock.canLock(context)) {
                                item(if (note.locked) S.unlockNote else S.lockNote, if (note.locked) Icons.Default.LockOpen else Icons.Default.Lock) {
                                    if (note.locked) vm.setLocked(false)
                                    else {
                                        vm.setLocked(true)
                                        app.notesUnlockedAt = SystemClock.elapsedRealtime()
                                        app.events.tryEmit(UiEvent(S.noteLockedInfo))
                                    }
                                }
                            }
                            if (!vm.isTemplate) item(S.saveAsTemplate, Icons.Outlined.Description) { vm.saveAsTemplate() }
                            item(S.history, Icons.Default.History) { onHistory() }
                        }
                    }
                }
            }

            if (locked) {
                LockedNote(onUnlock = {
                    activity?.let { a -> AppLock.authenticate(a, onSuccess = { app.notesUnlockedAt = SystemClock.elapsedRealtime() }) }
                })
                return@Column
            }

            val scroll = rememberScrollState()
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),
            ) {
                if (vm.isTemplate) {
                    Text(
                        S.editingTemplate,
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp)).background(cs.secondaryContainer).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                // Photos and drawings; tapping a drawing opens it to draw more.
                val images = attachments.filter { AttachmentKind.isPicture(it.kind) }
                if (images.isNotEmpty()) {
                    ImageGrid(images, onOpen = { a -> if (a.kind == AttachmentKind.DRAWING && !readOnly) onDraw(a.id) else viewer = a })
                }
                Spacer(Modifier.height(8.dp))
                val titleStyle = MaterialTheme.typography.headlineSmall.copy(color = cs.onSurface, fontSize = MaterialTheme.typography.headlineSmall.fontSize * scale)
                BasicTextField(
                    value = vm.title,
                    onValueChange = vm::onTitle,
                    readOnly = readOnly,
                    textStyle = titleStyle,
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    decorationBox = { inner ->
                        Box {
                            if (vm.title.text.isEmpty()) Text(S.titleHint, style = titleStyle.copy(color = cs.onSurface.copy(alpha = 0.35f)))
                            inner()
                        }
                    },
                )
                val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = cs.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * scale,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * scale,
                )
                if (vm.isChecklist) {
                    ChecklistEditor(vm, bodyStyle, app.prefs.checkedLast, readOnly)
                } else if (vm.reading || readOnly) {
                    if (vm.body.text.isBlank()) {
                        Text(S.bodyHint, style = bodyStyle.copy(color = cs.onSurface.copy(alpha = 0.35f)), modifier = Modifier.padding(horizontal = 20.dp))
                    } else {
                        MarkdownView(
                            vm.body.text, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                            style = bodyStyle, onToggleTask = if (readOnly) null else vm::toggleTaskInReading,
                            onNoteLink = { vm.openLink(it, onFollowLink) },
                        )
                    }
                } else {
                    val colors = mdColors()
                    val transformation = remember(colors) { MarkdownTransformation(colors) }
                    BasicTextField(
                        value = vm.body,
                        onValueChange = vm::onBody,
                        textStyle = bodyStyle,
                        cursorBrush = SolidColor(cs.primary),
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                            .onFocusChanged { bodyFocused = it.isFocused },
                        decorationBox = { inner ->
                            Box {
                                if (vm.body.text.isEmpty()) Text(S.bodyHint, style = bodyStyle.copy(color = cs.onSurface.copy(alpha = 0.35f)))
                                inner()
                            }
                        },
                    )
                }

                val audios = attachments.filter { it.kind == AttachmentKind.AUDIO }
                if (audios.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        audios.forEach { a ->
                            AudioRow(Files.file(context, a), a.durationMs, onDelete = if (readOnly) null else ({ vm.removeAttachment(a) }))
                        }
                    }
                }

                val files = attachments.filter { it.kind == AttachmentKind.FILE }
                if (files.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        files.forEach { a ->
                            FileRow(
                                a,
                                onOpen = { if (!AttachedFiles.open(context, a)) app.events.tryEmit(UiEvent(S.noAppForFile)) },
                                onShare = { AttachedFiles.share(context, a) },
                                onDelete = if (readOnly) null else ({ vm.removeAttachment(a) }),
                            )
                        }
                    }
                }

                // Reminder + labels as chips.
                val noteLabels = note.labelIds.mapNotNull { lid -> labels.firstOrNull { it.id == lid } }
                if (note.reminderAt != null || noteLabels.isNotEmpty()) {
                    FlowRow(
                        Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        note.reminderAt?.let { at ->
                            val overdue = note.reminderRepeat == Repeat.NONE && at < System.currentTimeMillis()
                            Chip(
                                reminderLabel(at, note.reminderRepeat),
                                if (note.reminderRepeat != Repeat.NONE) Icons.Default.Repeat else Icons.Default.Alarm,
                                strike = overdue,
                            ) { if (!readOnly) reminderDialog = true }
                        }
                        noteLabels.forEach { l -> Chip(l.name, Icons.AutoMirrored.Filled.Label) { if (!readOnly) labelSheet = true } }
                    }
                }
                // Notes whose text links here with [[this title]].
                val all by vm.allNotes.collectAsState()
                val backlinks = remember(all, vm.title.text) { vm.backlinksIn(all, vm.title.text) }
                if (backlinks.isNotEmpty() && !vm.isTemplate) {
                    Text(
                        S.backlinks, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                    )
                    FlowRow(
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        backlinks.take(20).forEach { n -> Chip(linkTitle(n), Icons.Default.Link) { onFollowLink(n.id) } }
                    }
                }
                Text(
                    S.createdEdited(fullDateTime(note.createdAt), fullDateTime(note.updatedAt)) + " · " +
                        S.words(wordCount(vm.title.text + " " + (if (vm.isChecklist) vm.items.joinToString(" ") { it.text.text } else vm.body.text))),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(80.dp))
            }

            if (!readOnly) {
                val linkQuery = if (bodyFocused && !vm.isChecklist && !vm.reading) {
                    Links.queryAt(vm.body.text, vm.body.selection.start).takeIf { vm.body.selection.collapsed }
                } else null
                if (linkQuery != null) LinkSuggestions(vm, linkQuery.first, linkQuery.second)
                AnimatedVisibility(bodyFocused && !vm.isChecklist && !vm.reading, enter = expandVertically(), exit = shrinkVertically()) {
                    FormatBar(vm)
                }
                BottomBar(
                    note.updatedAt, vm,
                    onAttach = { attachSheet = true },
                    onColor = { colorSheet = true },
                    onLabels = { labelSheet = true },
                )
            }
        }
    }

    if (attachSheet) {
        ModalBottomSheet(onDismissRequest = { attachSheet = false }) {
            SheetRow(Icons.Default.CameraAlt, S.takePhoto) { attachSheet = false; openCamera() }
            SheetRow(Icons.Default.Image, S.choosePhotos) {
                attachSheet = false
                pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            SheetRow(Icons.Default.DocumentScanner, S.scanDocument) { attachSheet = false; startScan() }
            SheetRow(Icons.Default.AttachFile, S.attachFile) { attachSheet = false; pickFile.launch(arrayOf("*/*")) }
            SheetRow(Icons.Default.Mic, S.recordAudio) { attachSheet = false; recording = true }
            SheetRow(Icons.Default.RecordVoiceOver, S.dictate) { attachSheet = false; startDictation() }
            SheetRow(Icons.Default.Brush, S.draw) { attachSheet = false; onDraw(null) }
            if (!vm.isChecklist) SheetRow(Icons.Default.AddBox, S.toChecklist) { attachSheet = false; vm.convertKind() }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (colorSheet) {
        ModalBottomSheet(onDismissRequest = { colorSheet = false }) {
            Text(S.color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
            ColorRow(note?.color ?: 0, onPick = { vm.setColor(it) })
            Spacer(Modifier.height(32.dp))
        }
    }
    if (labelSheet && note != null) {
        LabelPicker(labels, note.labelIds.toSet(), onDone = { add, remove -> vm.setLabels(add, remove); labelSheet = false }, onDismiss = { labelSheet = false })
    }
    if (reminderDialog && note != null) {
        ReminderDialog(
            note.reminderAt, note.reminderRepeat,
            onSet = { at, repeat ->
                vm.setReminder(at, repeat)
                reminderDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onRemove = { vm.setReminder(null, 0); reminderDialog = false },
            onDismiss = { reminderDialog = false },
        )
    }
    if (recording) {
        RecordDialog(onSaved = { f, len -> vm.addAudio(f, len); recording = false }, onDismiss = { recording = false })
    }
    viewer?.let { a ->
        ImageViewer(
            a, onClose = { viewer = null }, onDelete = if (readOnly) null else ({ vm.removeAttachment(a); viewer = null }),
            onShare = { share(context, "", listOf(a)) },
            onExtract = if (readOnly) null else ({ viewer = null; vm.extractText(listOf(a)) }),
        )
    }
}

@Composable
private fun LockedNote(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        IconBadge(Icons.Default.Lock, MaterialTheme.colorScheme.primary, 88.dp)
        Spacer(Modifier.height(18.dp))
        Text(S.lockedNote, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onUnlock) { Text(S.unlock) }
    }
}

@Composable
private fun Chip(text: String, icon: ImageVector, strike: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, textDecoration = if (strike) TextDecoration.LineThrough else null)
    }
}

@Composable
private fun SheetRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(18.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ImageGrid(images: List<Attachment>, onOpen: (Attachment) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (images.size == 1) {
            AttachmentImage(
                images[0].fileName,
                Modifier.fillMaxWidth().aspectRatio(1.4f).clickable { onOpen(images[0]) },
                maxSide = 1400,
            )
        } else {
            images.chunked(if (images.size == 2 || images.size == 4) 2 else 3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { a ->
                        AttachmentImage(a.fileName, Modifier.weight(1f).aspectRatio(1f).clickable { onOpen(a) }, maxSide = 700)
                    }
                    repeat((if (images.size == 2 || images.size == 4) 2 else 3) - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Markdown buttons above the keyboard. */
@Composable
private fun FormatBar(vm: EditorViewModel) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(cs.surfaceContainerHigh.copy(alpha = 0.92f)).horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun b(icon: ImageVector, label: String, action: () -> Unit) = IconButton(onClick = action) { Icon(icon, label) }
        b(Icons.Default.Title, S.fmtHeading) { vm.toggleLines(MdEdit.LineKind.HEADING) }
        b(Icons.Default.FormatBold, S.fmtBold) { vm.wrap("**") }
        b(Icons.Default.FormatItalic, S.fmtItalic) { vm.wrap("*") }
        b(Icons.Default.FormatStrikethrough, S.fmtStrike) { vm.wrap("~~") }
        b(Icons.AutoMirrored.Filled.FormatListBulleted, S.fmtBullets) { vm.toggleLines(MdEdit.LineKind.BULLET) }
        b(Icons.Default.FormatListNumbered, S.fmtNumbers) { vm.toggleLines(MdEdit.LineKind.NUMBERED) }
        b(Icons.Default.CheckBox, S.fmtTasks) { vm.toggleLines(MdEdit.LineKind.TASK) }
        b(Icons.Default.FormatQuote, S.fmtQuote) { vm.toggleLines(MdEdit.LineKind.QUOTE) }
        b(Icons.Default.Code, S.fmtCode) { vm.wrap("`") }
        b(Icons.Default.Link, S.fmtLink) { vm.insertLink() }
        b(Icons.Default.AddLink, S.fmtNoteLink) { vm.insertNoteLink() }
        b(Icons.Default.TableChart, S.fmtTable) { vm.insertTable() }
    }
}

/** An attached file: tap to open it in another app. */
@Composable
private fun FileRow(a: Attachment, onOpen: () -> Unit, onShare: () -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val size = remember(a.id) { AttachedFiles.size(context, a) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onOpen).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (a.ext) {
                "pdf" -> Icons.Default.PictureAsPdf
                "zip", "rar", "7z" -> Icons.Default.FolderZip
                else -> Icons.AutoMirrored.Filled.InsertDriveFile
            },
            null, tint = cs.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (size > 0) sizeLabel(size) else a.ext.uppercase(),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
            )
        }
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, S.share) }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Close, S.delete) }
    }
}

/** A note's name in link chips (a locked note without a title shows "Locked note", never its text). */
private fun linkTitle(n: Note): String =
    if (n.locked && n.title.isBlank()) S.lockedNote else n.displayTitle().ifBlank { S.untitled }

/** While typing `[[…`: matching notes to pick, or "create note …" when none has that title. */
@Composable
private fun LinkSuggestions(vm: EditorViewModel, start: Int, query: String) {
    val cs = MaterialTheme.colorScheme
    val all by vm.allNotes.collectAsState()
    val list = remember(all, query) { vm.linkSuggestions(query) }
    val exact = query.isNotBlank() && list.any { Links.key(it.displayTitle()) == Links.key(query) }
    Row(
        Modifier.fillMaxWidth().background(cs.surfaceContainerHigh.copy(alpha = 0.96f)).horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AddLink, null, tint = cs.primary)
        list.forEach { n ->
            val t = n.displayTitle()
            Chip(if (n.locked && n.title.isBlank()) S.lockedNote else t, Icons.Outlined.Description) { vm.completeLink(start, t) }
        }
        if (query.isNotBlank() && !exact) Chip(S.newLinkedNote(query.trim()), Icons.Default.Add) { vm.completeLink(start, query) }
    }
}

@Composable
private fun BottomBar(updatedAt: Long, vm: EditorViewModel, onAttach: () -> Unit, onColor: () -> Unit, onLabels: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAttach) { Icon(Icons.Default.AddBox, S.add) }
        IconButton(onClick = onColor) { Icon(Icons.Default.Palette, S.color) }
        IconButton(onClick = onLabels) { Icon(Icons.AutoMirrored.Filled.Label, S.labels) }
        Text(
            editedLabel(updatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
        IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, S.undo) }
        IconButton(onClick = vm::redo, enabled = vm.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, S.redo) }
    }
}

/** Checklist rows: drag handle, tick box, text; done items in their own folding section. */
@Composable
private fun ChecklistEditor(vm: EditorViewModel, style: androidx.compose.ui.text.TextStyle, checkedLast: Boolean, readOnly: Boolean) {
    val requesters = remember { mutableStateMapOf<Long, FocusRequester>() }
    val heights = remember { mutableStateMapOf<Long, Int>() }
    var dragKey by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var showDone by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(vm.focusKey) {
        val k = vm.focusKey ?: return@LaunchedEffect
        delay(40)
        runCatching { requesters[k]?.requestFocus() }
        vm.items.firstOrNull { it.key == k }?.let { it.text = it.text.copy(selection = TextRange(it.text.text.length)) }
        vm.focusKey = null
    }

    @Composable
    fun row(item: EditItem, draggable: Boolean) {
        val fr = requesters.getOrPut(item.key) { FocusRequester() }
        var focused by remember { mutableStateOf(false) }
        val dragging = dragKey == item.key
        Row(
            Modifier
                .fillMaxWidth()
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                .then(if (dragging) Modifier.shadow(8.dp, RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)) else Modifier)
                .onGloballyPositioned { heights[item.key] = it.size.height }
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (draggable && !readOnly) {
                Icon(
                    Icons.Default.DragIndicator, S.dragToMove,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(36.dp).padding(6.dp).pointerInput(item.key) {
                        detectDragGestures(
                            onDragStart = { dragKey = item.key; dragOffset = 0f },
                            onDragEnd = { dragKey = null; dragOffset = 0f },
                            onDragCancel = { dragKey = null; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val list = vm.items
                                val i = list.indexOfFirst { it.key == item.key }
                                if (i < 0) return@detectDragGestures
                                if (dragOffset > 0) {
                                    val j = (i + 1 until list.size).firstOrNull { !list[it].checked || !checkedLast }
                                    if (j != null) {
                                        val h = heights[list[j].key] ?: return@detectDragGestures
                                        if (dragOffset > h / 2f) { vm.moveItem(i, j); dragOffset -= h }
                                    }
                                } else {
                                    val j = (i - 1 downTo 0).firstOrNull { !list[it].checked || !checkedLast }
                                    if (j != null) {
                                        val h = heights[list[j].key] ?: return@detectDragGestures
                                        if (-dragOffset > h / 2f) { vm.moveItem(i, j); dragOffset += h }
                                    }
                                }
                            },
                        )
                    },
                )
            } else {
                Spacer(Modifier.width(36.dp))
            }
            Checkbox(
                checked = item.checked,
                onCheckedChange = { if (!readOnly) vm.toggleItem(item) },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)),
            )
            BasicTextField(
                value = item.text,
                onValueChange = { vm.onItemText(item, it) },
                readOnly = readOnly,
                textStyle = style.copy(
                    color = if (item.checked) style.color.copy(alpha = 0.5f) else style.color,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .focusRequester(fr)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.key == Key.Backspace && e.type == KeyEventType.KeyDown && item.text.text.isEmpty()) {
                            vm.removeItem(item); true
                        } else false
                    },
            )
            if (focused && !readOnly) {
                IconButton(onClick = { vm.removeItem(item) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, S.delete, Modifier.size(18.dp))
                }
            }
        }
    }

    val items = vm.items
    Column(Modifier.fillMaxWidth()) {
        if (checkedLast) {
            items.filter { !it.checked }.forEach { key(it.key) { row(it, draggable = true) } }
        } else {
            items.forEach { key(it.key) { row(it, draggable = true) } }
        }
        if (!readOnly) {
            Row(
                Modifier.fillMaxWidth().clickable { vm.addItem() }.padding(start = 40.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
                Text(S.addItem, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
        val done = items.filter { it.checked }
        if (checkedLast && done.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            Row(
                Modifier.fillMaxWidth().clickable { showDone = !showDone }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (showDone) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(S.doneItems(done.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showDone) done.forEach { key(it.key) { row(it, draggable = false) } }
        }
    }
}

/** Full-screen photo with pinch-to-zoom. */
@Composable
private fun ImageViewer(a: Attachment, onClose: () -> Unit, onDelete: (() -> Unit)?, onShare: () -> Unit, onExtract: (() -> Unit)?) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AttachmentImage(
                a.fileName,
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, z, _ ->
                            zoom = (zoom * z).coerceIn(1f, 6f)
                            if (zoom == 1f) { offsetX = 0f; offsetY = 0f } else { offsetX += pan.x; offsetY += pan.y }
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = if (zoom > 1f) 1f else 2.5f; offsetX = 0f; offsetY = 0f }) }
                    .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offsetX; translationY = offsetY },
                maxSide = 2048,
                contentScale = ContentScale.Fit,
            )
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, S.close, tint = Color.White) }
                Spacer(Modifier.weight(1f))
                if (onExtract != null) IconButton(onClick = onExtract) { Icon(Icons.Default.DocumentScanner, S.extractText, tint = Color.White) }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, S.share, tint = Color.White) }
                if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, S.delete, tint = Color.White) }
            }
        }
    }
}

/** Shares text and photos with another app (Zalo, Messenger, mail…). */
fun share(context: android.content.Context, text: String, attachments: List<Attachment>) {
    val images = attachments.filter { AttachmentKind.isPicture(it.kind) }.map { Files.file(context, it) }.filter { it.exists() }
    val uris = ArrayList(images.map { FileProvider.getUriForFile(context, context.packageName + ".files", it) })
    val intent = when {
        uris.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        uris.size == 1 -> Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, uris[0])
        else -> Intent(Intent.ACTION_SEND).setType("text/plain")
    }
    if (text.isNotBlank()) intent.putExtra(Intent.EXTRA_TEXT, text)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, S.share).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
