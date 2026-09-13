package com.panictracker.app.data.repo

import com.panictracker.app.data.entity.SleepKind
import com.panictracker.app.data.entity.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** 하루치 수면 합계 — 막대그래프용. */
data class SleepDay(
    val date: LocalDate,
    val nightSleepMinutes: Int,
    val napMinutes: Int,
    val efficiencyPercent: Int,
) {
    val totalMinutes: Int get() = nightSleepMinutes + napMinutes
}

data class SleepStats(
    val nightCount: Int,
    val napCount: Int,
    val avgTotalSleepMinutes: Int,
    val avgTimeInBedMinutes: Int,
    val avgEfficiencyPercent: Int,
    val avgSleepLatencyMinutes: Int,
    val avgAwakeningCount: Double,
    val avgAwakeAfterOnsetMinutes: Int,
    val avgNapMinutes: Int,
    /** 평균 취침 시각 — 자정을 넘나들므로 원형 평균으로 계산합니다. 분 단위(0~1439). */
    val avgBedTimeMinuteOfDay: Int?,
    val avgWakeTimeMinuteOfDay: Int?,
    val avgQuality: Double?,
    val days: List<SleepDay>,
) {
    companion object {
        val EMPTY = SleepStats(
            0, 0, 0, 0, 0, 0, 0.0, 0, 0, null, null, null, emptyList(),
        )
    }
}

object SleepStatsCalculator {

    fun compute(
        sessions: List<SleepSession>,
        rangeStart: LocalDate,
        rangeEndInclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SleepStats {
        if (sessions.isEmpty()) return SleepStats.EMPTY

        val nights = sessions.filter { it.sleep.kind == SleepKind.NIGHT }
        val naps = sessions.filter { it.sleep.kind == SleepKind.NAP }

        val qualities = sessions.mapNotNull { it.sleep.quality }

        // 밤잠은 "깬 날"을 기준으로 묶습니다 — 새벽 2시에 자도 그날 아침의 수면입니다.
        val byDate = LinkedHashMap<LocalDate, MutableList<SleepSession>>()
        var d = rangeStart
        while (!d.isAfter(rangeEndInclusive)) {
            byDate[d] = mutableListOf()
            d = d.plusDays(1)
        }
        for (s in sessions) {
            val anchor = when (s.sleep.kind) {
                SleepKind.NIGHT -> localDate(s.sleep.finalWakeAt, zone)
                SleepKind.NAP -> localDate(s.sleep.bedTimeAt, zone)
            }
            byDate.getOrPut(anchor) { mutableListOf() }.add(s)
        }

        val days = byDate.entries
            .filter { !it.key.isBefore(rangeStart) && !it.key.isAfter(rangeEndInclusive) }
            .sortedBy { it.key }
            .map { (date, list) ->
                val night = list.filter { it.sleep.kind == SleepKind.NIGHT }
                val nap = list.filter { it.sleep.kind == SleepKind.NAP }
                SleepDay(
                    date = date,
                    nightSleepMinutes = night.sumOf { it.totalSleepMinutes },
                    napMinutes = nap.sumOf { it.totalSleepMinutes },
                    efficiencyPercent = night.map { it.efficiencyPercent }.averageIntOrZero(),
                )
            }

        return SleepStats(
            nightCount = nights.size,
            napCount = naps.size,
            avgTotalSleepMinutes = nights.map { it.totalSleepMinutes }.averageIntOrZero(),
            avgTimeInBedMinutes = nights.map { it.timeInBedMinutes }.averageIntOrZero(),
            avgEfficiencyPercent = nights.map { it.efficiencyPercent }.averageIntOrZero(),
            avgSleepLatencyMinutes = nights.map { it.sleepLatencyMinutes }.averageIntOrZero(),
            avgAwakeningCount = if (nights.isEmpty()) 0.0
            else nights.sumOf { it.awakeningCount }.toDouble() / nights.size,
            avgAwakeAfterOnsetMinutes = nights.map { it.awakeAfterOnsetMinutes }.averageIntOrZero(),
            avgNapMinutes = naps.map { it.totalSleepMinutes }.averageIntOrZero(),
            avgBedTimeMinuteOfDay = circularMeanMinutes(nights.map { minuteOfDay(it.sleep.bedTimeAt, zone) }),
            avgWakeTimeMinuteOfDay = circularMeanMinutes(nights.map { minuteOfDay(it.sleep.finalWakeAt, zone) }),
            avgQuality = if (qualities.isEmpty()) null else qualities.average(),
            days = days,
        )
    }

    private fun List<Int>.averageIntOrZero(): Int = if (isEmpty()) 0 else (sum().toDouble() / size).toInt()

    private fun localDate(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    private fun minuteOfDay(epochMs: Long, zone: ZoneId): Int {
        val t = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
        return t.hour * 60 + t.minute
    }

    /**
     * 시각의 평균은 산술평균으로 구하면 안 됩니다 — 23:50 과 00:10 의 평균은 12:00 이 아니라
     * 00:00 이어야 하기 때문입니다. 각 시각을 원 위의 단위벡터로 바꿔 평균낸 뒤 각도를 되돌립니다.
     */
    fun circularMeanMinutes(minutes: List<Int>): Int? {
        if (minutes.isEmpty()) return null
        val twoPi = 2 * Math.PI
        var sx = 0.0
        var sy = 0.0
        for (m in minutes) {
            val a = m / 1440.0 * twoPi
            sx += cos(a)
            sy += sin(a)
        }
        if (kotlin.math.hypot(sx, sy) < 1e-9) return null // 방향이 상쇄됨 — 평균이 의미 없음
        var angle = atan2(sy / minutes.size, sx / minutes.size)
        if (angle < 0) angle += twoPi
        // 반올림 후 1440 으로 나눈 나머지를 취합니다. 평균이 정확히 자정일 때 부동소수점
        // 오차로 각도가 2π 바로 아래에 떨어져 23:59 로 새는 것을 막습니다.
        return (Math.round((angle / twoPi) * 1440.0).toInt() % 1440 + 1440) % 1440
    }
}
