package com.panictracker.app.ui.symptom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.panictracker.app.data.entity.SymptomLog
import com.panictracker.app.ui.common.DateTimeChip

/** 강도·지속시간·시각·메모를 채우는 보완 입력. 빠른 기록 뒤에 눌러 쓰거나 길게 눌러 씁니다. */
@Composable
fun SymptomEditDialog(
    initial: SymptomLog,
    onDismiss: () -> Unit,
    onSave: (SymptomLog) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var occurredAt by remember { mutableStateOf(initial.occurredAt) }
    var severity by remember { mutableStateOf(initial.severity) }
    var duration by remember { mutableStateOf(initial.durationMinutes?.toString() ?: "") }
    var note by remember { mutableStateOf(initial.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${initial.type.emoji} ${initial.type.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateTimeChip("발생 시각", occurredAt, { occurredAt = it })

                Text("강도", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { level ->
                        FilterChip(
                            selected = severity == level,
                            onClick = { severity = if (severity == level) null else level },
                            label = { Text("$level") },
                        )
                    }
                }
                Text(
                    "1 = 견딜 만함, 5 = 매우 심함 (비워 두어도 됩니다)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit).take(4) },
                    label = { Text("지속시간 (분)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("메모 (상황, 유발 요인 등)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        occurredAt = occurredAt,
                        severity = severity,
                        durationMinutes = duration.toIntOrNull(),
                        note = note.trim(),
                    ),
                )
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("삭제") }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}
