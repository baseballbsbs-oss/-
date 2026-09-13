package com.panictracker.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 기록 가능한 증상 종류. */
enum class SymptomType(val label: String, val emoji: String) {
    ANXIETY("불안", "😰"),
    HYPERVENTILATION("과호흡", "💨"),
    COLD_SWEAT("식은땀", "💧"),
    PANIC_ATTACK("공황발작", "🚨"),
    DROWSINESS("졸림", "😴"),
    PALPITATIONS("가슴두근거림", "💓"),
}

/**
 * 증상 1건. 빠른 기록(버튼 한 번)에서는 [occurredAt] 만 현재 시각으로 채우고
 * [severity] · [durationMinutes] 는 비워 둡니다. 나중에 눌러서 보완할 수 있습니다.
 */
@Entity(tableName = "symptom_log", indices = [Index("occurredAt"), Index("type")])
data class SymptomLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: SymptomType,
    /** 증상 발생 시각 (epoch millis). */
    val occurredAt: Long,
    /** 강도 1(약함) ~ 5(심함). 빠른 기록 시 null. */
    val severity: Int? = null,
    /** 지속시간 (분). 모르면 null. */
    val durationMinutes: Int? = null,
    val note: String = "",
)
