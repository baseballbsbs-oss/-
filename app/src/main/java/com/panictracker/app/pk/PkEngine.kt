package com.panictracker.app.pk

/** PK 계산에 필요한 최소한의 약물 정보 (DB 엔티티와 분리하여 순수 Kotlin 으로 테스트 가능). */
data class PkMedication(
    val id: Long,
    /** 화면에 표시할 이름 (보통 제품명). */
    val label: String,
    /** 성분명. */
    val ingredient: String,
    val profile: PkProfile,
    /** 상대 농도 100 % 의 기준이 되는 1회 용량 (mg). */
    val referenceDoseMg: Double,
    /** 복용 후 효과가 느껴지기 시작하는 시간 (분). */
    val onsetMinutes: Int,
    /** 1회 복용의 임상적 작용 지속시간 (시간). */
    val durationHours: Double,
    val colorArgb: Int,
)

/** 1회 투약 기록. */
data class PkDose(
    val medicationId: Long,
    val takenAtEpochMs: Long,
    val doseMg: Double,
)

data class PkPoint(val epochMs: Long, val percent: Double)

data class PkSeries(
    val medication: PkMedication,
    val points: List<PkPoint>,
) {
    val maxPercent: Double get() = points.maxOfOrNull { it.percent } ?: 0.0
}

enum class PkPhase {
    /** 최근 투약이 없거나 농도가 무시할 수준. */
    NONE,

    /** 복용했지만 아직 발현시간 전. */
    BEFORE_ONSET,

    /** 농도가 오르는 중. */
    RISING,

    /** 최고 농도 부근 (±10 %). */
    PEAK,

    /** 농도가 내려가는 중이지만 아직 작용 범위. */
    FALLING,

    /** 작용 역치 아래로 내려감. */
    WORN_OFF,
}

data class PkStatus(
    val medication: PkMedication,
    val percentNow: Double,
    val phase: PkPhase,
    val lastDoseAtEpochMs: Long?,
    val lastDoseMg: Double?,
    /** 곡선이 최고점에 이르는(또는 이른) 예상 시각. */
    val peakAtEpochMs: Long?,
    /** 농도가 작용 역치 아래로 내려가는 예상 시각. */
    val wearOffAtEpochMs: Long?,
)

/**
 * 여러 번의 투약을 **중첩(superposition)** 하여 예상 농도를 계산합니다.
 * 모델이 용량에 대해 선형이므로, 전체 농도는 각 투약이 만드는 곡선의 단순 합입니다.
 * 따라서 "이전 투약 데이터"가 자동으로 현재 농도에 반영됩니다.
 */
object PkEngine {

    const val MS_PER_HOUR = 3_600_000.0

    /** 이 값 아래로 내려가면 임상적으로 의미 없는 농도로 봅니다 (기준 Cmax 대비 %). */
    const val WEAR_OFF_PERCENT = 20.0

    /** 계산에 포함할 과거 투약의 범위 — 반감기의 이 배수까지 (그 이전은 기여분이 0.1 % 미만). */
    private const val LOOKBACK_HALF_LIVES = 10.0

    /** [epochMs] 시점의 [med] 상대 농도(%). 기준 용량 단회 투약의 Cmax 가 100 %. */
    fun concentrationAt(med: PkMedication, doses: List<PkDose>, epochMs: Long): Double {
        val lookbackMs = (med.profile.halfLifeHours * LOOKBACK_HALF_LIVES * MS_PER_HOUR).toLong()
        var total = 0.0
        for (d in doses) {
            if (d.medicationId != med.id) continue
            if (d.takenAtEpochMs > epochMs) continue
            if (epochMs - d.takenAtEpochMs > lookbackMs) continue
            val hours = (epochMs - d.takenAtEpochMs) / MS_PER_HOUR
            val ratio = if (med.referenceDoseMg > 0) d.doseMg / med.referenceDoseMg else 1.0
            total += 100.0 * ratio * med.profile.shape(hours)
        }
        return total
    }

    /** [fromMs] ~ [toMs] 구간의 농도 곡선을 [stepMinutes] 간격으로 생성합니다. */
    fun series(
        med: PkMedication,
        doses: List<PkDose>,
        fromMs: Long,
        toMs: Long,
        stepMinutes: Int = 5,
    ): PkSeries {
        require(toMs > fromMs) { "종료 시각이 시작 시각보다 뒤여야 합니다." }
        val stepMs = stepMinutes * 60_000L
        val points = ArrayList<PkPoint>(((toMs - fromMs) / stepMs).toInt() + 2)
        var t = fromMs
        while (t <= toMs) {
            points += PkPoint(t, concentrationAt(med, doses, t))
            t += stepMs
        }
        if (points.lastOrNull()?.epochMs != toMs) {
            points += PkPoint(toMs, concentrationAt(med, doses, toMs))
        }
        return PkSeries(med, points)
    }

    fun seriesForAll(
        meds: List<PkMedication>,
        doses: List<PkDose>,
        fromMs: Long,
        toMs: Long,
        stepMinutes: Int = 5,
    ): List<PkSeries> = meds.map { series(it, doses, fromMs, toMs, stepMinutes) }

    /** 현재 상태 요약 — 화면 상단 카드에 표시합니다. */
    fun status(med: PkMedication, doses: List<PkDose>, nowMs: Long): PkStatus {
        val mine = doses.filter { it.medicationId == med.id && it.takenAtEpochMs <= nowMs }
            .sortedBy { it.takenAtEpochMs }
        val last = mine.lastOrNull()
        val now = concentrationAt(med, doses, nowMs)

        if (last == null || now < 0.5) {
            return PkStatus(med, now, PkPhase.NONE, last?.takenAtEpochMs, last?.doseMg, null, null)
        }

        // 앞으로의 곡선을 훑어 최고점과 작용 종료 시점을 찾습니다.
        val stepMs = 60_000L * 5
        val horizonMs = (med.profile.halfLifeHours * LOOKBACK_HALF_LIVES * MS_PER_HOUR).toLong()
        var peakAt = nowMs
        var peakVal = now
        var wearOffAt: Long? = null
        var t = nowMs
        while (t <= nowMs + horizonMs) {
            val c = concentrationAt(med, doses, t)
            if (c > peakVal) {
                peakVal = c
                peakAt = t
            }
            if (wearOffAt == null && c < WEAR_OFF_PERCENT && t > peakAt) wearOffAt = t
            t += stepMs
        }
        // 이미 최고점을 지났다면 과거 구간에서 최고점을 찾습니다.
        if (peakAt == nowMs && peakVal == now) {
            var back = last.takenAtEpochMs
            while (back <= nowMs) {
                val c = concentrationAt(med, doses, back)
                if (c > peakVal) {
                    peakVal = c
                    peakAt = back
                }
                back += stepMs
            }
        }

        val onsetEndMs = last.takenAtEpochMs + med.onsetMinutes * 60_000L
        val ahead = concentrationAt(med, doses, nowMs + stepMs)
        val phase = when {
            nowMs < onsetEndMs -> PkPhase.BEFORE_ONSET
            now < WEAR_OFF_PERCENT -> PkPhase.WORN_OFF
            peakVal > 0 && now >= peakVal * 0.9 -> PkPhase.PEAK
            ahead > now -> PkPhase.RISING
            else -> PkPhase.FALLING
        }

        return PkStatus(
            medication = med,
            percentNow = now,
            phase = phase,
            lastDoseAtEpochMs = last.takenAtEpochMs,
            lastDoseMg = last.doseMg,
            peakAtEpochMs = peakAt,
            wearOffAtEpochMs = wearOffAt,
        )
    }
}
