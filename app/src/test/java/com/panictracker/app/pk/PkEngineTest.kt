package com.panictracker.app.pk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PkProfileTest {

    /** 역산한 ka 를 Tmax 식에 다시 넣으면 입력한 Tmax 가 나와야 합니다. */
    @Test
    fun `ka 역산 결과가 Tmax 를 재현한다`() {
        val cases = listOf(
            12.0 to 1.0,   // 알프라졸람 계열
            6.0 to 0.75,   // 로라제팜 계열 (짧은 Tmax)
            30.0 to 4.0,   // 에스시탈로프람 계열
            2.0 to 0.5,
        )
        for ((halfLife, tmax) in cases) {
            val p = PkProfile.of(halfLife, tmax)
            assertTrue("ka 는 ke 보다 커야 한다", p.ka > p.ke)
            val reconstructed = kotlin.math.ln(p.ka / p.ke) / (p.ka - p.ke)
            assertEquals("t½=$halfLife Tmax=$tmax", tmax, reconstructed, 1e-6)
        }
    }

    /** 곡선의 최고점이 Tmax 에서 정확히 1.0 이어야 합니다 (정규화 검증). */
    @Test
    fun `곡선은 Tmax 에서 최고값 1_0 을 가진다`() {
        val p = PkProfile.of(halfLifeHours = 12.0, tmaxHours = 1.0)
        assertEquals(1.0, p.shape(1.0), 1e-9)

        var maxV = 0.0
        var maxT = 0.0
        var t = 0.0
        while (t < 48.0) {
            val v = p.shape(t)
            if (v > maxV) { maxV = v; maxT = t }
            t += 0.001
        }
        assertEquals(1.0, maxV, 1e-6)
        assertEquals(1.0, maxT, 0.01)
    }

    /** 소실상에서는 반감기마다 농도가 절반이 되어야 합니다. */
    @Test
    fun `소실상에서 반감기마다 농도가 절반이 된다`() {
        val halfLife = 12.0
        val p = PkProfile.of(halfLifeHours = halfLife, tmaxHours = 1.0)
        // 흡수가 사실상 끝난 뒤(Tmax + 여유)에서 비교합니다.
        val t0 = 12.0
        val c0 = p.shape(t0)
        val c1 = p.shape(t0 + halfLife)
        assertEquals(0.5, c1 / c0, 0.01)
    }

    @Test
    fun `투약 전에는 농도가 0 이다`() {
        val p = PkProfile.of(12.0, 1.0)
        assertEquals(0.0, p.shape(-1.0), 0.0)
        assertEquals(0.0, p.shape(0.0), 0.0)
    }

    /** Tmax 가 물리적 한계를 넘으면 보정되고, 예외 없이 유효한 곡선을 만들어야 합니다. */
    @Test
    fun `비현실적인 Tmax 는 보정된다`() {
        val p = PkProfile.of(halfLifeHours = 2.0, tmaxHours = 10.0)
        assertTrue(p.effectiveTmaxHours < 10.0)
        assertTrue(p.effectiveTmaxHours > 0.0)
        assertEquals(1.0, p.shape(p.effectiveTmaxHours), 1e-6)
    }

    @Test
    fun `hoursToDecayTo 는 해당 농도가 되는 시각을 찾는다`() {
        val p = PkProfile.of(12.0, 1.0)
        val t = p.hoursToDecayTo(0.25)
        assertEquals(0.25, p.shape(t), 1e-4)
        assertTrue(t > p.effectiveTmaxHours)
    }
}

class PkEngineTest {

    private val hour = 3_600_000L
    private val med = PkMedication(
        id = 1L,
        label = "자낙스",
        ingredient = "알프라졸람",
        profile = PkProfile.of(halfLifeHours = 12.0, tmaxHours = 1.0),
        referenceDoseMg = 0.25,
        onsetMinutes = 20,
        durationHours = 6.0,
        colorArgb = 0xFF4C7DF0.toInt(),
    )
    private val t0 = 1_700_000_000_000L

    @Test
    fun `기준 용량 단회 투약의 최고 농도는 100 퍼센트`() {
        val doses = listOf(PkDose(1L, t0, 0.25))
        val peak = PkEngine.concentrationAt(med, doses, t0 + hour)
        assertEquals(100.0, peak, 1e-6)
    }

