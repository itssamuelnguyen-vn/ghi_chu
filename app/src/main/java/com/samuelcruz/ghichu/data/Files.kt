package com.samuelcruz.ghichu.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Attachment files in `filesDir/att`. Photos are turned upright, shrunk to at most 2048 px and saved as JPEG
 * (quality 82, usually 250–600 KB); recordings are AAC `.m4a` straight from the recorder.
 */
object Files {
    private const val MAX_SIDE = 2048

    fun dir(context: Context) = File(context.filesDir, "att").apply { mkdirs() }
    fun file(context: Context, a: Attachment) = File(dir(context), a.fileName)
    fun file(context: Context, name: String) = File(dir(context), name)

    /** Reads, rotates upright, shrinks and saves a picture as `<id>.jpg`. */
    suspend fun saveImage(context: Context, source: Uri, id: String): Unit = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        saveImage({ resolver.openInputStream(source) ?: error("Cannot read the picture") }, file(context, "$id.jpg"))
    }

    fun saveImage(open: () -> InputStream, target: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = open().use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Cannot read the picture")
        val rotation = runCatching { open().use { ExifInterface(it).rotationDegrees } }.getOrDefault(0)
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
    }

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** A photo shrunk to about [maxSide] px, cached in memory (lists show the same thumbnails again and again). */
    suspend fun loadImage(context: Context, name: String, maxSide: Int): Bitmap? {
        val key = "$name@$maxSide"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(context, name)
            if (!f.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.also { cache.put(key, it) }
        }
    }

    fun forget(name: String) {
        cache.snapshot().keys.filter { it.startsWith("$name@") }.forEach { cache.remove(it) }
    }
}
