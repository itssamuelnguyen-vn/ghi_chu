package com.samuelcruz.ghichu.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuelcruz.ghichu.core.Checklist
import com.samuelcruz.ghichu.core.Repeat
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Attachment
import com.samuelcruz.ghichu.data.AttachmentKind
import com.samuelcruz.ghichu.data.Files
import com.samuelcruz.ghichu.data.Note
import com.samuelcruz.ghichu.data.NoteKind

// Shared element "container transform": a card grows into the editor and shrinks back.

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.noteBounds(id: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    return with(shared) {
        this@noteBounds.sharedBounds(
            rememberSharedContentState("note-$id"),
            nav,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

@Composable
fun ProvideNavScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalNavScope provides scope, content = content)

/** A note with what its card needs from other tables. */
data class NoteItem(
    val note: Note,
    val labelNames: List<String>,
    val images: List<Attachment>,
    val audioCount: Int,
)

/** A photo file loaded off the main thread (null while loading or if missing). */
@Composable
fun rememberImage(name: String, maxSide: Int): ImageBitmap? {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, name, maxSide) {
        value = Files.loadImage(context, name, maxSide)?.asImageBitmap()
    }
    return bmp
}

@Composable
fun AttachmentImage(name: String, modifier: Modifier = Modifier, maxSide: Int = 720, contentScale: ContentScale = ContentScale.Crop) {
    val img = rememberImage(name, maxSide)
    Box(modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = contentScale)
        else Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
    }
}

/** A note in the grid / list: photo, title, text or checklist preview, reminder and labels. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NoteCard(
    item: NoteItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: List<String> = emptyList(),
    checkedLast: Boolean = true,
    previewLines: Int = 10,
) {
    val n = item.note
    val bg = NoteColors.background(n.color)
    val cs = MaterialTheme.colorScheme
    val scale = LocalNoteScale.current
    val shape = RoundedCornerShape(20.dp)
    val border = when {
        selected -> Modifier.border(2.5.dp, cs.primary, shape)
        n.color == 0 -> Modifier.border(1.dp, cs.outlineVariant, shape)
        else -> Modifier
    }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .noteBounds(n.id)
                .clip(shape)
                .background(bg)
                .then(border)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            if (item.images.isNotEmpty() && !n.locked) {
                val shownImages = item.images.take(3)
                Row(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                    shownImages.forEach { a ->
                        AttachmentImage(
                            a.fileName,
                            Modifier.weight(1f).aspectRatio(if (shownImages.size == 1) 1.3f else 0.9f),
                            maxSide = 480,
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (n.title.isNotBlank()) {
                    val hl = mdColors().highlight
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append(n.title)
                            for (r in com.samuelcruz.ghichu.core.matchRanges(n.title, highlight)) {
                                addStyle(androidx.compose.ui.text.SpanStyle(background = hl), r.first, r.last + 1)
                            }
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * scale),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
                val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * scale,
                    color = cs.onSurface.copy(alpha = 0.86f),
                )
                when {
                    n.locked -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(S.lockedNote, style = bodyStyle.copy(color = cs.onSurfaceVariant))
                    }
                    n.kind == NoteKind.CHECKLIST -> ChecklistPreview(n, bodyStyle, checkedLast, highlight)
                    n.body.isNotBlank() -> MarkdownView(n.body, style = bodyStyle, maxLines = previewLines, links = false, highlight = highlight)
                }
                val hasFooter = n.reminderAt != null || item.labelNames.isNotEmpty() || item.audioCount > 0
                if (hasFooter) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        n.reminderAt?.let { at ->
                            val overdue = n.reminderRepeat == Repeat.NONE && at < System.currentTimeMillis()
                            MiniChip(
                                reminderLabel(at, n.reminderRepeat),
                                if (n.reminderRepeat != Repeat.NONE) Icons.Default.Repeat else Icons.Default.Alarm,
                                strike = overdue,
                            )
                        }
                        if (item.audioCount > 0) MiniChip(if (item.audioCount > 1) "${item.audioCount}" else S.recording, Icons.Default.Mic)
                        item.labelNames.take(3).forEach { MiniChip(it) }
                        if (item.labelNames.size > 3) MiniChip("+${item.labelNames.size - 3}")
                    }
                }
            }
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Check, null, tint = cs.onPrimary, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun ChecklistPreview(n: Note, style: androidx.compose.ui.text.TextStyle, checkedLast: Boolean, highlight: List<String>) {
    val items = Checklist.parse(n.body).filter { it.text.isNotBlank() }
    val order = Checklist.displayOrder(items, checkedLast)
    val open = order.filter { !items[it].checked }
    val done = items.count { it.checked }
    val shown = (if (checkedLast) open else order).take(8)
    val c = mdColors()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        shown.forEach { i ->
            val it = items[i]
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (it.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null,
                    Modifier.padding(top = 1.dp).size(16.dp),
                    tint = if (it.checked) c.dim else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    inlineText(it.text, c, links = false, highlight = highlight),
                    style = style.copy(
                        textDecoration = if (it.checked) TextDecoration.LineThrough else null,
                        color = if (it.checked) c.dim else style.color,
                    ),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val hiddenOpen = (if (checkedLast) open.size else order.size) - shown.size
        if (hiddenOpen > 0) Text("+ $hiddenOpen", style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        if (checkedLast && done > 0) {
            Text(S.doneItems(done), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MiniChip(text: String, icon: ImageVector? = null, strike: Boolean = false, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(cs.onSurface.copy(alpha = 0.07f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
        )
    }
}

/** A round icon on a soft tinted circle (empty states, settings rows). */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f)) }
}

/** Big friendly message when a list is empty. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Section header in lists ("ĐÃ GHIM", "KHÁC"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
    )
}

/** Colour swatches; [selected] gets a tick. */
@Composable
fun ColorRow(selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalIsDark.current
    val cs = MaterialTheme.colorScheme
    androidx.compose.foundation.lazy.LazyRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(NoteColors.count) { i ->
            val color = NoteColors.of(i, dark, cs.surfaceContainer)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (i == selected) 2.5.dp else 1.dp, if (i == selected) cs.primary else cs.outline, CircleShape)
                    .clickable { onPick(i) },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    i == selected -> Icon(Icons.Default.Check, NoteColors.name(i), tint = cs.primary)
                    i == 0 -> Icon(Icons.Default.FormatColorReset, NoteColors.name(i), tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Opaque surface used for bottom sheets' rows etc. */
@Composable
fun SoftSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, content = content)

/** A photo or a drawing (shown as a picture). */
fun isImage(a: Attachment) = AttachmentKind.isPicture(a.kind)
