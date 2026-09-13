package com.panictracker.app.ui.sleep

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panictracker.app.data.entity.SleepKind
import com.panictracker.app.data.entity.SleepLog
import com.panictracker.app.data.entity.SleepSession
import com.panictracker.app.ui.Vm
import com.panictracker.app.ui.common.SectionCard
import com.panictracker.app.util.TimeFmt

/**
 * 수면 화면.
 *
 * 밤에 실제로 누를 수 있는 버튼만 큼직하게 둡니다 — 취침 / 중간에 깸 / 다시 잠듦 /
 * 완전히 기상. 잠든 시각과 수면의 질처럼 자면서는 알 수 없는 값은 기상 직후에 묻습니다.
 */
@Composable
fun SleepScreen(
    modifier: Modifier = Modifier,
    vm: SleepViewModel = viewModel(factory = Vm.factory),
) {
    val ongoing by vm.ongoing.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SleepSession?>(null) }
    var wakeUpFollowUp by remember { mutableStateOf<SleepSession?>(null) }

    LaunchedEffect(Unit) {
        vm.justFinished.collect { id -> wakeUpFollowUp = vm.session(id) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            if (ongoing == null) {
                SectionCard(
                    title = "수면 기록 시작",
                    subtitle = "잠자리에 들 때 누르세요. 기상할 때 다시 눌러 마무리합니다.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { vm.startSleep(SleepKind.NIGHT) },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Icon(Icons.Default.Bedtime, contentDescription = null)
                            Text("  취침")
                        }
                        OutlinedButton(
                            onClick = { vm.startSleep(SleepKind.NAP) },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Icon(Icons.Default.LightMode, contentDescription = null)
                            Text("  낮잠")
                        }
                    }
                }
            } else {
                OngoingCard(
                    session = ongoing!!,
                    onAwake = vm::logAwakeningNow,
                    onBackToSleep = vm::backToSleepNow,
                    onWakeUp = vm::wakeUpNow,
                    onCancel = vm::cancelOngoing,
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("수면 기록", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    editing = SleepSession(
                        SleepLog(
                            kind = SleepKind.NIGHT,
                            bedTimeAt = now - 8 * 3_600_000L,
                            finalWakeAt = now,
                        ),
                    )
                }) { Text("직접 추가") }
            }
        }

        if (recent.none { it.sleep.finalWakeAt != 0L }) {
            item {
                Text(
                    "아직 완료된 수면 기록이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(recent.filter { it.sleep.finalWakeAt != 0L }, key = { it.sleep.id }) { s ->
            SleepRow(s, onClick = { editing = s })
        }
    }

    editing?.let { s ->
        SleepEditDialog(
            session = s,
            onDismiss = { editing = null },
            onSave = { log, awakenings -> vm.saveSession(log, awakenings); editing = null },
            onDelete = { vm.delete(s.sleep); editing = null },
        )
    }

    wakeUpFollowUp?.let { s ->
        WakeUpFollowUpDialog(
            session = s,
            onDismiss = { wakeUpFollowUp = null },
            onSave = { vm.save(it); wakeUpFollowUp = null },
        )
    }
}

@Composable
private fun OngoingCard(
    session: SleepSession,
    onAwake: () -> Unit,
    onBackToSleep: () -> Unit,
    onWakeUp: () -> Unit,
    onCancel: () -> Unit,
) {
    val openAwakening = session.awakenings.firstOrNull { it.backToSleepAt == null }
    SectionCard(
        title = if (session.sleep.kind == SleepKind.NAP) "낮잠 중" else "수면 중",
        subtitle = "${TimeFmt.smart(session.sleep.bedTimeAt)}부터 · " +
            "중간에 깬 횟수 ${session.awakenings.size}회",
        trailing = { TextButton(onClick = onCancel) { Text("취소") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (openAwakening == null) {
                OutlinedButton(
                    onClick = onAwake,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("중간에 깼어요") }
            } else {
                Text(
                    "${TimeFmt.time(openAwakening.wokeAt)}에 깼습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onBackToSleep,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) { Text("다시 잠들어요") }
            }
            Button(
                onClick = onWakeUp,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("완전히 기상") }
        }
    }
}

@Composable
private fun SleepRow(s: SleepSession, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${if (s.sleep.kind == SleepKind.NAP) "낮잠" else "밤잠"} · " +
                    TimeFmt.duration(s.totalSleepMinutes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "효율 ${s.efficiencyPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${TimeFmt.smart(s.sleep.bedTimeAt)} → ${TimeFmt.time(s.sleep.finalWakeAt)}" +
                (if (s.awakeningCount > 0) " · 깸 ${s.awakeningCount}회 (${TimeFmt.duration(s.awakeAfterOnsetMinutes)})" else "") +
                (s.sleep.quality?.let { " · 질 $it/5" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (s.sleep.note.isNotBlank()) {
            Text(
                s.sleep.note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}
