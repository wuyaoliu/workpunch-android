package com.example.workpunch.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PunchDao {
    @Query("SELECT * FROM punch_records WHERE epochDay = :epochDay LIMIT 1")
    fun observeByDay(epochDay: Long): Flow<PunchRecord?>

    @Query("SELECT * FROM punch_records WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay ASC")
    fun observeBetween(startDay: Long, endDay: Long): Flow<List<PunchRecord>>

    @Query("SELECT * FROM punch_records WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay ASC")
    suspend fun getBetween(startDay: Long, endDay: Long): List<PunchRecord>

    @Query("SELECT * FROM punch_records WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getByDay(epochDay: Long): PunchRecord?

    @Query("DELETE FROM punch_records WHERE epochDay = :epochDay")
    suspend fun deleteByDay(epochDay: Long)

    @Upsert
    suspend fun upsert(record: PunchRecord)
}
