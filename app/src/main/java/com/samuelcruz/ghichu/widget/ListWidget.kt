package com.samuelcruz.ghichu.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment as UiAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn as GlanceLazyColumn
import androidx.glance.appwidget.lazy.items as glanceItems
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column as GlanceColumn
import androidx.glance.layout.Row as GlanceRow
import androidx.glance.layout.Spacer as GlanceSpacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text as GlanceText
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.samuelcruz.ghichu.GhiChuApp
import com.samuelcruz.ghichu.MainActivity
import com.samuelcruz.ghichu.R
import com.samuelcruz.ghichu.core.Checklist
import com.samuelcruz.ghichu.core.I18n
import com.samuelcruz.ghichu.core.Markdown
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Label
import com.samuelcruz.ghichu.data.Note
import com.samuelcruz.ghichu.data.NoteKind
import com.samuelcruz.ghichu.ui.GhiChuTheme
import com.samuelcruz.ghichu.ui.NoteColors
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.currentState
import androidx.glance.appwidget.state.updateAppWidgetState

/** Which notes a list widget shows (stored per widget in prefs as "pinned", "all" or "label:<id>"). */
object ListSource {
    const val PINNED = "pinned"
    const val ALL = "all"
    fun label(id: String) = "label:$id"

    fun notes(source: String, all: List<Note>): List<Note> {
        val live = all.filter { !it.inTrash && !it.archived }
        val chosen = when {
            source == PINNED -> live.filter { it.pinned }
            source.startsWith("label:") -> live.filter { source.removePrefix("label:") in it.labelIds }
            else -> live
        }
        return chosen.sortedByDescending { it.updatedAt }.take(40)
    }

    fun title(source: String, labels: List<Label>): String = when {
        source == PINNED -> S.widgetPinned
        source.startsWith("label:") -> labels.firstOrNull { it.id == source.removePrefix("label:") }?.name ?: S.widgetRecent
        else -> S.widgetRecent
    }
}

/** What a list widget shows, in its Glance state (see `WidgetNoteKey` for why). */
internal val WidgetListKey = stringPreferencesKey("list_source")

/** A list of notes on the home screen (pinned ones, a label, or the most recent); tap one to open it. */
class NoteListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as GhiChuApp
        I18n.set(app.prefs.language)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val dark = Widgets.isDark(context)
        val bg = if (dark) Color(0xFF1D1B19) else Color(0xFFFFFDF9)
        val card = if (dark) Color(0xFF2A2724) else Color(0xFFF3EEE7)
        val fg = if (dark) Color(0xFFEDE7DF) else Color(0xFF1F1B16)
        val dim = fg.copy(alpha = 0.55f)
        val accent = if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
        provideContent {
            // Read inside the content (live Room queries), so a new choice and note edits show right away.
            val source = currentState(WidgetListKey) ?: app.prefs.widgetList(widgetId)
            val all by remember { app.db.notes().observeAll() }.collectAsState(null)
            val labels by remember { app.db.labels().observeAll() }.collectAsState(emptyList())
            val notes = ListSource.notes(source, all.orEmpty())
            val title = ListSource.title(source, labels)
            GlanceColumn(GlanceModifier.fillMaxSize().background(ColorProvider(bg)).cornerRadius(22.dp).padding(12.dp)) {
                GlanceRow(GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlanceText(
                        title, maxLines = 1,
                        style = TextStyle(color = ColorProvider(accent), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(Widgets.openIntent(context, Intent.ACTION_MAIN))),
                    )
                    Box(
                        GlanceModifier.size(32.dp).clickable(actionStartActivity(Widgets.openIntent(context, MainActivity.ACTION_NEW_TEXT))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(ImageProvider(R.drawable.ic_add), S.newNote, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(ColorProvider(accent)))
                    }
                }
                if (all == null) {
                    // Still loading.
                } else if (notes.isEmpty()) {
                    GlanceText(S.widgetListEmpty, style = TextStyle(color = ColorProvider(dim), fontSize = 14.sp), modifier = GlanceModifier.padding(4.dp))
                } else {
                    GlanceLazyColumn(GlanceModifier.fillMaxSize()) {
                        glanceItems(notes, itemId = { it.id.hashCode().toLong() }) { n ->
                            GlanceColumn(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                GlanceColumn(
                                    GlanceModifier.fillMaxWidth().cornerRadius(14.dp)
                                        .background(ColorProvider(NoteColors.of(n.color, dark, card)))
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                        .clickable(actionStartActivity(Widgets.openIntent(context, MainActivity.ACTION_OPEN, n.id))),
                                ) {
                                    val head = n.displayTitle().ifBlank { S.untitled }
                                    GlanceText(head, maxLines = 1, style = TextStyle(color = ColorProvider(fg), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                    val preview = preview(n)
                                    if (preview.isNotBlank() && preview != head) {
                                        GlanceSpacer(GlanceModifier.height(2.dp))
                                        GlanceText(preview, maxLines = 2, style = TextStyle(color = ColorProvider(dim), fontSize = 13.sp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun preview(n: Note): String = when {
        n.locked -> S.lockedNote
        n.title.isBlank() -> ""
        n.kind == NoteKind.CHECKLIST -> Checklist.parse(n.body).filter { !it.checked && it.text.isNotBlank() }.joinToString(" · ") { it.text }
        else -> Markdown.plain(n.body).replace('\n', ' ')
    }
}

class NoteListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteListWidget()
}

/** Shown when a list widget is added: pick what it lists. */
class NoteListWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val app = application as GhiChuApp
        fun pick(source: String) {
            app.prefs.setWidgetList(widgetId, source)
            val ctx = applicationContext
            app.scope.launch {
                runCatching {
                    val glanceId = GlanceAppWidgetManager(ctx).getGlanceIdBy(widgetId)
                    updateAppWidgetState(ctx, glanceId) { it[WidgetListKey] = source }
                    NoteListWidget().update(ctx, glanceId)
                }.onFailure { runCatching { NoteListWidget().updateAll(ctx) } }
            }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
        setContent {
            GhiChuTheme(app.themeMode, app.fontSize) {
                val labels by app.db.labels().observeAll().collectAsState(emptyList())
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.statusBarsPadding()) {
                        Text(S.widgetListPick, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
                        val options = listOf(
                            Triple(ListSource.PINNED, Icons.Default.PushPin, S.widgetPinned),
                            Triple(ListSource.ALL, Icons.Default.Schedule, S.widgetRecent),
                        ) + labels.map { Triple(ListSource.label(it.id), Icons.AutoMirrored.Outlined.Label, S.widgetLabel(it.name)) }
                        LazyColumn(contentPadding = PaddingValues(16.dp)) {
                            items(options, key = { it.first }) { (source, icon, label) -> Option(icon, label) { pick(source) } }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Option(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = UiAlignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
