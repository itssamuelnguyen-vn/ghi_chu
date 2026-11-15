package com.samuelcruz.ghichu.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.samuelcruz.ghichu.core.S
import kotlinx.coroutines.delay
import java.io.File

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Records AAC (.m4a) from the microphone into the cache folder. */
private class Recorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var file: File? = null
        private set
    private var startedAt = 0L

    fun start() {
        val f = File(context.cacheDir, "rec-${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44_100)
        r.setAudioEncodingBitRate(96_000)
        r.setOutputFile(f.path)
        r.prepare()
        r.start()
        recorder = r
        file = f
        startedAt = SystemClock.elapsedRealtime()
    }

    fun elapsed(): Long = if (recorder == null) 0 else SystemClock.elapsedRealtime() - startedAt

    fun amplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    /** Stops; returns the length in ms (0 if it failed). */
    fun stop(): Long {
        val r = recorder ?: return 0
        val len = elapsed()
        recorder = null
        return try { r.stop(); r.release(); len } catch (e: RuntimeException) { r.release(); file?.delete(); 0 }
    }

    fun cancel() {
        stop()
        file?.delete()
    }
}

/** Recording sheet: asks for the microphone, shows time and a live level meter. */
@Composable
fun RecordDialog(onSaved: (File, Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember { Recorder(context) }
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var error by remember { mutableStateOf<String?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) error = S.micDenied
    }
    var elapsed by remember { mutableLongStateOf(0L) }
    val levels = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.RECORD_AUDIO) }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        runCatching { recorder.start() }.onFailure { error = S.recordFailed }
        while (error == null) {
            elapsed = recorder.elapsed()
            levels.add((recorder.amplitude() / 22_000f).coerceIn(0.04f, 1f))
            if (levels.size > 40) levels.removeAt(0)
            delay(90)
        }
    }
    DisposableEffect(Unit) { onDispose { if (recorder.file?.exists() == true && recorder.elapsed() > 0) recorder.cancel() } }

    val pulse = rememberInfiniteTransition(label = "rec")
    val dot by pulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")

    AlertDialog(
        onDismissRequest = {},
        title = { Text(S.recording) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).alpha(dot).clip(CircleShape).background(Color(0xFFEF4444)))
                        Spacer(Modifier.width(10.dp))
                        Text(formatDuration(elapsed), style = MaterialTheme.typography.displaySmall)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth().height(56.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        levels.forEach { l ->
                            Box(
                                Modifier.width(4.dp).height((56 * l).dp).clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f + 0.6f * l)),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = error == null && granted, onClick = {
                val f = recorder.file
                val len = recorder.stop()
                if (f != null && len > 500) onSaved(f, len) else { f?.delete(); onDismiss() }
            }) { Text(S.stopAndSave) }
        },
        dismissButton = { TextButton(onClick = { recorder.cancel(); onDismiss() }) { Text(S.cancel) } },
    )
}

/** A recording in a note: play/pause, position slider, length; optional remove button. */
@Composable
fun AudioRow(file: File, durationMs: Long, onDelete: (() -> Unit)?) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var length by remember { mutableLongStateOf(durationMs) }
    DisposableEffect(file) { onDispose { player?.release(); player = null } }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { p -> if (p.duration > 0) position = p.currentPosition.toFloat() / p.duration }
            delay(200)
        }
    }
    fun toggle() {
        val p = player ?: runCatching {
            MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
                setOnCompletionListener { playing = false; position = 0f }
                length = duration.toLong()
            }
        }.getOrNull()?.also { player = it } ?: return
        if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
    }
    val exists = file.exists()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(onClick = ::toggle, enabled = exists, colors = IconButtonDefaults.filledIconButtonColors()) {
            Icon(if (playing) Icons.Default.Pause else if (exists) Icons.Default.PlayArrow else Icons.Default.Mic, null)
        }
        Slider(
            value = position,
            onValueChange = { v -> position = v; player?.let { it.seekTo((v * it.duration).toInt()) } },
            enabled = exists && player != null,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            if (exists) formatDuration(if (playing || position > 0) (position * length).toLong() else length) else S.waitingDownload,
            style = MaterialTheme.typography.labelMedium,
        )
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Close, S.delete) }
    }
}
