package com.panictracker.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panictracker.app.data.entity.SymptomType
import com.panictracker.app.pk.PkPhase
import com.panictracker.app.pk.PkStatus
import com.panictracker.app.ui.Vm
import com.panictracker.app.ui.common.LegendChip
import com.panictracker.app.ui.common.PkChart
import com.panictracker.app.ui.common.ScrubReading
import com.panictracker.app.ui.common.SectionCard
import com.panictracker.app.util.TimeFmt
import kotlin.math.roundToInt

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenMedications: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel(factory = Vm.factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var scrub by remember { mutableStateOf<ScrubReading?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "예상 혈중 약물 농도",
                subtitle = "기준 용량 1회 복용 시 최고 농도를 100 %로 둔 상대값입니다. " +
                    "이전 투약의 남은 양이 이미 더해져 있습니다.",
                trailing = {
                    Text(
                        TimeFmt.time(state.nowMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            ) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TimeWindow.entries.forEach { w ->
                            FilterChip(
                                selected = state.window == w,
                                onClick = { vm.setWindow(w) },
                                label = { Text(w.label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    if (state.series.isEmpty()) {
                        EmptyMedsHint(onOpenMedications)
                    } else {
                        PkChart(
                            series = state.series,
                            doseMarkers = state.doseMarkers,
                            fromMs = state.fromMs,
                            toMs = state.toMs,
                            nowMs = state.nowMs,
                            onScrub = { scrub = it },
                        )
                        Spacer(Modifier.height(8.dp))

                        // 색만으로 계열을 구분하지 않도록 이름이 붙은 범례를 항상 둡니다.
                        val reading = scrub
                        Text(
                            text = if (reading != null)
                                "${TimeFmt.smart(reading.epochMs)} 시점"
                            else "지금 (${TimeFmt.time(state.nowMs)})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            state.series.forEach { s ->
                                val value = reading?.entries?.firstOrNull { it.first === s }?.second
                                    ?: state.statuses.firstOrNull { it.medication.id == s.medication.id }?.percentNow
                                    ?: 0.0
                                LegendChip(
                                    color = Color(s.medication.colorArgb),
                                    label = s.medication.label,
                                    trailing = "${value.roundToInt()}%",
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "그래프를 손가락으로 짚으면 그 시각의 값을 읽을 수 있습니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (state.statuses.isNotEmpty()) {
            item {
                SectionCard(title = "약효 상태") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.statuses.forEach { StatusRow(it) }
                    }
                }
            }
        }

        if (state.medications.isNotEmpty()) {
            item {
                SectionCard(
                    title = "지금 복용 기록",
                    subtitle = "누르면 현재 시각으로 바로 기록됩니다.",
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.medications.forEach { m ->
                            Button(
                                onClick = { vm.logDoseNow(m.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(m.colorArgb),
                                ),
                            ) {
                                Icon(Icons.Default.Medication, contentDescription = null)
                                Text("  ${m.displayName} ${fmtMg(m.doseMg)}")
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "오늘 요약",
                subtitle = TimeFmt.dateTimeFull(state.nowMs).substringBeforeLast(" "),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val counts = state.todaySymptoms.groupingBy { it.type }.eachCount()
                    if (counts.isEmpty()) {
                        Text(
                            "오늘 기록된 증상이 없습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SymptomType.entries.forEach { t ->
                                val c = counts[t] ?: return@forEach
                                AssistChip(
                                    onClick = {},
                                    label = { Text("${t.emoji} ${t.label} ${c}회") },
                                )
                            }
                        }
                    }
                    Text(
                        "오늘 복용 ${state.todayDoseCount}회",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (state.recentDoses.isNotEmpty()) {
            item {
                Text(
                    "최근 투약",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
            items(state.recentDoses, key = { it.id }) { d ->
                val med = state.medications.firstOrNull { it.id == d.medicationId }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "${med?.displayName ?: "삭제된 약"} ${fmtMg(d.doseMg)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            TimeFmt.smart(d.takenAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.deleteDose(d) }) { Text("삭제") }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(st: PkStatus) {
    val (text, color) = when (st.phase) {
        PkPhase.NONE -> "최근 복용 없음" to MaterialTheme.colorScheme.onSurfaceVariant
        PkPhase.BEFORE_ONSET -> "발현 전" to MaterialTheme.colorScheme.tertiary
        PkPhase.RISING -> "농도 상승 중" to MaterialTheme.colorScheme.primary
        PkPhase.PEAK -> "최고 농도 부근" to MaterialTheme.colorScheme.primary
        PkPhase.FALLING -> "농도 감소 중" to MaterialTheme.colorScheme.secondary
        PkPhase.WORN_OFF -> "작용 종료" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendChip(
                color = Color(st.medication.colorArgb),
                label = st.medication.label,
            )
            Text(
                "${st.percentNow.roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        val detail = buildList {
            st.lastDoseAtEpochMs?.let {
                add("마지막 복용 ${TimeFmt.smart(it)}")
            }
            if (st.phase == PkPhase.BEFORE_ONSET) {
                val onsetAt = (st.lastDoseAtEpochMs ?: 0L) + st.medication.onsetMinutes * 60_000L
                add("발현 예상 ${TimeFmt.time(onsetAt)}")
            }
            if (st.phase == PkPhase.RISING || st.phase == PkPhase.BEFORE_ONSET) {
                st.peakAtEpochMs?.let { add("최고 예상 ${TimeFmt.time(it)}") }
            }
            if (st.phase != PkPhase.NONE && st.phase != PkPhase.WORN_OFF) {
                st.wearOffAtEpochMs?.let { add("작용 종료 예상 ${TimeFmt.smart(it)}") }
            }
        }
        if (detail.isNotEmpty()) {
            Text(
                detail.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyMedsHint(onOpenMedications: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            "복용 중인 약을 먼저 등록하면 농도 곡선이 그려집니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenMedications) { Text("약 등록하러 가기") }
    }
}
