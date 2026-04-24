package com.example.expirytracker.ocr

import com.example.expirytracker.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class ExtractedFields(
    val name: String? = null,
    val manufacturingDate: Long? = null,
    val expiryDate: Long? = null,
    /** 0..1 — how confident we are in the extraction overall. */
    val confidence: Float = 0f,
)

/**
 * Heuristic parser that pulls out a likely product name and MFG/EXP dates from
 * messy OCR text. Designed to be fast and produce a confidence score so the
 * caller can decide whether to fall back to a cloud LLM.
 */
object TextParser {

    private val mfgKeywords = listOf(
        "MFG", "MFD", "MFG DATE", "MFG.DATE", "MANUFACTURED", "MANUFACTURING",
        "MANUFACTURE", "PROD", "PRODUCED", "PACKED", "PKD", "PD", "DATE OF MFG"
    )
    private val expKeywords = listOf(
        "EXP", "EXPY", "EXPIRY", "EXPIRES", "EXPIRATION", "BEST BEFORE", "BB", "BBE",
        "USE BY", "USE BEFORE", "UBB", "BEST BY"
    )

    private val datePatterns = listOf(
        // 12/05/2025, 12-05-25, 12.05.2025
        Regex("""(\d{1,2})[/.\-\\](\d{1,2})[/.\-\\](\d{2}|\d{4})"""),
        // 2025-05-12 / 2025/05/12
        Regex("""(\d{4})[/.\-](\d{1,2})[/.\-](\d{1,2})"""),
        // 12 MAY 2025 or MAY 12 2025 or MAY 2025
        Regex(
            """((?:\d{1,2}\s+)?(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*\s*\d{0,2}[, ]?\s*\d{2,4})""",
            RegexOption.IGNORE_CASE
        )
    )

    fun parse(text: String): ExtractedFields {
        if (text.isBlank()) return ExtractedFields(confidence = 0f)

        val rawLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (rawLines.isEmpty()) return ExtractedFields(confidence = 0f)

        val mfgDate = findKeyedDate(rawLines, mfgKeywords)
        val expDate = findKeyedDate(rawLines, expKeywords)

        // If keyed lookup misses, fall back to all dates and assume earliest=MFG, latest=EXP.
        val (mfgFinal, expFinal) = if (mfgDate == null || expDate == null) {
            val all = collectAllDates(rawLines).distinct().sorted()
            when (all.size) {
                0 -> Pair(mfgDate, expDate)
                1 -> Pair(mfgDate, expDate ?: all.first())
                else -> Pair(mfgDate ?: all.first(), expDate ?: all.last())
            }
        } else Pair(mfgDate, expDate)

        val name = guessName(rawLines)

        var confidence = 0f
        if (name != null) confidence += 0.30f
        if (mfgFinal != null) confidence += 0.30f
        if (expFinal != null) confidence += 0.40f
        if (mfgFinal != null && expFinal != null && expFinal <= mfgFinal) confidence -= 0.20f

        return ExtractedFields(
            name = name,
            manufacturingDate = mfgFinal,
            expiryDate = expFinal,
            confidence = confidence.coerceIn(0f, 1f)
        )
    }

    private fun findKeyedDate(lines: List<String>, keywords: List<String>): Long? {
        // Look on the same line as a keyword first, then on the next line.
        lines.forEachIndexed { idx, line ->
            val upper = line.uppercase(Locale.ROOT)
            if (keywords.any { upper.contains(it) }) {
                parseFirstDate(line)?.let { return it }
                if (idx + 1 < lines.size) parseFirstDate(lines[idx + 1])?.let { return it }
            }
        }
        return null
    }

    private fun collectAllDates(lines: List<String>): List<Long> {
        val out = mutableListOf<Long>()
        for (line in lines) {
            for (p in datePatterns) {
                p.findAll(line).forEach { m ->
                    parseDateMatch(m.value)?.let { out += it }
                }
            }
        }
        return out
    }