    @Test
    fun `용량이 2배면 농도도 2배 - 선형성`() {
        val single = PkEngine.concentrationAt(med, listOf(PkDose(1L, t0, 0.25)), t0 + hour)
        val double = PkEngine.concentrationAt(med, listOf(PkDose(1L, t0, 0.5)), t0 + hour)
        assertEquals(2 * single, double, 1e-9)
    }

    /** 핵심: 이전 투약이 남아 있는 상태에서 다시 복용하면 농도가 누적되어야 합니다. */
    @Test
    fun `연속 투약은 중첩되어 농도가 누적된다`() {
        val doses = listOf(PkDose(1L, t0, 0.25), PkDose(1L, t0 + 6 * hour, 0.25))
        val afterSecondPeak = PkEngine.concentrationAt(med, doses, t0 + 7 * hour)
        val singleOnly = PkEngine.concentrationAt(med, listOf(PkDose(1L, t0 + 6 * hour, 0.25)), t0 + 7 * hour)
        assertTrue("중첩 농도가 단회보다 높아야 한다", afterSecondPeak > singleOnly)
        // 첫 투약의 6시간 뒤 잔류분이 정확히 더해져야 합니다.
        val residual = PkEngine.concentrationAt(med, listOf(PkDose(1L, t0, 0.25)), t0 + 7 * hour)
        assertEquals(singleOnly + residual, afterSecondPeak, 1e-9)
    }

    @Test
    fun `다른 약물의 투약은 섞이지 않는다`() {
        val other = PkDose(medicationId = 99L, takenAtEpochMs = t0, doseMg = 10.0)
        assertEquals(0.0, PkEngine.concentrationAt(med, listOf(other), t0 + hour), 0.0)
    }

    @Test
    fun `series 는 요청한 구간을 채우고 곡선 모양을 유지한다`() {
        val doses = listOf(PkDose(1L, t0, 0.25))
        val s = PkEngine.series(med, doses, t0 - 2 * hour, t0 + 24 * hour, stepMinutes = 5)
        assertEquals(t0 - 2 * hour, s.points.first().epochMs)
        assertEquals(t0 + 24 * hour, s.points.last().epochMs)
        assertEquals(0.0, s.points.first().percent, 0.0)
        assertEquals(100.0, s.maxPercent, 0.5)
    }

    @Test
    fun `발현시간 전에는 BEFORE_ONSET 상태`() {
        val doses = listOf(PkDose(1L, t0, 0.25))
        val st = PkEngine.status(med, doses, t0 + 10 * 60_000L) // 10분 후, onset 20분
        assertEquals(PkPhase.BEFORE_ONSET, st.phase)
    }

    @Test
    fun `최고점 부근에서는 PEAK 상태이고 최고 시각을 알려준다`() {
        val doses = listOf(PkDose(1L, t0, 0.25))
        val st = PkEngine.status(med, doses, t0 + hour)
        assertEquals(PkPhase.PEAK, st.phase)
        assertEquals(100.0, st.percentNow, 1e-6)
        assertTrue(abs(st.peakAtEpochMs!! - (t0 + hour)) <= 5 * 60_000L)
    }

    @Test
    fun `소실 구간에서는 FALLING 이고 작용 종료 시각을 예측한다`() {
        val doses = listOf(PkDose(1L, t0, 0.25))
        val st = PkEngine.status(med, doses, t0 + 5 * hour)
        assertEquals(PkPhase.FALLING, st.phase)
        val wearOff = st.wearOffAtEpochMs
        assertTrue("작용 종료 시각이 예측되어야 한다", wearOff != null)
        val pct = PkEngine.concentrationAt(med, doses, wearOff!!)
        assertTrue("종료 시점 농도는 역치 부근이어야 한다", pct <= PkEngine.WEAR_OFF_PERCENT + 1.0)
    }

    @Test
    fun `투약 기록이 없으면 NONE 상태`() {
        val st = PkEngine.status(med, emptyList(), t0)
        assertEquals(PkPhase.NONE, st.phase)
        assertEquals(0.0, st.percentNow, 0.0)
    }

    @Test
    fun `아주 오래된 투약은 현재 농도에 기여하지 않는다`() {
        val old = PkDose(1L, t0 - 500 * hour, 0.25)
        assertEquals(0.0, PkEngine.concentrationAt(med, listOf(old), t0), 0.0)
    }
}
