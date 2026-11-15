package com.samuelcruz.ghichu.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.samuelcruz.ghichu.sync.SyncManager.CryptoResult
import com.samuelcruz.ghichu.BuildConfigInfo
import com.samuelcruz.ghichu.Reminders
import com.samuelcruz.ghichu.UiEvent
import com.samuelcruz.ghichu.core.AppLock
import com.samuelcruz.ghichu.core.I18n
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Backup
import com.samuelcruz.ghichu.data.Export
import com.samuelcruz.ghichu.data.KeepImport
import com.samuelcruz.ghichu.sync.GoogleAuth
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Schedule
import com.samuelcruz.ghichu.data.AutoBackup
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun SettingsScreen(onBack: () -> Unit, onTemplates: () -> Unit) {
    val app = app()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val sync by app.sync.state.collectAsState()
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var lockOn by remember { mutableStateOf(app.prefs.lockEnabled) }
    var checkedLast by remember { mutableStateOf(app.prefs.checkedLast) }
    var wifiOnly by remember { mutableStateOf(app.prefs.attachmentsWifiOnly) }
    var e2eDialog by remember { mutableStateOf<E2eMode?>(null) }
    val templates by app.db.notes().observeTemplates().collectAsState(emptyList())
    var dailyTemplate by remember { mutableStateOf(app.prefs.dailyTemplate) }
    var dailyDialog by remember { mutableStateOf(false) }
    var autoDir by remember { mutableStateOf(app.prefs.autoBackupDir) }
    var autoLast by remember { mutableStateOf(app.prefs.autoBackupLast) }
    var autoFailed by remember { mutableStateOf(app.prefs.autoBackupFailed) }
    var autoDialog by remember { mutableStateOf(false) }

    fun toast(msg: String) { app.events.tryEmit(UiEvent(msg)) }

    // Google sign-in: Google's own screen picks the account and asks for the Drive permission.
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) { busy = null; return@rememberLauncherForActivityResult }
        scope.launch {
            runCatching { app.sync.connect(GoogleAuth.tokenFromIntent(context, r.data)) }
                .onSuccess { toast(S.syncConnected) }
                .onFailure { toast(S.syncError(it.message ?: "")) }
            busy = null
        }
    }
    fun connect() {
        busy = S.connecting
        scope.launch {
            try {
                when (val res = GoogleAuth.authorize(context, if (sync.needsSignIn) app.prefs.syncEmail else null)) {
                    is GoogleAuth.Result.Token -> { app.sync.connect(res.value); toast(S.syncConnected); busy = null }
                    is GoogleAuth.Result.NeedsUi -> signIn.launch(IntentSenderRequest.Builder(res.intent.intentSender).build())
                }
            } catch (e: com.google.android.gms.common.api.ApiException) {
                toast(S.syncSetupError(e.statusCode)); busy = null
            } catch (e: Exception) {
                toast(S.syncError(e.message ?: e.javaClass.simpleName)); busy = null
            }
        }
    }

    val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = S.working
        scope.launch {
            runCatching { context.contentResolver.openOutputStream(uri)!!.use { Backup.write(context, app.db, it, app.prefs.deviceId) } }
                .onSuccess { toast(S.backupDone(it)) }.onFailure { toast(S.failed(it.message)) }
            busy = null
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = S.working
        scope.launch {
            runCatching { context.contentResolver.openInputStream(uri)!!.use { Backup.restore(context, app.db, it) } }
                .onSuccess {
                    toast(S.restoreDone(it))
                    Reminders.updateAll(context, app.db.notes().withReminders())
                    app.refreshWidgets(); app.sync.requestSoon()
                }
                .onFailure { toast(S.failed(it.message)) }
            busy = null
        }
    }
    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = S.working
        scope.launch {
            runCatching { context.contentResolver.openOutputStream(uri)!!.use { Export.allToZip(context, app.db, it) } }
                .onSuccess { toast(S.exportDone(it)) }.onFailure { toast(S.failed(it.message)) }
            busy = null
        }
    }
    val importKeep = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = S.importing
        scope.launch {
            runCatching { KeepImport.import(context, app.repo, app.db, uris) }
                .onSuccess { toast(S.keepDone(it.notes)); app.refreshWidgets(); app.sync.requestSoon() }
                .onFailure { toast(S.failed(it.message)) }
            busy = null
        }
    }

    fun backupNow() {
        busy = S.working
        scope.launch {
            runCatching { AutoBackup.run(context) }
                .onSuccess { toast(S.backupDone(it)) }.onFailure { toast(S.failed(it.message)) }
            autoLast = app.prefs.autoBackupLast
            autoFailed = app.prefs.autoBackupFailed
            busy = null
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { AutoBackup.enable(context, uri) }
            .onSuccess {
                autoDir = app.prefs.autoBackupDir
                toast(S.autoBackupEnabled)
                backupNow()
            }
            .onFailure { toast(S.failed(it.message)) }
    }

    SimpleScaffold(S.settings, onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SyncCard(sync, busy = busy == S.connecting, onConnect = ::connect, onSyncNow = { scope.launch { app.sync.syncNow() } }, onDisconnect = { confirmDisconnect = true })
            if (sync.enabled) {
                Group {
                    SwitchRow(Icons.Default.Wifi, S.wifiOnly, S.wifiOnlyText, wifiOnly) {
                        wifiOnly = it; app.prefs.attachmentsWifiOnly = it
                    }
                    val e2eText = when {
                        sync.needsPassphrase -> S.e2eLockedText
                        sync.encrypted -> S.e2eOnText
                        else -> S.e2eOffText
                    } + (if (sync.unreadable > 0) "\n" + S.unreadableFiles(sync.unreadable) else "")
                    ActionRow(Icons.Default.EnhancedEncryption, S.e2e, e2eText) {
                        e2eDialog = when {
                            sync.needsPassphrase -> E2eMode.UNLOCK
                            sync.encrypted -> E2eMode.DISABLE
                            else -> E2eMode.ENABLE
                        }
                    }
                }
            }

            Title(S.appearance)
            Group {
                ChoiceRow(Icons.Default.DarkMode, S.theme, listOf(S.themeSystem, S.themeLight, S.themeDark), app.themeMode) { app.changeTheme(it) }
                ChoiceRow(Icons.Default.FormatSize, S.textSize, listOf("A-", "A", "A+", "A++"), app.fontSize) { app.changeFontSize(it) }
                ChoiceRow(Icons.Default.Language, S.language, listOf("Tiếng Việt", "English"), if (I18n.lang == "en") 1 else 0) {
                    val code = if (it == 1) "en" else "vi"
                    if (code != I18n.lang) {
                        app.prefs.language = code
                        I18n.set(code)
                        app.updateShortcuts()
                        activity?.recreate()
                    }
                }
                SwitchRow(Icons.Default.CheckBox, S.checkedLast, S.checkedLastText, checkedLast) {
                    checkedLast = it; app.prefs.checkedLast = it
                }
            }

            Title(S.security)
            Group {
                val can = AppLock.canLock(context)
                SwitchRow(Icons.Default.Lock, S.appLock, if (can) S.appLockText else S.noScreenLock, lockOn, enabled = can) { on ->
                    activity?.let { a -> AppLock.authenticate(a, onSuccess = { lockOn = on; app.prefs.lockEnabled = on }) }
                }
            }

            Title(S.data)
            Group {
                ActionRow(Icons.Outlined.Description, S.templates, S.templatesText, onTemplates)
                val dailyName = templates.firstOrNull { it.id == dailyTemplate }?.let { it.displayTitle().ifBlank { S.untitled } }
                ActionRow(Icons.Default.Today, S.dailyTemplate, S.dailyTemplateText(dailyName)) { dailyDialog = true }
                ActionRow(Icons.Default.Backup, S.backup, S.backupText) { backup.launch("ghichu-backup-$stamp.zip") }
                val autoText = when {
                    autoDir.isEmpty() -> S.autoBackupOffText
                    else -> S.autoBackupOnText(AutoBackup.folderName(context, autoDir), autoLast.takeIf { it > 0 }?.let { whenLabel(it) }) +
                        (if (autoFailed) "\n" + S.autoBackupFailedText else "")
                }
                ActionRow(Icons.Default.Schedule, S.autoBackup, autoText) {
                    if (autoDir.isEmpty()) pickFolder.launch(null) else autoDialog = true
                }
                ActionRow(Icons.Default.Restore, S.restoreBackup, S.restoreText) { restore.launch(arrayOf("application/zip", "application/octet-stream")) }
                ActionRow(Icons.Default.Upload, S.exportMarkdown, S.exportMarkdownText) { exportMd.launch("ghichu-markdown-$stamp.zip") }
                ActionRow(Icons.Default.Download, S.importKeep, S.importKeepText) { importKeep.launch(arrayOf("application/zip", "application/octet-stream")) }
            }

            Title(S.about)
            Group {
                ActionRow(Icons.Default.Info, S.appName + " " + BuildConfigInfo.versionName(context), S.aboutText) {}
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    busy?.let { msg ->
        if (msg != S.connecting) {
            AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(msg)
                }
            })
        }
    }
    if (dailyDialog) {
        AlertDialog(
            onDismissRequest = { dailyDialog = false },
            title = { Text(S.dailyTemplate) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(S.dailyHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val options = listOf("" to S.dailyNoTemplate) + templates.map { it.id to it.displayTitle().ifBlank { S.untitled } }
                    options.forEach { (id, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                dailyTemplate = id; app.prefs.dailyTemplate = id; dailyDialog = false
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = dailyTemplate == id, onClick = {
                                dailyTemplate = id; app.prefs.dailyTemplate = id; dailyDialog = false
                            })
                            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dailyDialog = false }) { Text(S.close) } },
        )
    }
    if (autoDialog) {
        AlertDialog(
            onDismissRequest = { autoDialog = false },
            title = { Text(S.autoBackup) },
            text = {
                Column {
                    Text(
                        S.autoBackupOnText(AutoBackup.folderName(context, autoDir), autoLast.takeIf { it > 0 }?.let { whenLabel(it) }),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { autoDialog = false; backupNow() }) { Text(S.autoBackupNow) }
                    TextButton(onClick = { autoDialog = false; pickFolder.launch(null) }) { Text(S.changeFolder) }
                    TextButton(onClick = {
                        autoDialog = false
                        AutoBackup.disable(context)
                        autoDir = ""
                        toast(S.autoBackupDisabled)
                    }) { Text(S.turnOff) }
                }
            },
            confirmButton = { TextButton(onClick = { autoDialog = false }) { Text(S.close) } },
        )
    }
    e2eDialog?.let { mode ->
        PassphraseDialog(
            mode,
            onDismiss = { e2eDialog = null },
            onSubmit = { pass, showError ->
                busy = S.working
                scope.launch {
                    val r = when (mode) {
                        E2eMode.ENABLE -> app.sync.enableEncryption(pass)
                        E2eMode.UNLOCK -> app.sync.unlockEncryption(pass)
                        E2eMode.DISABLE -> app.sync.disableEncryption(pass)
                    }
                    busy = null
                    when (r) {
                        CryptoResult.Ok -> {
                            e2eDialog = null
                            toast(when (mode) { E2eMode.ENABLE -> S.e2eEnabled; E2eMode.UNLOCK -> S.e2eUnlocked; E2eMode.DISABLE -> S.e2eDisabled })
                        }
                        CryptoResult.WrongPassphrase -> showError(S.wrongPassphrase)
                        CryptoResult.AlreadyOn -> { e2eDialog = E2eMode.UNLOCK; toast(S.e2eAlreadyOn) }
                        is CryptoResult.Failed -> showError(r.message)
                    }
                }
            },
        )
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(S.disconnectTitle) },
            text = { Text(S.disconnectText) },
            confirmButton = { TextButton(onClick = { confirmDisconnect = false; scope.launch { app.sync.disconnect() } }) { Text(S.disconnect) } },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(S.cancel) } },
        )
    }
}

