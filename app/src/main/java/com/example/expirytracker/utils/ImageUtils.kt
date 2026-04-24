package com.example.expirytracker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageUtils {
    /** Persist a bitmap to internal storage and return the absolute path. */
    fun saveBitmapToInternal(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "product_images").apply { mkdirs() }
        val file = File(dir, "img_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    fun saveUriToInternal(context: Context, uri: Uri): String? {
        val bitmap = decodeUriToBitmap(context, uri) ?: return null
        return saveBitmapToInternal(context, bitmap)
    }

    fun decodeUriToBitmap(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
        val resolver = context.contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while ((opts.outWidth / sample) > maxDim || (opts.outHeight / sample) > maxDim) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null
        return rotateIfNeeded(context, uri, bmp)
    }

    private fun rotateIfNeeded(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
                val degrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees != 0f) {
                    val matrix = Matrix().apply { postRotate(degrees) }
                    Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                } else bmp
            } ?: bmp
        } catch (_: Exception) { bmp }
    }

    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80, maxDim: Int = 1024): String {
        val scale = maxOf(bitmap.width, bitmap.height).toFloat() / maxDim
        val resized = if (scale > 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width / scale).toInt(), (bitmap.height / scale).toInt(), true)
        } else bitmap
        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
    }
}
