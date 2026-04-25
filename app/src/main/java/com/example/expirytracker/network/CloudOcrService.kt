package com.example.expirytracker.network

import android.graphics.Bitmap
import com.example.expirytracker.BuildConfig
import com.example.expirytracker.ocr.ExtractedFields
import com.example.expirytracker.utils.DateUtils
import com.example.expirytracker.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Cloud fallback for product extraction. Sends a compressed, base64-encoded
 * image to the project's backend, which calls OpenAI on the server side.
 *
 * The Android app NEVER sees the OpenAI API key. This service only knows
 * about the backend URL configured at build time.
 *
 * Returns null if the backend URL is not configured or the request fails.
 */
object CloudOcrService {

    /** True if a non-empty backend URL was provided at build time. */
    val isConfigured: Boolean get() = BuildConfig.BACKEND_OCR_URL.isNotBlank()

    suspend fun extract(bitmap: Bitmap, ocrHint: String?): ExtractedFields? =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext null

            val base64 = ImageUtils.bitmapToBase64(bitmap)
            val request = OcrRequest(image = base64, hint = ocrHint?.take(2000))

            runCatching {
                OcrApiClient.api.extract(request)
            }.getOrNull()?.let { resp ->
                ExtractedFields(
                    name = resp.name?.takeIf { it.isNotBlank() && it != "null" },
                    manufacturingDate = parseIsoDate(resp.mfgDate),
                    expiryDate = parseIsoDate(resp.expDate),
                    confidence = resp.confidence
                        .toFloat()
                        .coerceIn(0f, 1f)
                        .let { if (it == 0f) 0.7f else it },
                )
            }
        }

    private val isoDayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val isoMonthFormat = SimpleDateFormat("yyyy-MM", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Backend returns ISO `YYYY-MM-DD` (or `YYYY-MM` when only month is known). */
    private fun parseIsoDate(s: String?): Long? {
        if (s.isNullOrBlank() || s == "null") return null
        val fmt = when {
            Regex("""^\d{4}-\d{2}-\d{2}$""").matches(s) -> isoDayFormat
            Regex("""^\d{4}-\d{2}$""").matches(s) -> isoMonthFormat
            else -> return null
        }
        return runCatching {
            val d = fmt.parse(s) ?: return null
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = d }
            DateUtils.toUtcMidnight(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }.getOrNull()
    }
}
