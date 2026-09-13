package com.panictracker.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panictracker.app.data.MedicationPresets
import com.panictracker.app.data.entity.SymptomType
import com.panictracker.app.ui.Vm
import com.panictracker.app.ui.common.BarEntry
import com.panictracker.app.ui.common.BarSegment
import com.panictracker.app.ui.common.DayBarChart
import com.panictracker.app.ui.common.EmptyChartHint
import com.panictracker.app.ui.common.HorizontalBars
import com.panictracker.app.ui.common.HourHeatmap
import com.panictracker.app.ui.common.LegendChip
import com.panictracker.app.ui.common.SectionCard
import com.panictracker.app.ui.common.StatRow
import com.panictracker.app.ui.common.StatTile
import com.panictracker.app.util.TimeFmt

/** 밤잠·낮잠은 한 막대에 쌓으므로 계열이 둘 — 범례를 반드시 함께 둡니다. */
private val NightColor = Color(0xFF2460B7)
private val NapColor = Color(0xFF9678E6)

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    vm: StatsViewModel = viewModel(factory = Vm.factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sleep = state.sleep
    val symptom = state.symptom

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatsRange.entries.forEach { r ->
                    FilterChip(
                        selected = state.range == r,
                        onClick = { vm.setRange(r) },
                        label = { Text(r.label) },
                    )
                }
            }
        }

        // ── 수면 통계 ────────────────────────────────────────────────────
        item {
            SectionCard(
                title = "수면 통계",
                subtitle = "최근 ${state.range.label} · 밤잠 ${sleep.nightCount}회, 낮잠 ${sleep.napCount}회",
            ) {
                Column {
                    StatRow {
                        StatTile(
                            "평균 수면시간",
                            TimeFmt.duration(sleep.avgTotalSleepMinutes),
                            Modifier.weight(1f),
                        )
                        StatTile(
                            "평균 수면효율",
                            "${sleep.avgEfficiencyPercent}%",
                            Modifier.weight(1f),
                            "잠자리에 머문 시간 대비",
                        )
                    }
                    StatRow {
                        StatTile(
                            "평균 취침",
                            sleep.avgBedTimeMinuteOfDay?.let { TimeFmt.minuteOfDay(it) } ?: "–",
                            Modifier.weight(1f),
                        )
                        StatTile(
                            "평균 기상",
                            sleep.avgWakeTimeMinuteOfDay?.let { TimeFmt.minuteOfDay(it) } ?: "–",
                            Modifier.weight(1f),
                        )
                    }
                    StatRow {
                        StatTile(
                            "밤중 각성",
                            "%.1f회".format(sleep.avgAwakeningCount),
                            Modifier.weight(1f),
                            "깨어 있던 시간 ${TimeFmt.duration(sleep.avgAwakeAfterOnsetMinutes)}",
                        )
                        StatTile(
                            "잠들기까지",
                            TimeFmt.duration(sleep.avgSleepLatencyMinutes),
                            Modifier.weight(1f),
                        )
                    }
                    StatRow {
                        StatTile(
                            "평균 낮잠",
                            if (sleep.napCount > 0) TimeFmt.duration(sleep.avgNapMinutes) else "–",
                            Modifier.weight(1f),
                        )
                        StatTile(
                            "평균 수면의 질",
                            sleep.avgQuality?.let { "%.1f / 5".format(it) } ?: "–",
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "날짜별 수면시간") {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendChip(NightColor, "밤잠")
                        LegendChip(NapColor, "낮잠")
                    }
                    Spacer(Modifier.height(10.dp))
                    if (sleep.days.all { it.totalMinutes == 0 }) {
                        EmptyChartHint("이 기간에 기록된 수면이 없습니다.")
                    } else {
                        val labelEvery = (sleep.days.size / 7).coerceAtLeast(1)
                        DayBarChart(
                            entries = sleep.days.mapIndexed { i, d ->
                                BarEntry(
                                    label = if (i % labelEvery == 0) TimeFmt.dayShort(d.date) else "",
                                    segments = listOf(
                                        BarSegment(d.nightSleepMinutes / 60f, NightColor),
                                        BarSegment(d.napMinutes / 60f, NapColor),
                                    ),
                                )
                            },
                            referenceValue = 7f,
                            referenceLabel = "가로 기준선 = 7시간",
                        )
                    }
                }
            }
        }

        // ── 증상 통계 ────────────────────────────────────────────────────
        item {
            SectionCard(
                title = "증상 통계",
                subtitle = "최근 ${state.range.label} · 총 ${symptom.total}회",
            ) {
                Column {
                    StatRow {
                        StatTile(
                            "하루 평균",
                            "%.1f회".format(symptom.avgPerDay),
                            Modifier.weight(1f),
                        )
                        StatTile(
                            "공황발작",
                            "${symptom.byType[SymptomType.PANIC_ATTACK] ?: 0}회",
                            Modifier.weight(1f),
                        )
                    }
                    StatRow {
                        StatTile(
                            "무증상 연속",
                            "${symptom.symptomFreeStreakDays}일",
                            Modifier.weight(1f),
                            symptom.daysSinceLast?.let { "마지막 기록 ${it}일 전" },
                        )
                        StatTile(
                            "평균 강도",
                            symptom.avgSeverity?.let { "%.1f / 5".format(it) } ?: "–",
                            Modifier.weight(1f),
                            "강도를 적은 기록만 집계",
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "증상 종류별 횟수") {
                HorizontalBars(
                    rows = SymptomType.entries.mapNotNull { t ->
                        val c = symptom.byType[t] ?: return@mapNotNull null
                        // 색은 증상 종류를 따라가며(순위가 아니라), 이름이 항상 옆에 붙습니다.
                        Triple("${t.emoji} ${t.label}", c, Color(MedicationPresets.colorFor(t.ordinal)))
                    }.sortedByDescending { it.second },
                )
            }
        }

        item {
            SectionCard(
                title = "시간대별 증상",
                subtitle = symptom.peakHour?.let { "가장 잦은 시간대: ${it}시" }
                    ?: "아직 기록이 부족합니다.",
            ) {
                HourHeatmap(counts = symptom.byHour)
            }
        }

        item {
            SectionCard(title = "날짜별 증상 횟수") {
                if (symptom.byDay.all { it.count == 0 }) {
                    EmptyChartHint("이 기간에 기록된 증상이 없습니다.")
                } else {
                    val labelEvery = (symptom.byDay.size / 7).coerceAtLeast(1)
                    val barColor = MaterialTheme.colorScheme.primary
                    DayBarChart(
                        entries = symptom.byDay.mapIndexed { i, d ->
                            BarEntry(
                                label = if (i % labelEvery == 0) TimeFmt.dayShort(d.date) else "",
                                segments = listOf(BarSegment(d.count.toFloat(), barColor)),
                            )
                        },
                    )
                }
            }
        }
    }
}
