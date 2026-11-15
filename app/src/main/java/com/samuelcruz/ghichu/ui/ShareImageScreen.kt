package com.samuelcruz.ghichu.ui

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.samuelcruz.ghichu.UiEvent
import com.samuelcruz.ghichu.core.Checklist
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.AttachmentKind
import com.samuelcruz.ghichu.data.NoteKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Chia sẻ dạng ảnh": the note drawn as a tidy card (its colour, pictures, text or checklist, labels), turned into
 * a PNG and handed to the share menu (Zalo, Messenger, save to gallery…).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareImageScreen(id: String, onBack: () -> Unit) {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val note by app.db.notes().observe(id).collectAsState(null)
    val atts by app.db.attachments().observeFor(id).collectAsState(emptyList())
    val labels by app.db.labels().observeAll().collectAsState(emptyList())
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    fun shareNow() {
        if (busy) return
        busy = true
        scope.launch {
            runCatching {
                val bmp = layer.toImageBitmap().asAndroidBitmap()
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                    File(dir, "ghichu-${System.currentTimeMillis()}.png").also { f ->
                        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent, S.share).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { app.events.emit(UiEvent(S.failed(it.message))) }
            busy = false
        }
    }

    SimpleScaffold(
        S.shareAsImage, onBack,
        fab = {
            ExtendedFloatingActionButton(
                onClick = ::shareNow,
                icon = { Icon(Icons.Default.Share, null) },
                text = { Text(S.share) },
                containerColor = cs.primary, contentColor = cs.onPrimary,
            )
        },
    ) { pad ->
        val n = note ?: return@SimpleScaffold
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Everything inside this box ends up in the picture.
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .background(cs.background)
                    .padding(14.dp),
            ) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(NoteColors.background(n.color))) {
                    val pictures = atts.filter { AttachmentKind.isPicture(it.kind) }
                    if (pictures.isNotEmpty() && !n.locked) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            pictures.take(4).forEach { a ->
                                AttachmentImage(a.fileName, Modifier.fillMaxWidth().aspectRatio(1.4f), maxSide = 1200)
                            }
                        }
                    }
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (n.title.isNotBlank()) Text(n.title, style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
                        val style = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface)
                        when {
                            n.locked -> Text(S.lockedNote, style = style)
                            n.kind == NoteKind.CHECKLIST -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val items = Checklist.parse(n.body).filter { it.text.isNotBlank() }
                                val order = Checklist.displayOrder(items, app.prefs.checkedLast)
                                val c = mdColors()
                                order.forEach { i ->
                                    val it = items[i]
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            if (it.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null,
                                            Modifier.padding(top = 2.dp).size(20.dp), tint = if (it.checked) c.dim else c.accent,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            inlineText(it.text, c, links = false),
                                            style = style.copy(
                                                color = if (it.checked) c.dim else style.color,
                                                textDecoration = if (it.checked) TextDecoration.LineThrough else null,
                                            ),
                                        )
                                    }
                                }
                            }
                            n.body.isNotBlank() -> MarkdownView(n.body, style = style, links = false)
                        }
                        val names = n.labelIds.mapNotNull { lid -> labels.firstOrNull { it.id == lid }?.name }
                        if (names.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                names.forEach { MiniChip(it) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(BrandBrush), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lightbulb, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        S.appName + " · " + fullDateTime(n.updatedAt),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}
