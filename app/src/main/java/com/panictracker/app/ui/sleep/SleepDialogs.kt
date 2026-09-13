package com.panictracker.app.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.panictracker.app.data.entity.SleepAwakening
import com.panictracker.app.data.entity.SleepKind
import com.panictracker.app.data.entity.SleepLog
import com.panictracker.app.data.entity.SleepSession
import com.panictracker.app.ui.common.DateTimeChip
import com.panictracker.app.util.TimeFmt

/** 수면 기록 전체를 손으로 고치는 화면 — 취침/잠든/깬/기상 시각과 중간에 깬 구간들. */
@Composable
fun SleepEditDialog(
    session: SleepSession,
    onDismiss: () -> Unit,
    onSave: (SleepLog, List<SleepAwakening>) -> Unit,
    onDelete: () -> Unit,
) {
    var kind by remember { mutableStateOf(session.sleep.kind) }
    var bedTime by remember { mutableStateOf(session.sleep.bedTimeAt) }
    var sleepAt by remember { mutableStateOf(session.sleep.sleepAt) }
    var finalWake by remember {
        mutableStateOf(
            session.sleep.finalWakeAt.takeIf { it != 0L } ?: System.currentTimeMillis(),
        )
    }
    var getUp by remember { mutableStateOf(session.sleep.getUpAt) }
    var quality by remember { mutableStateOf(session.sleep.quality) }
    var note by remember { mutableStateOf(session.sleep.note) }
    val awakenings = remember { mutableStateListOf<SleepAwakening>().apply { addAll(session.awakenings) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (session.sleep.id == 0L) "수면 기록 추가" else "수면 기록 수정") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SleepKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k.label) },
                        )
                    }
                }

                DateTimeChip("취침", bedTime, { bedTime = it })

                if (sleepAt == null) {
                    AssistChip(
                        onClick = { sleepAt = bedTime + 20 * 60_000L },
                        label = { Text("잠든 시각 추가") },
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DateTimeChip("잠든 시각", sleepAt!!, { sleepAt = it })
                        TextButton(onClick = { sleepAt = null }) { Text("지움") }
                    }
                }

                DateTimeChip("완전히 깬 시각", finalWake, { finalWake = it })

                if (getUp == null) {
                    AssistChip(
                        onClick = { getUp = finalWake + 10 * 60_000L },
                        label = { Text("잠자리에서 일어난 시각 추가") },
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DateTimeChip("기상", getUp!!, { getUp = it })
                        TextButton(onClick = { getUp = null }) { Text("지움") }
                    }
                }

                Text("중간에 깬 구간", style = MaterialTheme.typography.labelLarge)
                awakenings.forEachIndexed { i, a ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DateTimeChip("깸", a.wokeAt, { awakenings[i] = a.copy(wokeAt = it) })
                            TextButton(onClick = { awakenings.removeAt(i) }) { Text("삭제") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DateTimeChip(
                                "다시 잠듦",
                                a.backToSleepAt ?: (a.wokeAt + 15 * 60_000L),
                                { awakenings[i] = a.copy(backToSleepAt = it) },
                            )
                            Text(
                                "  ${TimeFmt.duration(a.awakeMinutes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                AssistChip(
                    onClick = {
                        val base = awakenings.lastOrNull()?.backToSleepAt ?: (sleepAt ?: bedTime)
                        awakenings.add(
                            SleepAwakening(
                                sleepLogId = session.sleep.id,
                                wokeAt = base + 2 * 3_600_000L,
                                backToSleepAt = base + 2 * 3_600_000L + 15 * 60_000L,
                            ),
                        )
                    },
                    label = { Text("깬 구간 추가") },
                )

                Text("수면의 질", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { level ->
                        FilterChip(
                            selected = quality == level,
                            onClick = { quality = if (quality == level) null else level },
                            label = { Text("$level") },
                        )
                    }
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
            TextButton(onClick = {
                onSave(
                    session.sleep.copy(
                        kind = kind,
                        bedTimeAt = bedTime,
                        sleepAt = sleepAt,
                        finalWakeAt = finalWake,
                        getUpAt = getUp,
                        quality = quality,
                        note = note.trim(),
                    ),
                    awakenings.toList(),
                )
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (session.sleep.id != 0L) {
                    TextButton(onClick = onDelete) { Text("삭제") }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

/**
 * 기상 직후 묻는 두 가지.
 * 자는 동안에는 알 수 없는 값이라 여기서 채웁니다. 건너뛰어도 기록은 이미 저장돼 있습니다.
 */
@Composable
fun WakeUpFollowUpDialog(
    session: SleepSession,
    onDismiss: () -> Unit,
    onSave: (SleepLog) -> Unit,
) {
    var latency by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("잘 주무셨나요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${TimeFmt.time(session.sleep.bedTimeAt)} 취침 → " +
                        "${TimeFmt.time(session.sleep.finalWakeAt)} 기상" +
                        if (session.awakeningCount > 0) " · 중간에 ${session.awakeningCount}회 깸" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = latency,
                    onValueChange = { latency = it.filter(Char::isDigit).take(3) },
                    label = { Text("잠들기까지 걸린 시간 (분)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("수면의 질", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { level ->
                        FilterChip(
                            selected = quality == level,
                            onClick = { quality = level },
                            label = { Text("$level") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = latency.toIntOrNull()
                onSave(
                    session.sleep.copy(
                        sleepAt = minutes?.let { session.sleep.bedTimeAt + it * 60_000L },
                        quality = quality,
                    ),
                )
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("건너뛰기") } },
    )
}
