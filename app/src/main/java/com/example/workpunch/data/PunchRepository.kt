package com.example.workpunch.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class PunchRepository(private val dao: PunchDao) {
    fun observeToday(): Flow<PunchRecord?> = observeDay(LocalDate.now())

    fun observeDay(date: LocalDate): Flow<PunchRecord?> {
        return dao.observeByDay(date.toEpochDayKey())
    }

    fun observeMonth(month: LocalDate): Flow<List<PunchRecord>> {
        val first = month.withDayOfMonth(1)
        val last = first.plusMonths(1).minusDays(1)
        return observeBetween(first, last)
    }

    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<PunchRecord>> {
        return dao.observeBetween(start.toEpochDayKey(), end.toEpochDayKey())
    }

    fun observeStats(start: LocalDate, end: LocalDate): Flow<StatsSummary> {
        val safeStart = minOf(start, end)
        val safeEnd = maxOf(start, end)
        return observeBetween(safeStart, safeEnd).map { records ->
            records.toStatsSummary(safeStart, safeEnd)
        }
    }

    suspend fun clockIn(nowMillis: Long = System.currentTimeMillis(), date: LocalDate = LocalDate.now()) {
        val key = date.toEpochDayKey()
        val old = dao.getByDay(key)
        dao.upsert(
            old?.copy(
                clockInMillis = old.clockInMillis ?: nowMillis,
                updatedAtMillis = nowMillis
            )
                ?: PunchRecord(epochDay = key, clockInMillis = nowMillis, updatedAtMillis = nowMillis)
        )
    }

    suspend fun clockOut(nowMillis: Long = System.currentTimeMillis(), date: LocalDate = LocalDate.now()) {
        val key = date.toEpochDayKey()
        val old = dao.getByDay(key)
        dao.upsert(
            old?.copy(
                clockOutMillis = old.clockOutMillis ?: nowMillis,
                updatedAtMillis = nowMillis
            )
                ?: PunchRecord(epochDay = key, clockOutMillis = nowMillis, updatedAtMillis = nowMillis)
        )
    }

    suspend fun saveRecord(date: LocalDate, clockInMillis: Long?, clockOutMillis: Long?) {
        val key = date.toEpochDayKey()
        if (clockInMillis == null && clockOutMillis == null) {
            dao.deleteByDay(key)
            return
        }
        dao.upsert(
            PunchRecord(
                epochDay = key,
                clockInMillis = clockInMillis,
                clockOutMillis = clockOutMillis,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteRecord(date: LocalDate) {
        dao.deleteByDay(date.toEpochDayKey())
    }
}

fun List<PunchRecord>.toStatsSummary(start: LocalDate, end: LocalDate): StatsSummary {
    val complete = mapNotNull { it.workedMinutes() }
    val total = complete.sum()
    val weeks = ChronoUnit.WEEKS.between(start.weekStart(), end.weekStart()) + 1
    return StatsSummary(
        totalDays = size,
        completeDays = complete.size,
        clockInDays = count { it.clockInMillis != null },
        totalMinutes = total,
        averageMinutes = if (complete.isEmpty()) 0 else total / complete.size,
        weeklyAverageMinutes = if (weeks == 0L) 0 else total / weeks
    )
}
