package com.example.expirytracker.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun format(epochMillis: Long): String = displayFormat.format(Date(epochMillis))

    fun isExpired(expiryEpochMillis: Long): Boolean {
        val today = todayUtcMillis()
        return expiryEpochMillis < today
    }

    fun todayUtcMillis(): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun toUtcMidnight(year: Int, monthZeroBased: Int, day: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, monthZeroBased, day, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
