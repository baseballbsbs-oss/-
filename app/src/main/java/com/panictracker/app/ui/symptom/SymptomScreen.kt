package com.panictracker.app.ui.symptom

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panictracker.app.data.entity.SymptomLog
import com.panictracker.app.data.entity.SymptomType
import com.panictracker.app.ui.Vm
import com.panictracker.app.util.TimeFmt

/**
 * 증상 화면.
 *
 * 메뉴 → 증상 → 버튼 한 번이면 기록이 끝납니다. 공황이 오는 중에 입력 폼을 채우게
 * 하지 않는 것이 이 화면의 유일한 목표입니다. 강도·지속시간·메모는 나중에
 * 목록에서 눌러 채우거나, 버튼을 길게 눌러 처음부터 자세히 적을 수 있습니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomScreen(
    modifier: Modifier = Modifier,
    vm: SymptomViewModel = viewModel(factory = Vm.factory),
) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val todayCounts by vm.todayCounts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var editing by remember { mutableStateOf<SymptomLog?>(null) }
    var detailFor by remember { mutableStateOf<SymptomType?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            val result = snackbar.showSnackbar(
                message = "${e.type.label} 기록됨 · ${TimeFmt.time(System.currentTimeMillis())}",
                actionLabel = "실행취소",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo(e.logId)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "증상 기록",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "누르면 바로 기록됩니다. 길게 누르면 강도·시각을 직접 적을 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                // 6개뿐이라 격자로 한 화면에 다 들어옵니다 — 스크롤 없이 누를 수 있게.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(SymptomType.entries.toList(), key = { it.name }) { type ->
                        SymptomButton(
                            type = type,
                            todayCount = todayCounts[type] ?: 0,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.quickLog(type)
                            },
                            onLongClick = { detailFor = type },
                        )
                    }
                }
            }

            item {
                Text(
                    "최근 기록",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (recent.isEmpty()) {
                item {
                    Text(
                        "아직 기록이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(recent, key = { it.id }) { log ->
                SymptomRow(log, onClick = { editing = log }, onDelete = { vm.delete(log) })
            }
        }
    }

    editing?.let { log ->
        SymptomEditDialog(
            initial = log,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
            onDelete = { vm.delete(log); editing = null },
        )
    }

    detailFor?.let { type ->
        SymptomEditDialog(
            initial = SymptomLog(type = type, occurredAt = System.currentTimeMillis()),
            onDismiss = { detailFor = null },
            onSave = { vm.save(it); detailFor = null },
            onDelete = null,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SymptomButton(
    type: SymptomType,
    todayCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(type.emoji, fontSize = 28.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    type.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (todayCount > 0) {
                    Text(
                        "오늘 ${todayCount}회",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SymptomRow(log: SymptomLog, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${log.type.emoji} ${log.type.label}" +
                    (log.severity?.let { " · 강도 $it" } ?: "") +
                    (log.durationMinutes?.let { " · ${TimeFmt.duration(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                TimeFmt.smart(log.occurredAt) + if (log.note.isNotBlank()) " · ${log.note}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TextButton(onClick = onDelete) { Text("삭제") }
    }
}