@Composable
private fun SyncCard(
    sync: com.samuelcruz.ghichu.sync.SyncState,
    busy: Boolean,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BrandBrush).padding(20.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(if (sync.enabled) Icons.Default.CloudDone else Icons.Default.Cloud, null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(S.googleSync, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        if (sync.enabled) sync.email.ifBlank { S.connected } else S.syncPitch,
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (!sync.enabled) {
                Button(
                    onClick = onConnect, enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB45309)),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(S.connectGoogle)
                }
            } else {
                val status = when {
                    sync.running -> S.syncing
                    sync.error != null -> sync.error
                    sync.lastSyncAt > 0 -> S.lastSynced(whenLabel(sync.lastSyncAt))
                    else -> S.notSyncedYet
                }
                Text(status, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                if (sync.attachmentsPending > 0) {
                    Text(S.attachmentsPending(sync.attachmentsPending), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (sync.needsSignIn) {
                        Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB45309))) { Text(S.signInAgain) }
                    } else {
                        Button(
                            onClick = onSyncNow, enabled = !sync.running,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB45309)),
                        ) {
                            Icon(Icons.Default.Sync, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(S.syncNow)
                        }
                    }
                    OutlinedButton(onClick = onDisconnect, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text(S.disconnect) }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) = SectionLabel(text, Modifier.padding(top = 4.dp))

