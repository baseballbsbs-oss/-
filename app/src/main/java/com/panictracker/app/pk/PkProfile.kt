package com.panictracker.app.pk

import kotlin.math.exp
import kotlin.math.ln

/**
 * 1-구획 · 1차 흡수(경구) 약동학 모델.
 *
 * 혈중농도 곡선은 Bateman 식을 따릅니다.
 *
 *     C(t) = (F · D · ka) / (Vd · (ka − ke)) · (e^(−ke·t) − e^(−ka·t))
 *
 * 일반 사용자는 F(생체이용률)·Vd(분포용적)를 알 수 없으므로, 이 앱은 절대 농도(ng/mL)
 * 대신 **상대 농도(%)** 를 사용합니다. 기준 용량 1회를 복용했을 때의 최고 농도(Cmax)를
 * 100 %로 두고, 그에 대한 비율로 표시합니다. 모델이 용량에 대해 선형이므로 다른 용량과
 * 여러 번의 투약은 단순 비례·중첩(superposition)으로 계산할 수 있습니다.
 *
 * 입력은 사용자가 첨부문서에서 쉽게 찾을 수 있는 두 값 — 반감기(t½)와 최고농도도달시간
 * (Tmax) — 이며, 여기서 소실속도상수 ke 와 흡수속도상수 ka 를 유도합니다.
 *
 *     ke = ln2 / t½
 *     Tmax = ln(ka / ke) / (ka − ke)      → ka 는 수치해석으로 역산
 */
class PkProfile private constructor(
    /** 소실속도상수 (1/시간). */
    val ke: Double,
    /** 흡수속도상수 (1/시간). */
    val ka: Double,
    /** 실제 모델에 반영된 Tmax (입력값이 물리적으로 불가능하면 보정됨, 시간). */
    val effectiveTmaxHours: Double,
    /** 반감기 (시간). */
    val halfLifeHours: Double,
    /** 단위용량 곡선의 최고값 — 정규화에 사용. */
    private val unitCmax: Double,
) {

    /**
     * 기준 용량 1회 투약 후 [hoursSinceDose] 시점의 상대 농도.
     * 최고점에서 정확히 1.0 이 되도록 정규화되어 있습니다. 투약 전(t<0)은 0.
     */
    fun shape(hoursSinceDose: Double): Double {
        if (hoursSinceDose <= 0.0) return 0.0
        return unitCurve(hoursSinceDose) / unitCmax
    }

    private fun unitCurve(t: Double): Double =
        if (isFluxLimited) {
            // ka ≈ ke 인 특수해: C(t) ∝ t · e^(−ke·t)
            t * exp(-ke * t)
        } else {
            ka / (ka - ke) * (exp(-ke * t) - exp(-ka * t))
        }

    private val isFluxLimited: Boolean get() = kotlin.math.abs(ka - ke) < 1e-9

    /**
     * 단회 투약 후 농도가 최고치의 [fraction] 까지 떨어지는 시점(시간).
     * 소실상(Tmax 이후) 에서만 탐색합니다.
     */
    fun hoursToDecayTo(fraction: Double): Double {
        require(fraction in 0.0001..0.9999) { "fraction 은 0 과 1 사이여야 합니다." }
        var lo = effectiveTmaxHours
        var hi = effectiveTmaxHours + halfLifeHours * 20.0
        repeat(200) {
            val mid = (lo + hi) / 2.0
            if (shape(mid) > fraction) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }

    companion object {
        private const val LN2 = 0.6931471805599453

        /**
         * Tmax 는 물리적으로 1/ke ( = t½ / ln2 ≈ 1.4427 · t½ ) 보다 작아야 ka > ke 인 해가
         * 존재합니다. 사용자가 그보다 큰 값을 넣으면 이 비율까지 자동으로 당겨 보정합니다.
         */
        private const val MAX_TMAX_RATIO = 0.97

        /**
         * @param halfLifeHours 반감기 (시간). 0 보다 커야 합니다.
         * @param tmaxHours     최고농도도달시간 (시간). 0 보다 커야 하며, 너무 크면 보정됩니다.
         */
        fun of(halfLifeHours: Double, tmaxHours: Double): PkProfile {
            require(halfLifeHours > 0.0) { "반감기는 0보다 커야 합니다." }
            require(tmaxHours > 0.0) { "Tmax 는 0보다 커야 합니다." }

            val ke = LN2 / halfLifeHours
            val tmaxCeiling = MAX_TMAX_RATIO / ke
            val tmax = tmaxHours.coerceAtMost(tmaxCeiling)

            val ka = solveKa(ke, tmax)
            val unitCmax = if (kotlin.math.abs(ka - ke) < 1e-9) {
                tmax * exp(-ke * tmax)
            } else {
                ka / (ka - ke) * (exp(-ke * tmax) - exp(-ka * tmax))
            }
            return PkProfile(ke, ka, tmax, halfLifeHours, unitCmax)
        }

        /**
         * f(ka) = ln(ka/ke)/(ka−ke) − Tmax = 0 을 이분법으로 풉니다.
         * f 는 (ke, ∞) 에서 단조감소하며 ka→ke⁺ 에서 1/ke − Tmax (> 0), ka→∞ 에서 −Tmax (< 0)
         * 이므로 해가 유일하게 존재합니다.
         */
        private fun solveKa(ke: Double, tmax: Double): Double {
            var lo = ke * 1.0000001
            var hi = ke * 2.0
            var guard = 0
            while (tmaxFor(ke, hi) > tmax && guard++ < 200) hi *= 2.0
            repeat(200) {
                val mid = (lo + hi) / 2.0
                if (tmaxFor(ke, mid) > tmax) lo = mid else hi = mid
            }
            return (lo + hi) / 2.0
        }

        private fun tmaxFor(ke: Double, ka: Double): Double = ln(ka / ke) / (ka - ke)
    }
}
