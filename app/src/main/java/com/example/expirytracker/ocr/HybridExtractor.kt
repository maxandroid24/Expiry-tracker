package com.example.expirytracker.ocr

import android.graphics.Bitmap
import com.example.expirytracker.network.CloudOcrService

/**
 * Runs the fast on-device pipeline first. If the heuristic confidence is below
 * [threshold] and a cloud key is configured, fall back to the cloud model.
 */
object HybridExtractor {
    suspend fun extract(bitmap: Bitmap, threshold: Float = 0.6f): Pair<ExtractedFields, String> {
        val text = OcrAnalyzer.recognize(bitmap)
        val onDevice = TextParser.parse(text)

        val needsFallback = onDevice.confidence < threshold &&
                CloudOcrService.isConfigured

        if (!needsFallback) return onDevice to "on-device"

        val cloud = CloudOcrService.extract(bitmap, ocrHint = text)
        return if (cloud != null && cloud.confidence >= onDevice.confidence) {
            cloud to "cloud"
        } else {
            onDevice to "on-device"
        }
    }
}
