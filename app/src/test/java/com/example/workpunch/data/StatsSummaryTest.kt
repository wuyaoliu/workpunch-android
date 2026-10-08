package com.example.workpunch.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsSummaryTest {
    @Test
    fun formatsCompactCalendarDuration() {
        assertEquals("8:05", minutesToCalendarText(8 * 60 + 5))
    }

    @Test
    fun formatsDecimalHours() {
        assertEquals("11.50 小时", minutesToDecimalHourText(11 * 60 + 30))
        assertEquals("8.75 小时", minutesToDecimalHourText(8 * 60 + 45))
        assertEquals("8.75h", minutesToCalendarDecimalText(8 * 60 + 45))
    }

    @Test
    fun calculatesOngoingWorkFromClockIn() {
        val record = PunchRecord(epochDay = LocalDate.of(2026, 7, 13).toEpochDay(), clockInMillis = 1_000L)

        assertEquals(125L, record.workedMinutesAt(7_501_000L))
    }

    @Test
    fun calculatesDailyAndWeeklyAveragesAcrossTheSelectedRange() {
        val monday = LocalDate.of(2026, 7, 6)
        val records = listOf(
            record(monday, 8 * 60),
            record(monday.plusDays(1), 6 * 60),
            record(monday.plusDays(7), 10 * 60)
        )

        val summary = records.toStatsSummary(monday, monday.plusDays(8))

        assertEquals(3, summary.completeDays)
        assertEquals(3, summary.clockInDays)
        assertEquals(24 * 60L, summary.totalMinutes)
        assertEquals(8 * 60L, summary.averageMinutes)
        assertEquals(12 * 60L, summary.weeklyAverageMinutes)
    }

    private fun record(date: LocalDate, workedMinutes: Long): PunchRecord {
        val start = 1_700_000_000_000L
        return PunchRecord(
            epochDay = date.toEpochDay(),
            clockInMillis = start,
            clockOutMillis = start + workedMinutes * 60_000
        )
    }
}
