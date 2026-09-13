package com.panictracker.app.data.repo

import com.panictracker.app.data.AppDatabase
import com.panictracker.app.data.entity.DoseLog
import com.panictracker.app.data.entity.Medication
import com.panictracker.app.data.entity.SleepAwakening
import com.panictracker.app.data.entity.SleepKind
import com.panictracker.app.data.entity.SleepLog
import com.panictracker.app.data.entity.SleepSession
import com.panictracker.app.data.entity.SymptomLog
import com.panictracker.app.data.entity.SymptomType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 화면(ViewModel)이 쓰는 단일 창구. */
class TrackerRepository(private val db: AppDatabase) {

    private val medDao get() = db.medicationDao()
    private val doseDao get() = db.doseDao()
    private val symptomDao get() = db.symptomDao()
    private val sleepDao get() = db.sleepDao()

    // ── 약물 ─────────────────────────────────────────────────────────────────
    fun medications(): Flow<List<Medication>> = medDao.observeAll()
    fun activeMedications(): Flow<List<Medication>> = medDao.observeActive()
    suspend fun medication(id: Long) = medDao.byId(id)
    suspend fun medicationCount() = medDao.count()
    suspend fun addMedication(m: Medication): Long = medDao.insert(m)
    suspend fun updateMedication(m: Medication) = medDao.update(m)
    suspend fun deleteMedication(m: Medication) = medDao.delete(m)

    // ── 투약 ─────────────────────────────────────────────────────────────────
    fun dosesSince(fromMs: Long): Flow<List<DoseLog>> = doseDao.observeSince(fromMs)
    fun recentDoses(limit: Int = 50): Flow<List<DoseLog>> = doseDao.observeRecent(limit)

    /** 지금 복용했다고 기록합니다. 용량을 주지 않으면 약에 등록된 표준 용량을 씁니다. */
    suspend fun logDose(
        medicationId: Long,
        atMs: Long = System.currentTimeMillis(),
        doseMg: Double? = null,
        note: String = "",
    ): Long {
        val amount = doseMg ?: medDao.byId(medicationId)?.doseMg ?: 0.0
        return doseDao.insert(DoseLog(medicationId = medicationId, takenAt = atMs, doseMg = amount, note = note))
    }

    suspend fun updateDose(d: DoseLog) = doseDao.update(d)
    suspend fun deleteDose(d: DoseLog) = doseDao.delete(d)
    suspend fun dose(id: Long) = doseDao.byId(id)

    // ── 증상 ─────────────────────────────────────────────────────────────────
    fun recentSymptoms(limit: Int = 100): Flow<List<SymptomLog>> = symptomDao.observeRecent(limit)
    fun symptomsSince(fromMs: Long): Flow<List<SymptomLog>> = symptomDao.observeSince(fromMs)

    /** 버튼 한 번으로 끝나는 빠른 기록 — 현재 시각만 남기고 강도·메모는 비워 둡니다. */
    suspend fun quickLogSymptom(type: SymptomType, atMs: Long = System.currentTimeMillis()): Long =
        symptomDao.insert(SymptomLog(type = type, occurredAt = atMs))

    suspend fun updateSymptom(s: SymptomLog) = symptomDao.update(s)
    suspend fun deleteSymptom(s: SymptomLog) = symptomDao.delete(s)
    suspend fun deleteSymptomById(id: Long) = symptomDao.deleteById(id)
    suspend fun symptom(id: Long) = symptomDao.byId(id)

    // ── 수면 ─────────────────────────────────────────────────────────────────
    fun recentSleep(limit: Int = 60): Flow<List<SleepSession>> = sleepDao.observeRecent(limit)
    fun sleepSince(fromMs: Long): Flow<List<SleepSession>> = sleepDao.observeSince(fromMs)
    fun ongoingSleep(): Flow<SleepSession?> = sleepDao.observeOngoing().map { it.firstOrNull() }
    suspend fun sleepSession(id: Long) = sleepDao.sessionById(id)

    /**
     * "지금 잔다" — 진행 중인 수면을 시작합니다.
     * 완전히 깬 시각은 0 으로 두고, 나중에 [finishSleep] 으로 채웁니다.
     */
    suspend fun startSleep(kind: SleepKind, atMs: Long = System.currentTimeMillis()): Long =
        sleepDao.insert(SleepLog(kind = kind, bedTimeAt = atMs, finalWakeAt = 0L))

    /** "지금 깼다" — 진행 중인 수면을 끝냅니다. */
    suspend fun finishSleep(sleepLogId: Long, atMs: Long = System.currentTimeMillis()) {
        val s = sleepDao.sessionById(sleepLogId) ?: return
        sleepDao.update(s.sleep.copy(finalWakeAt = atMs))
    }

    suspend fun saveSleep(s: SleepLog): Long =
        if (s.id == 0L) sleepDao.insert(s) else { sleepDao.update(s); s.id }

    suspend fun deleteSleep(s: SleepLog) = sleepDao.delete(s)

    /**
     * 수면 기록과 "중간에 깬 구간" 목록을 한 번에 저장합니다.
     * 화면에서 지운 구간은 DB 에서도 지우고, 새로 추가한 구간은 넣고, 나머지는 갱신합니다.
     */
    suspend fun saveSleepSession(log: SleepLog, awakenings: List<SleepAwakening>): Long {
        val id = saveSleep(log)
        val existing = sleepDao.awakeningsFor(id).associateBy { it.id }
        val keptIds = awakenings.mapNotNull { it.id.takeIf { v -> v != 0L } }.toSet()
        existing.values.filter { it.id !in keptIds }.forEach { sleepDao.deleteAwakening(it) }
        for (a in awakenings) {
            val bound = a.copy(sleepLogId = id)
            if (bound.id == 0L) sleepDao.insertAwakening(bound) else sleepDao.updateAwakening(bound)
        }
        return id
    }

    /** 밤중에 깼다고 기록합니다(다시 잠든 시각은 나중에 채웁니다). */
    suspend fun logAwakening(sleepLogId: Long, wokeAt: Long, backToSleepAt: Long? = null): Long =
        sleepDao.insertAwakening(SleepAwakening(sleepLogId = sleepLogId, wokeAt = wokeAt, backToSleepAt = backToSleepAt))

    suspend fun updateAwakening(a: SleepAwakening) = sleepDao.updateAwakening(a)
    suspend fun deleteAwakening(a: SleepAwakening) = sleepDao.deleteAwakening(a)
}
