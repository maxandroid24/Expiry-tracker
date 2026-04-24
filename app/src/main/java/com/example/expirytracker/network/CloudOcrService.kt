package com.example.expirytracker.network

import android.graphics.Bitmap
import com.example.expirytracker.BuildConfig
import com.example.expirytracker.ocr.ExtractedFields
import com.example.expirytracker.utils.DateUtils
import com.example.expirytracker.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Cloud fallback for product extraction. Uses OpenAI's chat completions API
 * with a vision-capable model. Returns null if no API key is configured or the
 * request fails. The caller should keep the on-device result in that case.
 */
object CloudOcrService {
    private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
    private const val MODEL = "gpt-4o-mini"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    val isConfigured: Boolean get() = BuildConfig.OPENAI_API_KEY.isNotBlank()

    suspend fun extract(bitmap: Bitmap, ocrHint: String?): ExtractedFields? = withContext(Dispatchers.IO) {
        val key = BuildConfig.OPENAI_API_KEY
        if (key.isBlank()) return@withContext null

        val base64 = ImageUtils.bitmapToBase64(bitmap)
        val sys = "You extract product info from packaging photos. " +
                "Always respond with strict JSON: {\"name\":string|null,\"manufacturing_date\":\"YYYY-MM-DD\"|null," +
                "\"expiry_date\":\"YYYY-MM-DD\"|null,\"confidence\":0..1}. No prose, no code fences."
        val userText = buildString {
            append("Identify product name, manufacturing date and expiry date from this image. ")
            append("Output JSON only.")
            if (!ocrHint.isNullOrBlank()) {
                append(" Here is OCR text already extracted from the image as a hint: ")
                append(ocrHint.take(2000))
            }
        }

        val body = JSONObject().apply {
            put("model", MODEL)
            put("temperature", 0)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", sys))
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().put("type", "text").put("text", userText))
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64"))
                        })
                    })
                })
            })
        }

        val req = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return@withContext runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string() ?: return@use null
                val content = JSONObject(raw)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                parsePayload(content)
            }
        }.getOrNull()
    }

    private fun parsePayload(content: String): ExtractedFields? {
        val cleaned = content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val obj = JSONObject(cleaned)
            ExtractedFields(
                name = obj.optString("name").takeIf { it.isNotBlank() && it != "null" },
                manufacturingDate = parseIso(obj.optString("manufacturing_date")),
                expiryDate = parseIso(obj.optString("expiry_date")),
                confidence = obj.optDouble("confidence", 0.7).toFloat().coerceIn(0f, 1f)
            )
        } catch (_: Exception) { null }
    }

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseIso(s: String?): Long? {
        if (s.isNullOrBlank() || s == "null") return null
        return try {
            val d = isoFormat.parse(s) ?: return null
            // Snap to UTC midnight already
            val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = d }
            DateUtils.toUtcMidnight(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
        } catch (_: Exception) { null }
    }
}
