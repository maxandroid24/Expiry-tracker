package com.example.expirytracker.network

import com.example.expirytracker.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Retrofit interface for the Expiry Tracker OCR backend.
 *
 * The backend exposes a single endpoint that accepts a base64-encoded image
 * (and an optional ML Kit OCR hint) and returns extracted product fields. The
 * OpenAI API key lives only on the backend — the Android app never sees it.
 */
interface OcrApi {
    @POST("ocr")
    suspend fun extract(@Body request: OcrRequest): OcrResponse
}

data class OcrRequest(
    val image: String,
    val hint: String? = null,
)

data class OcrResponse(
    val name: String? = null,
    val mfgDate: String? = null,
    val expDate: String? = null,
    val confidence: Double = 0.0,
)

/** Lazily-built singleton Retrofit client pointing at [BuildConfig.BACKEND_OCR_URL]. */
object OcrApiClient {
    val api: OcrApi by lazy { build() }

    private fun build(): OcrApi {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
            else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val baseUrl = BuildConfig.BACKEND_OCR_URL.let {
            if (it.endsWith("/")) it else "$it/"
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OcrApi::class.java)
    }
}
