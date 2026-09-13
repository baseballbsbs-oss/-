package com.panictracker.app.data.repo

import com.panictracker.app.data.entity.SymptomLog
import com.panictracker.app.data.entity.SymptomType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SymptomDay(val date: LocalDate, val count: Int, val panicCount: Int)

data class SymptomStats(
    val total: Int,
    val byType: Map<SymptomType, Int>,
    /** 0~23 시 각각의 발생 횟수 (항상 길이 24). */
    val byHour: List<Int>,
    val byDay: List<SymptomDay>,
    val avgSeverity: Double?,
    val avgPerDay: Double,
    /** 가장 자주 증상이 생기는 시간대. 기록이 없으면 null. */
    val peakHour: Int?,
    /** 마지막 증상 이후 무증상으로 지난 일수. 기록이 전혀 없으면 null. */
    val daysSinceLast: Int?,
    /** 오늘부터 거슬러 올라가 증상이 하나도 없었던 연속 일수. */
    val symptomFreeStreakDays: Int,
) {
    companion object {
        val EMPTY = SymptomStats(0, emptyMap(), List(24) { 0 }, emptyList(), null, 0.0, null, null, 0)
    }
}

object SymptomStatsCalculator {

    fun compute(
        logs: List<SymptomLog>,
        rangeStart: LocalDate,
        rangeEndInclusive: LocalDate,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SymptomStats {
        val dayCount = (rangeEndInclusive.toEpochDay() - rangeStart.toEpochDay() + 1)
            .toInt().coerceAtLeast(1)
        if (logs.isEmpty()) return SymptomStats.EMPTY.copy(byDay = emptyDays(rangeStart, dayCount))

        val byType = SymptomType.entries.associateWith { t -> logs.count { it.type == t } }
            .filterValues { it > 0 }

        val byHour = MutableList(24) { 0 }
        for (l in logs) byHour[hourOf(l.occurredAt, zone)]++

        val grouped = logs.groupBy { localDate(it.occurredAt, zone) }
        val byDay = (0 until dayCount).map { offset ->
            val date = rangeStart.plusDays(offset.toLong())
            val onDate = grouped[date].orEmpty()
            SymptomDay(
                date = date,
                count = onDate.size,
                panicCount = onDate.count { it.type == SymptomType.PANIC_ATTACK },
            )
        }

        val severities = logs.mapNotNull { it.severity }
        val lastAt = logs.maxOf { it.occurredAt }
        val lastDate = localDate(lastAt, zone)

        var streak = 0
        var cursor = today
        while (grouped[cursor].isNullOrEmpty() && streak < 3650) {
            streak++
            cursor = cursor.minusDays(1)
        }

        return SymptomStats(
            total = logs.size,
            byType = byType,
            byHour = byHour,
            byDay = byDay,
            avgSeverity = if (severities.isEmpty()) null else severities.average(),
            avgPerDay = logs.size.toDouble() / dayCount,
            peakHour = byHour.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index,
            daysSinceLast = (today.toEpochDay() - lastDate.toEpochDay()).toInt().coerceAtLeast(0),
            symptomFreeStreakDays = streak,
        )
    }

    private fun emptyDays(start: LocalDate, count: Int) =
        (0 until count).map { SymptomDay(start.plusDays(it.toLong()), 0, 0) }

    private fun localDate(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    private fun hourOf(epochMs: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(epochMs).atZone(zone).hour
}
