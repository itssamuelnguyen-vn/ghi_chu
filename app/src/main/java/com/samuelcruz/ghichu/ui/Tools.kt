package com.samuelcruz.ghichu.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Attachment
import com.samuelcruz.ghichu.data.Files
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Waits for a Google Play services task (like `await` on a JS promise). */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * Google's document scanner (runs inside Play services: edge detection, crop, flatten, no camera code here).
 * It returns one JPEG per page.
 */
object Scanner {
    fun start(activity: Activity, onIntent: (IntentSender) -> Unit, onError: () -> Unit) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { onIntent(it) }
            .addOnFailureListener { onError() }
    }

    fun pages(data: Intent?): List<Uri> =
        GmsDocumentScanningResult.fromActivityResultIntent(data)?.pages?.map { it.imageUri }.orEmpty()
}

/** Text in a picture (on the phone, through Play services; Latin script, Vietnamese accents included). */
object Ocr {
    suspend fun read(context: Context, file: File): String {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return recognizer.process(image).await().text.trim()
        } finally {
            recognizer.close()
        }
    }
}

/** Opening and sharing attached files with other apps (through the FileProvider, like a signed download URL). */
object AttachedFiles {
    fun mime(a: Attachment): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(a.ext) ?: "application/octet-stream"

    private fun uri(context: Context, a: Attachment): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", Files.file(context, a))

    /** False when no app can open it. */
    fun open(context: Context, a: Attachment): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri(context, a), mime(a))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (e: ActivityNotFoundException) { false }
    }

    fun share(context: Context, a: Attachment) {
        val intent = Intent(Intent.ACTION_SEND).setType(mime(a)).putExtra(Intent.EXTRA_STREAM, uri(context, a))
            .putExtra(Intent.EXTRA_TITLE, a.displayName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, S.share).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun size(context: Context, a: Attachment): Long = Files.file(context, a).length()
}

/** "1,2 MB", "350 KB". */
fun sizeLabel(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/**
 * Reads a note aloud with the phone's text-to-speech. Vietnamese when the text has Vietnamese letters, English
 * otherwise. [onSpeaking] tells whether it is talking (may be called from another thread).
 */
class Speaker(private val context: Context, private val onSpeaking: (Boolean) -> Unit) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var queued: Pair<String, (String) -> Unit>? = null
    private var lastId = ""

    fun speak(text: String, onError: (String) -> Unit) {
        if (!ready) {
            queued = text to onError
            if (tts == null) tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.setOnUtteranceProgressListener(listener)
                    queued?.let { (t, e) -> queued = null; speak(t, e) }
                } else {
                    queued = null
                    tts = null
                    onError(S.noTts)
                }
            }
            return
        }
        val engine = tts ?: return
        val locale = if (vietnamese.containsMatchIn(text)) Locale.forLanguageTag("vi-VN") else Locale.US
        if (engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) { onError(S.noTtsVoice); return }
        val chunks = chunks(text, TextToSpeech.getMaxSpeechInputLength() - 100)
        chunks.forEachIndexed { k, c ->
            val id = "read-$k-${System.nanoTime()}"
            if (k == chunks.lastIndex) lastId = id
            engine.speak(c, if (k == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
        onSpeaking(true)
    }

    fun stop() {
        tts?.stop()
        onSpeaking(false)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = onSpeaking(true)
        override fun onDone(utteranceId: String?) { if (utteranceId == lastId) onSpeaking(false) }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onSpeaking(false)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = onSpeaking(false)
    }

    private companion object {
        val vietnamese = Regex("[ăâđêôơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ]", RegexOption.IGNORE_CASE)

        /** Pieces of at most [max] characters, cut at paragraph / sentence / word ends. */
        fun chunks(text: String, max: Int): List<String> {
            val out = mutableListOf<String>()
            var rest = text.trim()
            while (rest.length > max) {
                val cut = listOf(rest.lastIndexOf("\n", max), rest.lastIndexOf(". ", max), rest.lastIndexOf(' ', max))
                    .firstOrNull { it > max / 2 } ?: max
                out += rest.substring(0, cut + 1).trim()
                rest = rest.substring(cut + 1).trim()
            }
            if (rest.isNotEmpty()) out += rest
            return out
        }
    }
}
