package com.example.workpunch.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Entity(tableName = "punch_records")
data class PunchRecord(
    @PrimaryKey val epochDay: Long,
    val clockInMillis: Long? = null,
    val clockOutMillis: Long? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val date: LocalDate
        get() = LocalDate.ofEpochDay(epochDay)

    fun workedMinutes(): Long? {
        val start = clockInMillis ?: return null
        val end = clockOutMillis ?: return null
        return ((end - start) / 60_000).coerceAtLeast(0)
    }

    fun workedMinutesAt(nowMillis: Long): Long? {
        val start = clockInMillis ?: return null
        return (((clockOutMillis ?: nowMillis) - start) / 60_000).coerceAtLeast(0)
    }
}

data class StatsSummary(
    val totalDays: Int,
    val completeDays: Int,
    val clockInDays: Int,
    val totalMinutes: Long,
    val averageMinutes: Long,
    val weeklyAverageMinutes: Long
)

fun LocalDate.toEpochDayKey(): Long = toEpochDay()

fun Long.toLocalTimeText(): String {
    return DateTimeFormatter.ofPattern("HH:mm")
        .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime())
}

fun minutesToHourText(minutes: Long): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return "${hours}小时${rest}分钟"
}

/** A compact duration for calendar cells, for example 8:30. */
fun minutesToCalendarText(minutes: Long): String {
    return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
}

fun minutesToDecimalHourText(minutes: Long): String {
    return String.format(Locale.US, "%.2f 小时", minutes / 60.0)
}

fun minutesToCalendarDecimalText(minutes: Long): String {
    return String.format(Locale.US, "%.2fh", minutes / 60.0)
}

fun LocalDate.weekStart(): LocalDate {
    return minus(daysFromMonday().toLong(), ChronoUnit.DAYS)
}

private fun LocalDate.daysFromMonday(): Int = dayOfWeek.value - 1