    private fun parseFirstDate(line: String): Long? {
        for (p in datePatterns) {
            p.find(line)?.let { m -> parseDateMatch(m.value)?.let { return it } }
        }
        return null
    }

    private fun parseDateMatch(raw: String): Long? {
        val s = raw.trim().replace(',', ' ').replace("\\", "/")
            .replace('.', '/').replace('-', '/').replace(Regex("\\s+"), " ")

        val numeric = Regex("""^(\d{1,4})/(\d{1,2})(?:/(\d{1,4}))?$""").matchEntire(s)
        if (numeric != null) {
            val a = numeric.groupValues[1].toInt()
            val b = numeric.groupValues[2].toInt()
            val cStr = numeric.groupValues[3]
            return when {
                cStr.isEmpty() -> {
                    // MM/YYYY or MM/YY
                    val month = a
                    val year = normalizeYear(b)
                    if (month in 1..12) DateUtils.toUtcMidnight(year, month - 1, 1) else null
                }
                a > 31 -> { // YYYY/MM/DD
                    DateUtils.toUtcMidnight(a, (b - 1).coerceIn(0, 11), cStr.toInt().coerceIn(1, 31))
                }
                else -> { // DD/MM/YYYY (locale-friendly default)
                    val year = normalizeYear(cStr.toInt())
                    DateUtils.toUtcMidnight(year, (b - 1).coerceIn(0, 11), a.coerceIn(1, 31))
                }
            }
        }

        // Month-name patterns
        val monthMap = mapOf(
            "JAN" to 0, "FEB" to 1, "MAR" to 2, "APR" to 3, "MAY" to 4, "JUN" to 5,
            "JUL" to 6, "AUG" to 7, "SEP" to 8, "OCT" to 9, "NOV" to 10, "DEC" to 11
        )
        val upper = s.uppercase(Locale.ROOT)
        val monthRegex = Regex("""(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)""")
        val mMatch = monthRegex.find(upper) ?: return null
        val month = monthMap[mMatch.value] ?: return null
        val nums = Regex("""\d+""").findAll(upper).map { it.value.toInt() }.toList()
        return when (nums.size) {
            1 -> DateUtils.toUtcMidnight(normalizeYear(nums[0]), month, 1)
            2 -> {
                val (day, year) = if (nums[0] > 31) Pair(1, nums[0])
                else if (nums[1] > 31 || nums[1] >= 100) Pair(nums[0].coerceIn(1, 31), nums[1])
                else Pair(nums[0].coerceIn(1, 31), normalizeYear(nums[1]))
                DateUtils.toUtcMidnight(normalizeYear(year), month, day)
            }
            3 -> {
                val day = nums[0].coerceIn(1, 31)
                val year = normalizeYear(nums.last())
                DateUtils.toUtcMidnight(year, month, day)
            }
            else -> null
        }
    }

    private fun normalizeYear(y: Int): Int = when {
        y >= 100 -> y
        y >= 70 -> 1900 + y
        else -> 2000 + y
    }

    private fun guessName(lines: List<String>): String? {
        // Pick the longest "wordy" line near the top that doesn't look like a date or batch line.
        val candidates = lines.take(6).filter { line ->
            val u = line.uppercase(Locale.ROOT)
            line.length in 3..60 &&
                line.any { it.isLetter() } &&
                !mfgKeywords.any { u.contains(it) } &&
                !expKeywords.any { u.contains(it) } &&
                !u.contains("BATCH") && !u.contains("LOT") && !u.contains("MRP") &&
                !Regex("""\d{1,2}[/.\-]\d{1,2}""").containsMatchIn(line)
        }
        return candidates.maxByOrNull { it.count { ch -> ch.isLetter() } }?.let { titleCase(it) }
    }

    private fun titleCase(s: String): String =
        s.lowercase(Locale.ROOT).split(' ').joinToString(" ") {
            if (it.isEmpty()) it else it.replaceFirstChar { c -> c.uppercase(Locale.ROOT) }
        }
}
