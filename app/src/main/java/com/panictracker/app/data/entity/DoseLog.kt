package com.panictracker.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.panictracker.app.pk.PkDose

/** 실제로 복용한 1회 투약 기록. */
@Entity(
    tableName = "dose_log",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("medicationId"), Index("takenAt")],
)
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    /** 복용 시각 (epoch millis). */
    val takenAt: Long,
    /** 실제 복용량 (mg). 반 알을 먹었다면 표준 용량의 절반을 적습니다. */
    val doseMg: Double,
    val note: String = "",
) {
    fun toPk(): PkDose = PkDose(medicationId, takenAt, doseMg)
}
