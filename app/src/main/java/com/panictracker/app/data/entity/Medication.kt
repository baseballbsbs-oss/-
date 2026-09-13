package com.panictracker.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.panictracker.app.pk.PkMedication
import com.panictracker.app.pk.PkProfile

/**
 * 복용 중인 약물 한 가지. 약동학 파라미터는 첨부문서(제품설명서)에서 옮겨 적을 수 있는
 * 값들로만 구성했습니다 — 반감기, Tmax, 발현시간, 작용시간.
 */
@Entity(tableName = "medication")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 성분명 (예: 알프라졸람). */
    val ingredientName: String,

    /** 제품명 (예: 자낙스정). */
    val brandName: String,

    /** 1회 표준 용량 (mg). 농도 100 % 의 기준이 됩니다. */
    val doseMg: Double,

    /** 반감기 (시간). */
    val halfLifeHours: Double,

    /** 최고 혈중농도 도달시간 Tmax (시간). */
    val tmaxHours: Double,

    /** 발현시간 — 복용 후 효과가 나타나기 시작하기까지 (분). */
    val onsetMinutes: Int,

    /** 작용시간 — 1회 복용의 임상적 지속시간 (시간). */
    val durationHours: Double,

    /** 그래프에서 사용할 색 (ARGB). */
    val colorArgb: Int,

    /** 복용 중이면 true. 중단한 약은 false 로 두면 목록 아래로 내려갑니다. */
    val isActive: Boolean = true,

    val note: String = "",
) {
    /** PK 계산기가 쓰는 형태로 변환합니다. */
    fun toPk(): PkMedication = PkMedication(
        id = id,
        label = brandName.ifBlank { ingredientName },
        ingredient = ingredientName,
        profile = PkProfile.of(halfLifeHours, tmaxHours),
        referenceDoseMg = doseMg,
        onsetMinutes = onsetMinutes,
        durationHours = durationHours,
        colorArgb = colorArgb,
    )

    val displayName: String get() = brandName.ifBlank { ingredientName }

    /** 곡선을 그릴 수 있는 값인지. 하나라도 0 이하이면 계산이 불가능합니다. */
    val hasUsablePk: Boolean
        get() = halfLifeHours > 0.0 && tmaxHours > 0.0 && doseMg > 0.0
}
