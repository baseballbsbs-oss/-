package com.panictracker.app.ui.symptom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panictracker.app.data.entity.SymptomLog
import com.panictracker.app.data.entity.SymptomType
import com.panictracker.app.data.repo.TrackerRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 빠른 기록 직후 띄우는 안내 + 실행취소. */
data class QuickLogEvent(val type: SymptomType, val logId: Long)

class SymptomViewModel(private val repo: TrackerRepository) : ViewModel() {

    val recent: StateFlow<List<SymptomLog>> = repo.recentSymptoms(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 오늘 종류별 횟수 — 버튼 위 배지에 씁니다. */
    val todayCounts: StateFlow<Map<SymptomType, Int>> =
        repo.symptomsSince(startOfTodayMs())
            .map { logs -> logs.groupingBy { it.type }.eachCount() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _events = Channel<QuickLogEvent>(Channel.BUFFERED)
    val events: Flow<QuickLogEvent> = _events.receiveAsFlow()

    /** 버튼 한 번 = 기록 완료. 강도·메모는 나중에 눌러서 채울 수 있습니다. */
    fun quickLog(type: SymptomType) {
        viewModelScope.launch {
            val id = repo.quickLogSymptom(type)
            _events.send(QuickLogEvent(type, id))
        }
    }

    fun undo(logId: Long) {
        viewModelScope.launch { repo.deleteSymptomById(logId) }
    }

    fun save(log: SymptomLog) {
        viewModelScope.launch {
            if (log.id == 0L) repo.quickLogSymptom(log.type, log.occurredAt) else repo.updateSymptom(log)
        }
    }

    fun delete(log: SymptomLog) {
        viewModelScope.launch { repo.deleteSymptom(log) }
    }

    private fun startOfTodayMs(): Long =
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
}
