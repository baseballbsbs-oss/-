package com.panictracker.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.panictracker.app.data.MedicationPreset
import com.panictracker.app.data.MedicationPresets
import com.panictracker.app.data.entity.Medication
import com.panictracker.app.data.entity.SymptomType
import com.panictracker.app.pk.PkPhase
import com.panictracker.app.pk.PkStatus
import com.panictracker.app.ui.Vm
import com.panictracker.app.ui.common.LegendChip
import com.panictracker.app.ui.common.PkChart
import com.panictracker.app.ui.common.ScrubReading
import com.panictracker.app.ui.common.SectionCard
import com.panictracker.app.ui.med.MedicationEditDialog
import com.panictracker.app.util.TimeFmt
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenMedications: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel(factory = Vm.factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var scrub by remember { mutableStateOf<ScrubReading?>(null) }
    // 약 추가 다이얼로그 — 이 화면에서 바로 열어, 약물 탭까지 가지 않아도 되게 합니다.
    var editingMedication by remember { mutableStateOf<Medication?>(null) }

    val hasMedications = state.medications.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "예상 혈중 약물 농도",
                subtitle = if (hasMedications) {
                    "기준 용량 1회 복용 시 최고 농도를 100 %로 둔 상대값입니다. " +
                        "이전 투약의 남은 양이 이미 더해져 있습니다."
                } else {
                    "복용하는 약을 등록하면 여기에 곡선이 그려집니다."
                },
                trailing = {
                    Text(
                        TimeFmt.time(state.nowMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            ) {
                if (!hasMedications) {
                    SetupGuide(
                        onPickPreset = { preset ->
                            editingMedication = preset.toMedication(vm.nextColor())
                        },
                        onAddCustom = { editingMedication = vm.blankMedication() },
                        onOpenMedications = onOpenMedications,
                    )
                } else {
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
                            text = if (reading != null) {
                                "${TimeFmt.smart(reading.epochMs)} 시점"
                            } else {
                                "지금 (${TimeFmt.time(state.nowMs)})"
                            },
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
                            text = if (state.recentDoses.isEmpty()) {
                                "아직 복용 기록이 없어 곡선이 0 입니다. " +
                                    "아래 “지금 복용 기록” 버튼을 누르면 곡선이 올라갑니다."
                            } else {
                                "그래프를 손가락으로 짚으면 그 시각의 값을 읽을 수 있습니다."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (hasMedications) {
            item {
                SectionCard(
                    title = "지금 복용 기록",
                    subtitle = "누르면 현재 시각으로 바로 기록됩니다.",
                    trailing = {
                        TextButton(onClick = { editingMedication = vm.blankMedication() }) {
                            Text("약 추가")
                        }
                    },
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.medications.forEach { m ->
                            Button(
                                onClick = { vm.logDoseNow(m.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(m.colorArgb)),
                            ) {
                                Icon(Icons.Default.Medication, contentDescription = null)
                                Text("  ${m.displayName} ${fmtMg(m.doseMg)}")
                            }
                        }
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

    editingMedication?.let { m ->
        MedicationEditDialog(
            initial = m,
            onDismiss = { editingMedication = null },
            onSave = { vm.addMedication(it); editingMedication = null },
            onDelete = null,
            showPresetPicker = false,
        )
    }
}

/**
 * 약이 하나도 없을 때 첫 화면에 띄우는 안내.
 *
 * 앱을 처음 열면 이 탭이 가장 먼저 보입니다. 여기서 바로 약을 등록할 수 있어야
 * 화면이 비어 보이지 않고, 무엇을 하면 곡선이 생기는지도 알 수 있습니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupGuide(
    onPickPreset: (MedicationPreset) -> Unit,
    onAddCustom: () -> Unit,
    onOpenMedications: () -> Unit,
) {
    Column {
        StepRow(1, "복용하는 약을 등록합니다", "아래에서 고르면 용량만 확인하고 바로 등록됩니다.")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MedicationPresets.all.take(8).forEach { p ->
                AssistChip(
                    onClick = { onPickPreset(p) },
                    label = { Text(p.brandName) },
                )
            }
            AssistChip(onClick = onAddCustom, label = { Text("＋ 직접 입력") })
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "목록의 약동학 값은 제품설명서 기준의 참고치입니다. 등록 화면에서 본인 약에 맞게 고칠 수 있습니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        StepRow(2, "복용할 때 버튼을 누릅니다", "약 이름이 적힌 버튼 한 번이면 현재 시각으로 기록됩니다.")
        Spacer(Modifier.height(12.dp))
        StepRow(3, "농도 곡선이 그려집니다", "이전 복용분이 몸에 남은 양까지 더해 계산합니다.")

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenMedications) { Text("약물 탭에서 자세히 설정하기") }
    }
}

@Composable
private fun StepRow(number: Int, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            LegendChip(color = Color(st.medication.colorArgb), label = st.medication.label)
            Text(
                "${st.percentNow.roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        val detail = buildList {
            st.lastDoseAtEpochMs?.let { add("마지막 복용 ${TimeFmt.smart(it)}") }
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
