package com.panictracker.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.panictracker.app.data.entity.DoseLog
import com.panictracker.app.data.entity.Medication
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medication ORDER BY isActive DESC, ingredientName ASC")
    fun observeAll(): Flow<List<Medication>>

    @Query("SELECT * FROM medication WHERE isActive = 1 ORDER BY ingredientName ASC")
    fun observeActive(): Flow<List<Medication>>

    @Query("SELECT * FROM medication WHERE id = :id")
    suspend fun byId(id: Long): Medication?

    @Query("SELECT COUNT(*) FROM medication")
    suspend fun count(): Int

    @Insert suspend fun insert(m: Medication): Long
    @Update suspend fun update(m: Medication)
    @Delete suspend fun delete(m: Medication)
}

@Dao
interface DoseDao {
    @Query("SELECT * FROM dose_log WHERE takenAt >= :fromMs ORDER BY takenAt DESC")
    fun observeSince(fromMs: Long): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_log ORDER BY takenAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_log WHERE takenAt BETWEEN :fromMs AND :toMs ORDER BY takenAt ASC")
    suspend fun between(fromMs: Long, toMs: Long): List<DoseLog>

    @Query("SELECT * FROM dose_log WHERE id = :id")
    suspend fun byId(id: Long): DoseLog?

    @Insert suspend fun insert(d: DoseLog): Long
    @Update suspend fun update(d: DoseLog)
    @Delete suspend fun delete(d: DoseLog)
}
