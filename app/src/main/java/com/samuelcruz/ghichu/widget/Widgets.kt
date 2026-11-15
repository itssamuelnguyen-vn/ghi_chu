package com.samuelcruz.ghichu.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn as GlanceLazyColumn
import androidx.glance.appwidget.lazy.items as glanceItems
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.samuelcruz.ghichu.GhiChuApp
import com.samuelcruz.ghichu.MainActivity
import com.samuelcruz.ghichu.R
import com.samuelcruz.ghichu.core.Checklist
import com.samuelcruz.ghichu.core.I18n
import com.samuelcruz.ghichu.core.Markdown
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Note
import com.samuelcruz.ghichu.data.NoteKind
import com.samuelcruz.ghichu.ui.GhiChuTheme
import com.samuelcruz.ghichu.ui.NoteColors
import androidx.glance.text.Text as GlanceText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.currentState
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

object Widgets {
    suspend fun updateAll(context: Context) {
        NoteWidget().updateAll(context)
        QuickWidget().updateAll(context)
        NoteListWidget().updateAll(context)
    }

    fun openIntent(context: Context, action: String, noteId: String? = null): Intent =
        Intent(context, MainActivity::class.java).setAction(action)
            .apply { if (noteId != null) { putExtra(MainActivity.EXTRA_NOTE, noteId); data = Uri.parse("ghichu://note/$noteId") } }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Opens the "pick a note" screen for one note widget. */
    fun configIntent(context: Context, widgetId: Int): Intent =
        Intent(context, NoteWidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .setData(Uri.parse("ghichu://widget/$widgetId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun isDark(context: Context) =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

private val NoteIdKey = ActionParameters.Key<String>("note")
private val LineKey = ActionParameters.Key<Int>("line")

/**
 * The note a widget shows, kept in the widget's own Glance state. Writing it there (not only in app prefs) makes a
 * running widget redraw at once: Glance re-runs only the code inside `provideContent`, so anything read before it
 * stays stale until the widget's session ends.
 */
internal val WidgetNoteKey = stringPreferencesKey("note_id")

/** Value of a Room query once it has loaded (null before that, so nothing wrong flashes on screen). */
private class Loaded<T>(val value: T)

/** A chosen note on the home screen; checklist items can be ticked right there. */
class NoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as GhiChuApp
        I18n.set(app.prefs.language)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val dark = Widgets.isDark(context)
        val fg = if (dark) Color(0xFFEDE7DF) else Color(0xFF1F1B16)
        val dim = fg.copy(alpha = 0.45f)
        provideContent {
            // Read inside the content (live Room query), so a new choice, edits and ticks show right away.
            val noteId = currentState(WidgetNoteKey) ?: app.prefs.widgetNote(widgetId)
            val loaded by remember(noteId) {
                if (noteId == null) flowOf(Loaded<Note?>(null))
                else app.db.notes().observe(noteId).map { n -> Loaded(n?.takeIf { !it.deleted }) }
            }.collectAsState(null)
            val note = loaded?.value
            val bg = NoteColors.of(note?.color ?: 0, dark, if (dark) Color(0xFF1D1B19) else Color.White)
            Column(
                GlanceModifier.fillMaxSize().background(ColorProvider(bg)).cornerRadius(22.dp).padding(14.dp),
            ) {
                if (loaded == null) return@Column
                if (note == null) {
                    // Tap to pick a note for this very widget (works on every launcher, also when it was never set).
                    GlanceText(
                        if (noteId == null) S.widgetNotChosen else S.widgetNoNote,
                        style = TextStyle(color = ColorProvider(dim), fontSize = 14.sp),
                        modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(Widgets.configIntent(context, widgetId))),
                    )
                    return@Column
                }
                val open = actionStartActivity(Widgets.openIntent(context, MainActivity.ACTION_OPEN, note.id))
                GlanceText(
                    note.displayTitle().ifBlank { S.untitled },
                    maxLines = 2,
                    style = TextStyle(color = ColorProvider(fg), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.fillMaxWidth().clickable(open),
                )
                Spacer(GlanceModifier.height(6.dp))
                when {
                    note.locked -> GlanceText(S.lockedNote, style = TextStyle(color = ColorProvider(dim), fontSize = 14.sp), modifier = GlanceModifier.clickable(open))
                    note.kind == NoteKind.CHECKLIST -> {
                        val items = Checklist.parse(note.body)
                        val order = Checklist.displayOrder(items, app.prefs.checkedLast).filter { items[it].text.isNotBlank() }
                        GlanceLazyColumn(GlanceModifier.fillMaxSize()) {
                            glanceItems(order, itemId = { it.toLong() }) { i ->
                                val item = items[i]
                                Row(
                                    GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                                        .clickable(actionRunCallback<ToggleItemAction>(actionParametersOf(NoteIdKey to note.id, LineKey to i))),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Image(
                                        ImageProvider(if (item.checked) R.drawable.ic_box_checked else R.drawable.ic_box),
                                        null, GlanceModifier.size(20.dp),
                                        colorFilter = ColorFilter.tint(ColorProvider(if (item.checked) dim else fg)),
                                    )
                                    Spacer(GlanceModifier.width(8.dp))
                                    GlanceText(
                                        item.text, maxLines = 2,
                                        style = TextStyle(
                                            color = ColorProvider(if (item.checked) dim else fg), fontSize = 14.sp,
                                            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    else -> GlanceLazyColumn(GlanceModifier.fillMaxSize()) {
                        item {
                            GlanceText(
                                Markdown.plain(note.body),
                                style = TextStyle(color = ColorProvider(fg), fontSize = 14.sp),
                                modifier = GlanceModifier.clickable(open),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ticks / unticks a checklist item from the widget. */
class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as GhiChuApp
        val id = parameters[NoteIdKey] ?: return
        val line = parameters[LineKey] ?: return
        app.repo.update(id) { n ->
            val items = Checklist.parse(n.body).toMutableList()
            if (line !in items.indices) return@update n
            items[line] = items[line].copy(checked = !items[line].checked)
            n.copy(body = Checklist.format(items))
        }
        NoteWidget().update(context, glanceId)
    }
}

class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteWidget()
}

/** A bar for quick capture: search, new note, checklist, voice, photo. */
class QuickWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as GhiChuApp
        I18n.set(app.prefs.language)
        val dark = Widgets.isDark(context)
        val bg = if (dark) Color(0xFF262320) else Color(0xFFFFFDF9)
        val fg = if (dark) Color(0xFFEDE7DF) else Color(0xFF1F1B16)
        val accent = if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
        provideContent {
            Row(
                GlanceModifier.fillMaxSize().background(ColorProvider(bg)).cornerRadius(28.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    GlanceModifier.defaultWeight().clickable(actionStartActivity(Widgets.openIntent(context, MainActivity.ACTION_SEARCH))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(ImageProvider(R.drawable.ic_search), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(ColorProvider(fg.copy(alpha = 0.6f))))
                    Spacer(GlanceModifier.width(10.dp))
                    GlanceText(S.widgetSearch, maxLines = 1, style = TextStyle(color = ColorProvider(fg.copy(alpha = 0.6f)), fontSize = 15.sp))
                }
                listOf(
                    R.drawable.ic_sc_list to MainActivity.ACTION_NEW_LIST,
                    R.drawable.ic_sc_mic to MainActivity.ACTION_NEW_VOICE,
                    R.drawable.ic_sc_photo to MainActivity.ACTION_NEW_PHOTO,
                    R.drawable.ic_sc_note to MainActivity.ACTION_NEW_TEXT,
                ).forEach { (icon, action) ->
                    Box(
                        GlanceModifier.size(44.dp).clickable(actionStartActivity(Widgets.openIntent(context, action))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(ImageProvider(icon), null, GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(ColorProvider(accent)))
                    }
                }
            }
        }
    }
}

class QuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickWidget()
}

/** Shown when a note widget is added: pick which note it shows. */
class NoteWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val app = application as GhiChuApp
        setContent {
            GhiChuTheme(app.themeMode, app.fontSize) {
                val notes by app.db.notes().observeAll().collectAsState(emptyList())
                val shown = notes.filter { !it.inTrash }.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.statusBarsPadding()) {
                        Text(S.widgetPick, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
                        LazyColumn(contentPadding = PaddingValues(16.dp)) {
                            items(shown, key = { it.id }) { n ->
                                Surface(
                                    color = NoteColors.background(n.color),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).clickable {
                                        app.prefs.setWidgetNote(widgetId, n.id)
                                        app.scope.launchUpdate(this@NoteWidgetConfigActivity, widgetId, n.id)
                                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                        finish()
                                    },
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(n.displayTitle().ifBlank { S.untitled }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (!n.locked && n.title.isNotBlank()) {
                                            Text(
                                                Markdown.plain(n.body), style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun CoroutineScope.launchUpdate(context: Context, widgetId: Int, noteId: String) {
    val appContext = context.applicationContext
    launch {
        runCatching {
            val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(widgetId)
            updateAppWidgetState(appContext, glanceId) { it[WidgetNoteKey] = noteId }
            NoteWidget().update(appContext, glanceId)
        }.onFailure {
            // Some launchers hand over the widget late: redraw them all (the choice is also in app prefs).
            runCatching { NoteWidget().updateAll(appContext) }
        }
    }
}
