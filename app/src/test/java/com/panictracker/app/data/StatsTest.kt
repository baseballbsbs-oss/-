package com.panictracker.app.data

import com.panictracker.app.data.entity.SleepAwakening
import com.panictracker.app.data.entity.SleepKind
import com.panictracker.app.data.entity.SleepLog
import com.panictracker.app.data.entity.SleepSession
import com.panictracker.app.data.entity.SymptomLog
import com.panictracker.app.data.entity.SymptomType
import com.panictracker.app.data.repo.SleepStatsCalculator
import com.panictracker.app.data.repo.SymptomStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.systemDefault()

private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
    LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
        .atZone(ZONE).toInstant().toEpochMilli()

class SleepSessionTest {

    private val d1 = LocalDate.of(2026, 3, 10)
    private val d2 = d1.plusDays(1)

    /** 23:00 취침 → 23:20 입면 → 02:00~02:30 깸 → 07:00 완전 기상 → 07:15 잠자리에서 나옴 */
    private val session = SleepSession(
        sleep = SleepLog(
            id = 1,
            kind = SleepKind.NIGHT,
            bedTimeAt = at(d1, 23, 0),
            sleepAt = at(d1, 23, 20),
            finalWakeAt = at(d2, 7, 0),
            getUpAt = at(d2, 7, 15),
            quality = 3,
        ),
        awakenings = listOf(
            SleepAwakening(id = 1, sleepLogId = 1, wokeAt = at(d2, 2, 0), backToSleepAt = at(d2, 2, 30)),
        ),
    )

    @Test
    fun `잠자리에 머문 시간은 취침부터 일어난 시각까지`() {
        assertEquals(8 * 60 + 15, session.timeInBedMinutes)
    }

    @Test
    fun `입면 시간은 취침과 잠든 시각의 차이`() {
        assertEquals(20, session.sleepLatencyMinutes)
    }

    @Test
    fun `실제 수면시간에서 중간에 깬 시간을 뺀다`() {
        // 23:20 → 07:00 은 460분, 여기서 깨어 있던 30분을 뺍니다.
        assertEquals(430, session.totalSleepMinutes)
        assertEquals(30, session.awakeAfterOnsetMinutes)
    }

    @Test
    fun `수면 효율은 실제 수면시간 나누기 잠자리에 머문 시간`() {
        assertEquals((430 * 100.0 / 495).toInt(), session.efficiencyPercent)
    }

    @Test
    fun `다시 잠들지 않은 구간은 깨어 있던 시간에 포함하지 않는다`() {
        val open = SleepAwakening(sleepLogId = 1, wokeAt = at(d2, 3, 0), backToSleepAt = null)
        assertEquals(0, open.awakeMinutes)
    }

    @Test
    fun `잠든 시각을 적지 않으면 취침 시각부터 잔 것으로 본다`() {
        val noOnset = session.copy(sleep = session.sleep.copy(sleepAt = null))
        assertEquals(450, noOnset.totalSleepMinutes) // 23:00 → 07:00 (480) − 30
    }
}

class SleepStatsCalculatorTest {

    private val start = LocalDate.of(2026, 3, 10)
    private val end = start.plusDays(6)

    private fun night(day: LocalDate, bedHour: Int, wakeHour: Int, awake: Int = 0) = SleepSession(
        sleep = SleepLog(
            id = day.toEpochDay(),
            kind = SleepKind.NIGHT,
            bedTimeAt = at(day, bedHour),
            finalWakeAt = at(day.plusDays(1), wakeHour),
        ),
        awakenings = if (awake == 0) emptyList() else listOf(
            SleepAwakening(
                id = day.toEpochDay(),
                sleepLogId = day.toEpochDay(),
                wokeAt = at(day.plusDays(1), 3),
                backToSleepAt = at(day.plusDays(1), 3) + awake * 60_000L,
            ),
        ),
    )

    @Test
    fun `기록이 없으면 빈 통계를 돌려준다`() {
        val s = SleepStatsCalculator.compute(emptyList(), start, end)
        assertEquals(0, s.nightCount)
        assertEquals(0, s.avgTotalSleepMinutes)
        assertNull(s.avgBedTimeMinuteOfDay)
    }

    @Test
    fun `밤잠 평균과 각성 횟수를 집계한다`() {
        val sessions = listOf(
            night(start, 23, 7),                 // 8시간
            night(start.plusDays(1), 23, 7, 30), // 8시간 − 30분
            night(start.plusDays(2), 23, 7),     // 8시간
        )
        val s = SleepStatsCalculator.compute(sessions, start, end)
        assertEquals(3, s.nightCount)
        assertEquals((480 + 450 + 480) / 3, s.avgTotalSleepMinutes)
        assertEquals(1.0 / 3, s.avgAwakeningCount, 1e-9)
        assertEquals(10, s.avgAwakeAfterOnsetMinutes)
    }

