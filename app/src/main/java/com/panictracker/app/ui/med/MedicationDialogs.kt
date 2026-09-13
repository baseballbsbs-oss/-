package com.panictracker.app.ui.med

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.panictracker.app.data.MedicationPresets
import com.panictracker.app.data.entity.Medication
import com.panictracker.app.pk.PkDose
import com.panictracker.app.pk.PkEngine
import com.panictracker.app.ui.common.DateTimeChip
import com.panictracker.app.ui.common.PkChart
import com.panictracker.app.util.TimeFmt

/** 약 추가·수정. 값을 바꿀 때마다 1회 복용 곡선이 미리보기로 다시 그려집니다. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MedicationEditDialog(
    initial: Medication,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit,
    onDelete: (() -> Unit)?,
    /** 이미 바깥에서 약을 골라 왔다면 목록을 또 보여 줄 필요가 없습니다. */
    showPresetPicker: Boolean = initial.id == 0L,
) {
    var ingredient by remember { mutableStateOf(initial.ingredientName) }
    var brand by remember { mutableStateOf(initial.brandName) }
    var dose by remember { mutableStateOf(trimNum(initial.doseMg)) }
    var halfLife by remember { mutableStateOf(trimNum(initial.halfLifeHours)) }
    var tmax by remember { mutableStateOf(trimNum(initial.tmaxHours)) }
    var onset by remember { mutableStateOf(initial.onsetMinutes.toString()) }
    var duration by remember { mutableStateOf(trimNum(initial.durationHours)) }
    var color by remember { mutableStateOf(initial.colorArgb) }
    var note by remember { mutableStateOf(initial.note) }
    var showPresets by remember { mutableStateOf(showPresetPicker) }

    val draft = initial.copy(
        ingredientName = ingredient.trim(),
        brandName = brand.trim(),
        doseMg = dose.toDoubleOrNull() ?: 0.0,
        halfLifeHours = halfLife.toDoubleOrNull() ?: 0.0,
        tmaxHours = tmax.toDoubleOrNull() ?: 0.0,
        onsetMinutes = onset.toIntOrNull() ?: 0,
        durationHours = duration.toDoubleOrNull() ?: 0.0,
        colorArgb = color,
        note = note.trim(),
    )
    val valid = draft.doseMg > 0 && draft.halfLifeHours > 0 && draft.tmaxHours > 0 &&
        (draft.ingredientName.isNotBlank() || draft.brandName.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "약 추가" else "약 수정") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (showPresets) {
                    Text("자주 쓰는 약에서 고르기", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MedicationPresets.all.forEach { p ->
                            AssistChip(
                                onClick = {
                                    ingredient = p.ingredientName
                                    brand = p.brandName
                                    dose = trimNum(p.doseMg)
                                    halfLife = trimNum(p.halfLifeHours)
                                    tmax = trimNum(p.tmaxHours)
                                    onset = p.onsetMinutes.toString()
                                    duration = trimNum(p.durationHours)
                                    note = p.note
                                    showPresets = false
                                },
                                label = { Text(p.brandName) },
                            )
                        }
                    }
                    TextButton(onClick = { showPresets = false }) { Text("직접 입력하기") }
                }

                OutlinedTextField(
                    value = ingredient,
                    onValueChange = { ingredient = it },
                    label = { Text("성분명") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("제품명") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(dose, { dose = it }, "1회 용량 (mg)")

                Text("약동학 값", style = MaterialTheme.typography.labelLarge)
                Text(
                    "제품설명서의 값을 넣으세요. Tmax 는 반감기의 약 1.4배보다 작아야 하며, " +
                        "넘으면 자동으로 보정해서 그립니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(halfLife, { halfLife = it }, "반감기 (시간)", Modifier.weight(1f))
                    NumberField(tmax, { tmax = it }, "Tmax (시간)", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(onset, { onset = it }, "발현시간 (분)", Modifier.weight(1f))
                    NumberField(duration, { duration = it }, "작용시간 (시간)", Modifier.weight(1f))
                }

                Text("그래프 색", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MedicationPresets.palette.forEach { c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (c == color) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }

                if (valid) {
                    Text("1회 복용 시 예상 곡선", style = MaterialTheme.typography.labelLarge)
                    SingleDosePreview(draft)
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(draft) }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("삭제") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() || it == '.' }.take(7)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 지금 이 값으로 1회 복용했을 때의 곡선 — 파라미터를 감으로 맞춰 볼 때 씁니다. */
@Composable
private fun SingleDosePreview(m: Medication) {
    val pk = remember(m) { runCatching { m.toPk() }.getOrNull() } ?: return
    val now = remember(m) { System.currentTimeMillis() }
    val span = remember(m) {
        (maxOf(m.durationHours, m.halfLifeHours * 2.0, 6.0)).coerceAtMost(72.0)
    }
    val series = remember(m) {
        PkEngine.series(
            pk,
            listOf(PkDose(pk.id, now, m.doseMg)),
            now - 3_600_000L,
            now + (span * 3_600_000L).toLong(),
            stepMinutes = (span * 60 / 300).toInt().coerceAtLeast(1),
        )
    }
    Column {
        PkChart(
            series = listOf(series),
            doseMarkers = emptyList(),
            fromMs = now - 3_600_000L,
            toMs = now + (span * 3_600_000L).toLong(),
            nowMs = now,
            height = 140.dp,
        )
        Text(
            "최고 농도 ${TimeFmt.duration((pk.profile.effectiveTmaxHours * 60).toInt())} 뒤 · " +
                "20 %까지 ${TimeFmt.duration((pk.profile.hoursToDecayTo(0.2) * 60).toInt())}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 지난 시각의 복용을 나중에 적을 때. */
@Composable
fun DoseLogDialog(
    medication: Medication,
    onDismiss: () -> Unit,
    onSave: (atMs: Long, doseMg: Double) -> Unit,
) {
    var at by remember { mutableStateOf(System.currentTimeMillis()) }
    var mg by remember { mutableStateOf(trimNum(medication.doseMg)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${medication.displayName} 복용 기록") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                DateTimeChip("복용 시각", at, { at = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-60, -30, -15, 15).forEach { d ->
                        AssistChip(
                            onClick = { at += d * 60_000L },
                            label = { Text(if (d < 0) "${-d}분 전" else "+${d}분") },
                        )
                    }
                }
                NumberField(mg, { mg = it }, "복용량 (mg)")
                Spacer(Modifier.height(0.dp))
                Text(
                    "반 알을 드셨다면 표준 용량의 절반을 적으세요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = (mg.toDoubleOrNull() ?: 0.0) > 0,
                onClick = { onSave(at, mg.toDouble()) },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
