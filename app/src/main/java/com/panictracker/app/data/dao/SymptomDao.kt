package com.panictracker.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.panictracker.app.data.entity.SymptomLog
import com.panictracker.app.data.entity.SymptomType
import kotlinx.coroutines.flow.Flow

/** 통계 집계용 결과 행. */
data class SymptomCount(val type: SymptomType, val count: Int)
data class HourCount(val hour: Int, val count: Int)
data class DayCount(val day: String, val count: Int)

@Dao
interface SymptomDao {
    @Query("SELECT * FROM symptom_log ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SymptomLog>>

    @Query("SELECT * FROM symptom_log WHERE occurredAt >= :fromMs ORDER BY occurredAt DESC")
    fun observeSince(fromMs: Long): Flow<List<SymptomLog>>

    @Query("SELECT * FROM symptom_log WHERE id = :id")
    suspend fun byId(id: Long): SymptomLog?

    @Query(
        "SELECT type, COUNT(*) AS count FROM symptom_log " +
            "WHERE occurredAt >= :fromMs GROUP BY type ORDER BY count DESC"
    )
    fun countsByType(fromMs: Long): Flow<List<SymptomCount>>

    /**
     * 시간대(0~23)별 발생 횟수. SQLite 의 strftime 은 초 단위 UTC 를 받으므로
     * 'localtime' 수식어로 기기 표준시에 맞춥니다.
     */
    @Query(
        "SELECT CAST(strftime('%H', occurredAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, " +
            "COUNT(*) AS count FROM symptom_log WHERE occurredAt >= :fromMs " +
            "GROUP BY hour ORDER BY hour ASC"
    )
    fun countsByHour(fromMs: Long): Flow<List<HourCount>>

    /** 날짜(YYYY-MM-DD)별 발생 횟수. */
    @Query(
        "SELECT strftime('%Y-%m-%d', occurredAt / 1000, 'unixepoch', 'localtime') AS day, " +
            "COUNT(*) AS count FROM symptom_log WHERE occurredAt >= :fromMs " +
            "GROUP BY day ORDER BY day ASC"
    )
    fun countsByDay(fromMs: Long): Flow<List<DayCount>>

    @Query("SELECT AVG(severity) FROM symptom_log WHERE occurredAt >= :fromMs AND severity IS NOT NULL")
    fun averageSeverity(fromMs: Long): Flow<Double?>

    @Insert suspend fun insert(s: SymptomLog): Long
    @Update suspend fun update(s: SymptomLog)
    @Delete suspend fun delete(s: SymptomLog)

    @Query("DELETE FROM symptom_log WHERE id = :id")
    suspend fun deleteById(id: Long)
}
