package com.panictracker.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class SleepKind(val label: String) {
    NIGHT("밤잠"),
    NAP("낮잠"),
}

/**
 * 수면 1회분.
 *
 * - [bedTimeAt]  취침 시각 (누운 시각)
 * - [sleepAt]    실제로 잠든 시각. 모르면 null 이며 이때는 취침 시각을 잠든 시각으로 봅니다.
 * - [finalWakeAt] 완전히 깬 시각
 * - [getUpAt]    잠자리에서 일어난 시각. 없으면 완전히 깬 시각과 같게 봅니다.
 *
 * 중간에 깬 구간은 [SleepAwakening] 으로 따로 저장하고, 총 수면시간에서 빼 줍니다.
 */
@Entity(tableName = "sleep_log", indices = [Index("bedTimeAt")])
data class SleepLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: SleepKind = SleepKind.NIGHT,
    val bedTimeAt: Long,
    val sleepAt: Long? = null,
    val finalWakeAt: Long,
    val getUpAt: Long? = null,
    /** 주관적 수면의 질 1(나쁨) ~ 5(좋음). */
    val quality: Int? = null,
    val note: String = "",
)

/** 밤중에 깼던 구간 1개. [backToSleepAt] 이 null 이면 아직 다시 잠들지 않은 상태. */
@Entity(
    tableName = "sleep_awakening",
    foreignKeys = [
        ForeignKey(
            entity = SleepLog::class,
            parentColumns = ["id"],
            childColumns = ["sleepLogId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sleepLogId")],
)
data class SleepAwakening(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sleepLogId: Long,
    /** 깬 시각. */
    val wokeAt: Long,
    /** 다시 잠든 시각. */
    val backToSleepAt: Long? = null,
    val note: String = "",
) {
    /** 깨어 있던 시간 (분). 아직 다시 잠들지 않았으면 0. */
    val awakeMinutes: Int
        get() = backToSleepAt?.let { ((it - wokeAt) / 60_000L).toInt().coerceAtLeast(0) } ?: 0
}

/** 수면 기록 + 중간에 깬 구간들. 통계는 대부분 이 조합 위에서 계산합니다. */
data class SleepSession(
    @androidx.room.Embedded val sleep: SleepLog,
    @Relation(parentColumn = "id", entityColumn = "sleepLogId")
    val awakenings: List<SleepAwakening> = emptyList(),
) {
    /** 잠자리에 머문 시간 (분) — 취침부터 기상까지. */
    val timeInBedMinutes: Int
        get() = (((sleep.getUpAt ?: sleep.finalWakeAt) - sleep.bedTimeAt) / 60_000L)
            .toInt().coerceAtLeast(0)

    /** 잠들기까지 걸린 시간 (분). */
    val sleepLatencyMinutes: Int
        get() = sleep.sleepAt?.let { ((it - sleep.bedTimeAt) / 60_000L).toInt().coerceAtLeast(0) } ?: 0

    /** 밤중에 깨어 있던 총 시간 (분). */
    val awakeAfterOnsetMinutes: Int
        get() = awakenings.sumOf { it.awakeMinutes }

    /** 실제로 잔 시간 (분) = (잠든 시각 → 완전히 깬 시각) − 중간에 깬 시간. */
    val totalSleepMinutes: Int
        get() {
            val start = sleep.sleepAt ?: sleep.bedTimeAt
            val gross = ((sleep.finalWakeAt - start) / 60_000L).toInt()
            return (gross - awakeAfterOnsetMinutes).coerceAtLeast(0)
        }

    /** 수면 효율 (%) = 실제 수면시간 / 잠자리에 머문 시간. */
    val efficiencyPercent: Int
        get() = if (timeInBedMinutes <= 0) 0
        else (totalSleepMinutes * 100.0 / timeInBedMinutes).toInt().coerceIn(0, 100)

    val awakeningCount: Int get() = awakenings.size
}
