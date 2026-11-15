package com.samuelcruz.ghichu.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.samuelcruz.ghichu.UiEvent
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Attachment
import com.samuelcruz.ghichu.data.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** What the finger draws with. */
enum class DrawTool { PEN, MARKER, ERASER }

/**
 * One line drawn, in picture pixels. [clear] = the "clear page" step (paints everything white), kept as a step so
 * it can be undone like a stroke.
 */
class InkStroke(val points: List<Offset>, val color: Color, val width: Float, val alpha: Float = 1f, val clear: Boolean = false)

private val Paper = Color.White
private val InkColors = listOf(
    Color(0xFF1F1B16), Color(0xFF6B7280), Color(0xFFDC2626), Color(0xFFEA580C),
    Color(0xFFEAB308), Color(0xFF16A34A), Color(0xFF2563EB), Color(0xFF9333EA),
)
private val InkWidths = listOf(2.5f, 5f, 10f)

/**
 * Drawing / handwriting on a white page. A new drawing is as big as the screen; an existing one is opened as the
 * background and new strokes go on top (the eraser paints white). Back saves it as a PNG attachment of the note.
 */
@Composable
fun DrawScreen(noteId: String, attachmentId: String?, onDone: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var original by remember { mutableStateOf<Attachment?>(null) }
    var base by remember { mutableStateOf<Bitmap?>(null) }
    var loaded by remember { mutableStateOf(attachmentId == null) }
    /** Size of the picture in pixels (the base image's, or the canvas's for a new drawing). */
    var pageSize by remember { mutableStateOf<IntSize?>(null) }

    val strokes = remember { mutableStateListOf<InkStroke>() }
    val redo = remember { mutableStateListOf<InkStroke>() }
    var current by remember { mutableStateOf<List<Offset>?>(null) }
    var tool by remember { mutableStateOf(DrawTool.PEN) }
    var color by remember { mutableStateOf(InkColors[0]) }
    var widthIndex by remember { mutableStateOf(1) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(attachmentId) {
        if (attachmentId == null) return@LaunchedEffect
        val a = app.db.attachments().get(attachmentId)
        original = a
        if (a != null) {
            base = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(Files.file(app, a).path, BitmapFactory.Options().apply { inMutable = false })
            }
            base?.let { pageSize = IntSize(it.width, it.height) }
        }
        loaded = true
    }
    val baseImage: ImageBitmap? = remember(base) { base?.asImageBitmap() }

    fun save(then: () -> Unit) {
        val size = pageSize
        if (saving) return
        if (strokes.isEmpty() || size == null) { then(); return }
        saving = true
        val snapshot = strokes.toList()
        val bg = base
        val replaces = original
        // Saved before leaving, so the note is never seen without its drawing (e.g. dropped as empty).
        app.scope.launch {
            withContext(NonCancellable) {
                runCatching {
                    val png = withContext(Dispatchers.Default) { renderPng(size, bg, snapshot) }
                    app.repo.saveDrawing(noteId, png, replaces)
                }.onFailure { app.events.emit(UiEvent(S.failed(it.message))) }
            }
            withContext(Dispatchers.Main) { then() }
        }
    }

    BackHandler { save(onDone) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        if (saving) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding())
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { save(onDone) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) }
            Text(S.drawing, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { strokes.removeLastOrNull()?.let { redo += it } }, enabled = strokes.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Undo, S.undo)
            }
            IconButton(onClick = { redo.removeLastOrNull()?.let { strokes += it } }, enabled = redo.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Redo, S.redo)
            }
            IconButton(onClick = {
                strokes += InkStroke(emptyList(), Paper, 0f, clear = true); redo.clear()
            }) { Icon(Icons.Default.DeleteSweep, S.clearPage) }
            if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, S.deleteDrawing) }
            else IconButton(onClick = { strokes.clear(); onDone() }) { Icon(Icons.Default.Close, S.discard) }
        }

        // The page.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            val boxW = constraints.maxWidth
            val boxH = constraints.maxHeight
            LaunchedEffect(loaded, boxW, boxH) {
                if (loaded && pageSize == null && boxW > 0 && boxH > 0) pageSize = IntSize(boxW, boxH)
            }
            val page = pageSize ?: return@BoxWithConstraints
            val scale = minOf(boxW.toFloat() / page.width, boxH.toFloat() / page.height)
            val shownW = page.width * scale
            val shownH = page.height * scale
            val strokeWidth = with(density) { InkWidths[widthIndex].dp.toPx() } / scale
            Canvas(
                Modifier
                    .size(with(density) { shownW.toDp() }, with(density) { shownH.toDp() })
                    .clip(MaterialTheme.shapes.small)
                    .background(Paper)
                    .pointerInput(tool, color, widthIndex, scale) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val points = mutableListOf(down.position / scale)
                            current = points.toList()
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                points += change.position / scale
                                current = points.toList()
                                change.consume()
                            }
                            val (c, w, alpha) = when (tool) {
                                DrawTool.PEN -> Triple(color, strokeWidth, 1f)
                                DrawTool.MARKER -> Triple(color, strokeWidth * 3.5f, 0.35f)
                                DrawTool.ERASER -> Triple(Paper, strokeWidth * 5f, 1f)
                            }
                            strokes += InkStroke(points.toList(), c, w, alpha)
                            redo.clear()
                            current = null
                        }
                    },
            ) {
                withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                    drawRect(Paper, size = Size(page.width.toFloat(), page.height.toFloat()))
                    baseImage?.let { drawImage(it, dstSize = IntSize(page.width, page.height), dstOffset = IntOffset.Zero) }
                    for (s in strokes) drawInk(s, page)
                    current?.let { pts ->
                        val (c, w, alpha) = when (tool) {
                            DrawTool.PEN -> Triple(color, strokeWidth, 1f)
                            DrawTool.MARKER -> Triple(color, strokeWidth * 3.5f, 0.35f)
                            DrawTool.ERASER -> Triple(Paper, strokeWidth * 5f, 1f)
                        }
                        drawInk(InkStroke(pts, c, w, alpha), page)
                    }
                }
            }
        }

        // Tools.
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolButton(Icons.Default.Create, S.pen, tool == DrawTool.PEN) { tool = DrawTool.PEN }
                ToolButton(Icons.Default.Highlight, S.marker, tool == DrawTool.MARKER) { tool = DrawTool.MARKER }
                ToolButton(Icons.Default.AutoFixNormal, S.eraser, tool == DrawTool.ERASER) { tool = DrawTool.ERASER }
                Spacer(Modifier.weight(1f))
                InkWidths.forEachIndexed { i, w ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { widthIndex = i }
                            .background(if (i == widthIndex) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size((w * 1.6f + 3).dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Spacer(Modifier.width(2.dp))
                InkColors.forEach { c ->
                    val selected = c == color && tool != DrawTool.ERASER
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(c)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { color = c; if (tool == DrawTool.ERASER) tool = DrawTool.PEN },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(S.deleteDrawing + "?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    original?.let { a -> scope.launch { app.repo.removeAttachment(a); app.events.emit(UiEvent(S.attachmentRemoved)) } }
                    strokes.clear()
                    onDone()
                }) { Text(S.delete) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(S.cancel) } },
        )
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
    ) { Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** A smooth line through the points (quadratic curves between midpoints). */
private fun inkPath(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    if (points.size == 2) { lineTo(points[1].x, points[1].y); return@apply }
    for (i in 1 until points.size - 1) {
        val mid = (points[i] + points[i + 1]) / 2f
        quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
    }
    lineTo(points.last().x, points.last().y)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInk(s: InkStroke, page: IntSize) {
    if (s.clear) { drawRect(Paper, size = Size(page.width.toFloat(), page.height.toFloat())); return }
    if (s.points.isEmpty()) return
    if (s.points.size == 1) {
        drawCircle(s.color, radius = s.width / 2, center = s.points[0], alpha = s.alpha)
        return
    }
    drawPath(inkPath(s.points), s.color, alpha = s.alpha, style = Stroke(s.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Draws the page into a PNG (Android canvas, same smoothing as on screen). */
private fun renderPng(size: IntSize, base: Bitmap?, strokes: List<InkStroke>): ByteArray {
    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    if (base != null) canvas.drawBitmap(base, null, android.graphics.Rect(0, 0, size.width, size.height), null)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    for (s in strokes) {
        if (s.clear) { canvas.drawColor(android.graphics.Color.WHITE); continue }
        if (s.points.isEmpty()) continue
        paint.color = s.color.copy(alpha = s.alpha).toArgb()
        paint.strokeWidth = s.width
        if (s.points.size == 1) {
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawCircle(s.points[0].x, s.points[0].y, s.width / 2, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            continue
        }
        val p = android.graphics.Path()
        val pts = s.points
        p.moveTo(pts[0].x, pts[0].y)
        if (pts.size == 2) p.lineTo(pts[1].x, pts[1].y) else {
            for (i in 1 until pts.size - 1) {
                val mx = (pts[i].x + pts[i + 1].x) / 2f
                val my = (pts[i].y + pts[i + 1].y) / 2f
                p.quadTo(pts[i].x, pts[i].y, mx, my)
            }
            p.lineTo(pts.last().x, pts.last().y)
        }
        canvas.drawPath(p, paint)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    bmp.recycle()
    return out.toByteArray()
}
