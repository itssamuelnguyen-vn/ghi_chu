package com.samuelcruz.ghichu.ui

import android.app.Activity
import android.graphics.Bitmap
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.samuelcruz.ghichu.GhiChuApp
import com.samuelcruz.ghichu.core.Checklist
import com.samuelcruz.ghichu.core.Html
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.AttachmentKind
import com.samuelcruz.ghichu.data.Files
import com.samuelcruz.ghichu.data.NoteKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * PDF export through Android's print screen: the notes become one HTML page (see [Html]), a hidden WebView lays
 * it out in pages and the system print dialog offers "Save as PDF" (or a real printer). No PDF library needed,
 * and text stays selectable in the PDF.
 */
object PdfExport {
    /** The WebView must stay alive while the print dialog is open. */
    private var webView: WebView? = null

    /** Builds the HTML for [ids] (in that order). Locked notes show only their title unless they are unlocked. */
    suspend fun html(app: GhiChuApp, ids: List<String>, unlocked: Boolean): Pair<String, String> = withContext(Dispatchers.IO) {
        val notes = ids.mapNotNull { app.db.notes().get(it) }.filter { !it.deleted }
        val pages = notes.map { n ->
            val hidden = n.locked && !unlocked
            val pictures = if (hidden) emptyList() else app.db.attachments().forNote(n.id)
                .filter { AttachmentKind.isPicture(it.kind) }
                .mapNotNull { a -> Files.loadImage(app, a.fileName, 1400)?.let { dataUri(it) } }
            val color = if (n.color == 0) null else hex(NoteColors.of(n.color, false, Color.White))
            Html.Page(
                title = n.title.ifBlank { if (hidden) S.lockedNote else "" },
                markdown = if (hidden) "" else n.body,
                items = if (!hidden && n.kind == NoteKind.CHECKLIST) Checklist.parse(n.body) else null,
                pictures = pictures,
                footer = S.appName + " · " + fullDateTime(n.updatedAt),
                color = color,
            )
        }
        val title = if (notes.size == 1) notes[0].displayTitle().ifBlank { S.noteFileName } else S.appName
        title to Html.document(title, pages)
    }

    /** Opens the print dialog for [html]; the user picks "Save as PDF". */
    fun print(activity: Activity, title: String, html: String) {
        val wv = WebView(activity)
        webView = wv
        wv.settings.javaScriptEnabled = false
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = activity.getSystemService(PrintManager::class.java)
                val job = title.take(60)
                pm.print(
                    job, view.createPrintDocumentAdapter(job),
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build(),
                )
            }
        }
        wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private fun dataUri(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun hex(c: Color): String = "#%06x".format(c.toArgb() and 0xFFFFFF)
}
