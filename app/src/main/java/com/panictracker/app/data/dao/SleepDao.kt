package com.panictracker.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.panictracker.app.data.entity.SleepAwakening
import com.panictracker.app.data.entity.SleepLog
import com.panictracker.app.data.entity.SleepSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {
    @Transaction
    @Query("SELECT * FROM sleep_log ORDER BY bedTimeAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SleepSession>>

    @Transaction
    @Query("SELECT * FROM sleep_log WHERE bedTimeAt >= :fromMs ORDER BY bedTimeAt ASC")
    fun observeSince(fromMs: Long): Flow<List<SleepSession>>

    @Transaction
    @Query("SELECT * FROM sleep_log WHERE id = :id")
    suspend fun sessionById(id: Long): SleepSession?

    /** 아직 완전히 깨지 않은(= 진행 중인) 수면이 있으면 반환합니다. */
    @Transaction
    @Query("SELECT * FROM sleep_log WHERE finalWakeAt = 0 ORDER BY bedTimeAt DESC LIMIT 1")
    fun observeOngoing(): Flow<List<SleepSession>>

    @Insert suspend fun insert(s: SleepLog): Long
    @Update suspend fun update(s: SleepLog)
    @Delete suspend fun delete(s: SleepLog)

    @Insert suspend fun insertAwakening(a: SleepAwakening): Long
    @Update suspend fun updateAwakening(a: SleepAwakening)
    @Delete suspend fun deleteAwakening(a: SleepAwakening)

    @Query("SELECT * FROM sleep_awakening WHERE sleepLogId = :sleepLogId ORDER BY wokeAt ASC")
    suspend fun awakeningsFor(sleepLogId: Long): List<SleepAwakening>
}