    @Test
    fun `밤잠은 깬 날짜에 묶이고 낮잠은 시작한 날짜에 묶인다`() {
        val sessions = listOf(
            night(start, 23, 7), // start 23시 취침 → start+1 에 기상
            SleepSession(
                sleep = SleepLog(
                    id = 999,
                    kind = SleepKind.NAP,
                    bedTimeAt = at(start.plusDays(1), 14),
                    finalWakeAt = at(start.plusDays(1), 15),
                ),
            ),
        )
        val s = SleepStatsCalculator.compute(sessions, start, end)
        val day2 = s.days.first { it.date == start.plusDays(1) }
        assertEquals(480, day2.nightSleepMinutes)
        assertEquals(60, day2.napMinutes)
        assertEquals(0, s.days.first { it.date == start }.nightSleepMinutes)
        assertEquals(1, s.napCount)
        assertEquals(60, s.avgNapMinutes)
    }

    @Test
    fun `날짜 구간의 모든 날이 결과에 들어간다`() {
        val s = SleepStatsCalculator.compute(listOf(night(start, 23, 7)), start, end)
        assertEquals(7, s.days.size)
        assertEquals(start, s.days.first().date)
        assertEquals(end, s.days.last().date)
    }

    /** 자정을 넘나드는 취침 시각은 산술평균으로 구하면 정오가 나옵니다 — 원형 평균이어야 합니다. */
    @Test
    fun `평균 취침 시각은 자정을 넘어도 올바르다`() {
        // 23:50 과 00:10 의 평균은 12:00 이 아니라 00:00
        assertEquals(0, SleepStatsCalculator.circularMeanMinutes(listOf(23 * 60 + 50, 10)))
        // 23:00 과 01:00 의 평균은 00:00
        assertEquals(0, SleepStatsCalculator.circularMeanMinutes(listOf(23 * 60, 60)))
        // 자정을 넘지 않는 평범한 경우도 그대로
        assertEquals(14 * 60, SleepStatsCalculator.circularMeanMinutes(listOf(13 * 60, 15 * 60)))
        assertNull(SleepStatsCalculator.circularMeanMinutes(emptyList()))
    }

    @Test
    fun `평균 취침과 기상 시각을 계산한다`() {
        val sessions = listOf(night(start, 23, 7), night(start.plusDays(1), 23, 7))
        val s = SleepStatsCalculator.compute(sessions, start, end)
        assertEquals(23 * 60, s.avgBedTimeMinuteOfDay!!, )
        assertEquals(7 * 60, s.avgWakeTimeMinuteOfDay!!)
    }
}

class SymptomStatsCalculatorTest {

    private val today = LocalDate.of(2026, 3, 16)
    private val start = today.minusDays(6)

    private fun log(day: LocalDate, hour: Int, type: SymptomType, severity: Int? = null) =
        SymptomLog(
            id = day.toEpochDay() * 100 + hour,
            type = type,
            occurredAt = at(day, hour),
            severity = severity,
        )

    @Test
    fun `기록이 없으면 구간의 모든 날이 0 으로 채워진다`() {
        val s = SymptomStatsCalculator.compute(emptyList(), start, today, today)
        assertEquals(0, s.total)
        assertEquals(7, s.byDay.size)
        assertTrue(s.byDay.all { it.count == 0 })
        assertEquals(List(24) { 0 }, s.byHour)
        assertNull(s.peakHour)
    }

    @Test
    fun `종류별 시간대별 날짜별로 집계한다`() {
        val logs = listOf(
            log(today, 3, SymptomType.PANIC_ATTACK, 5),
            log(today, 3, SymptomType.ANXIETY, 3),
            log(today.minusDays(1), 3, SymptomType.ANXIETY, 2),
            log(today.minusDays(2), 14, SymptomType.PALPITATIONS),
        )
        val s = SymptomStatsCalculator.compute(logs, start, today, today)

        assertEquals(4, s.total)
        assertEquals(2, s.byType[SymptomType.ANXIETY])
        assertEquals(1, s.byType[SymptomType.PANIC_ATTACK])
        assertNull(s.byType[SymptomType.COLD_SWEAT]) // 0회인 종류는 빠집니다

        assertEquals(3, s.byHour[3])
        assertEquals(1, s.byHour[14])
        assertEquals(3, s.peakHour)

        assertEquals(2, s.byDay.last().count)
        assertEquals(1, s.byDay.last().panicCount)
        assertEquals(4.0 / 7, s.avgPerDay, 1e-9)

        // 강도를 적은 3건만 평균에 들어갑니다.
        assertEquals((5 + 3 + 2) / 3.0, s.avgSeverity!!, 1e-9)
    }

    @Test
    fun `무증상 연속 일수는 오늘부터 거슬러 센다`() {
        val logs = listOf(log(today.minusDays(3), 10, SymptomType.ANXIETY))
        val s = SymptomStatsCalculator.compute(logs, start, today, today)
        assertEquals(3, s.symptomFreeStreakDays)
        assertEquals(3, s.daysSinceLast)
    }

    @Test
    fun `오늘 증상이 있으면 연속 일수는 0`() {
        val logs = listOf(log(today, 10, SymptomType.ANXIETY))
        val s = SymptomStatsCalculator.compute(logs, start, today, today)
        assertEquals(0, s.symptomFreeStreakDays)
        assertEquals(0, s.daysSinceLast)
    }
}
