package com.moneymap.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Receipt photos, one per expense, kept in app-private storage as `receipts/<expenseId>.jpg`.
 * Photos are scaled down to about 1600 px on the long side so they stay small. They are not part of JSON backups.
 */
object Receipts {
    private const val MAX_SIDE = 1600

    private fun dir(context: Context) = File(context.filesDir, "receipts").apply { mkdirs() }

    fun file(context: Context, expenseId: Long) = File(dir(context), "$expenseId.jpg")

    /** Ids of expenses that have a receipt. */
    fun ids(context: Context): Set<Long> =
        dir(context).listFiles()?.mapNotNull { it.nameWithoutExtension.toLongOrNull() }?.toSet().orEmpty()

    /** A content URI the camera app can write a new photo to. */
    fun cameraUri(context: Context): Uri {
        val f = File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    suspend fun save(context: Context, expenseId: Long, source: Uri) = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Could not read the photo")
        file(context, expenseId).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
    }

    fun remove(context: Context, expenseId: Long) {
        file(context, expenseId).delete()
    }

    /** Loads a receipt scaled for showing on screen, or null if there is none. */
    suspend fun load(context: Context, expenseId: Long, maxSide: Int = 1200): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(context, expenseId)
        if (!f.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Deletes photos whose expense no longer exists. */
    fun cleanUp(context: Context, expenseIds: Set<Long>) {
        dir(context).listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension.toLongOrNull()
            if (id == null || id !in expenseIds) f.delete()
        }
    }
}