@Composable
private fun Group(content: @Composable () -> Unit) {
    SoftSurface(Modifier.fillMaxWidth()) { Column(Modifier.padding(vertical = 6.dp)) { content() } }
}

@Composable
private fun RowBase(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) = RowBase(icon, title, subtitle, onClick)

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) =
    RowBase(icon, title, subtitle, if (enabled) ({ onChange(!checked) }) else null) {
        Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
    }

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, MaterialTheme.colorScheme.primary, 38.dp)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == selected,
                    onClick = { onPick(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

private enum class E2eMode { ENABLE, UNLOCK, DISABLE }

/**
 * Asks for the encryption passphrase. Switching on asks twice and explains that a forgotten passphrase cannot be
 * recovered; [onSubmit] gets the passphrase and a way to show an error under the field.
 */
@Composable
private fun PassphraseDialog(mode: E2eMode, onDismiss: () -> Unit, onSubmit: (String, (String) -> Unit) -> Unit) {
    var pass by remember(mode) { mutableStateOf("") }
    var again by remember(mode) { mutableStateOf("") }
    var error by remember(mode) { mutableStateOf<String?>(null) }
    val title = when (mode) { E2eMode.ENABLE -> S.e2eEnable; E2eMode.UNLOCK -> S.e2eUnlock; E2eMode.DISABLE -> S.e2eDisable }
    fun submit() {
        error = when {
            mode == E2eMode.ENABLE && pass.length < 8 -> S.passphraseShort
            mode == E2eMode.ENABLE && pass != again -> S.passphraseMismatch
            pass.isEmpty() -> S.passphraseShort
            else -> null
        }
        if (error == null) onSubmit(pass) { error = it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (mode) {
                    E2eMode.ENABLE -> Text(S.e2eWarning, style = MaterialTheme.typography.bodyMedium)
                    E2eMode.UNLOCK -> Text(S.e2eLockedText, style = MaterialTheme.typography.bodyMedium)
                    E2eMode.DISABLE -> Text(S.e2eDisableText, style = MaterialTheme.typography.bodyMedium)
                }
                val keyboard = KeyboardOptions(keyboardType = KeyboardType.Password)
                OutlinedTextField(
                    pass, { pass = it; error = null }, label = { Text(S.passphrase) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = keyboard,
                    isError = error != null, modifier = Modifier.fillMaxWidth(),
                )
                if (mode == E2eMode.ENABLE) {
                    OutlinedTextField(
                        again, { again = it; error = null }, label = { Text(S.passphraseRepeat) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = keyboard,
                        isError = error != null, modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = ::submit) { Text(S.ok) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
    )
}
