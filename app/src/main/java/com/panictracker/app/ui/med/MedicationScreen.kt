package com.panictracker.app.ui.med

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panictracker.app.data.entity.Medication
import com.panictracker.app.ui.Vm
import com.panictracker.app.ui.home.fmtMg

/** 복용 중인 약 목록. 여기 등록한 약동학 값이 그대로 농도 곡선이 됩니다. */
@Composable
fun MedicationScreen(
    modifier: Modifier = Modifier,
    vm: MedicationViewModel = viewModel(factory = Vm.factory),
) {
    val meds by vm.medications.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Medication?>(null) }
    var loggingFor by remember { mutableStateOf<Medication?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = Medication(
                    ingredientName = "",
                    brandName = "",
                    doseMg = 1.0,
                    halfLifeHours = 12.0,
                    tmaxHours = 1.5,
                    onsetMinutes = 30,
                    durationHours = 6.0,
                    colorArgb = vm.nextColor(),
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = "약 추가")
            }
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text("복용 약물", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "성분명·제품명·용량과 약동학 값을 등록하면 농도 곡선이 그려집니다. " +
                        "약동학 값은 제품설명서 기준의 참고치이므로 본인 약에 맞게 고쳐 쓰세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(4.dp))
            }

            if (meds.isEmpty()) {
                item {
                    Text(
                        "등록된 약이 없습니다. 오른쪽 아래 + 버튼으로 추가하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(meds, key = { it.id }) { m ->
                MedicationRow(
                    m = m,
                    onClick = { editing = m },
                    onToggle = { vm.toggleActive(m) },
                    onLogDose = { loggingFor = m },
                )
            }
        }
    }

    editing?.let { m ->
        MedicationEditDialog(
            initial = m,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
            onDelete = if (m.id != 0L) { { vm.delete(m); editing = null } } else null,
        )
    }

    loggingFor?.let { m ->
        DoseLogDialog(
            medication = m,
            onDismiss = { loggingFor = null },
            onSave = { at, mg -> vm.logDose(m.id, at, mg); loggingFor = null },
        )
    }
}

@Composable
private fun MedicationRow(
    m: Medication,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onLogDose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(m.colorArgb)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${m.brandName.ifBlank { "제품명 미입력" }} · ${fmtMg(m.doseMg)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                m.ingredientName.ifBlank { "성분명 미입력" },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "반감기 ${trimNum(m.halfLifeHours)}h · Tmax ${trimNum(m.tmaxHours)}h · " +
                    "발현 ${m.onsetMinutes}분 · 작용 ${trimNum(m.durationHours)}h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Switch(checked = m.isActive, onCheckedChange = { onToggle() })
            TextButton(onClick = onLogDose) { Text("복용 기록") }
        }
    }
}

/** 12.0 → "12", 1.5 → "1.5" */
internal fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
