package com.panictracker.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panictracker.app.data.repo.SleepStats
import com.panictracker.app.data.repo.SleepStatsCalculator
import com.panictracker.app.data.repo.SymptomStats
import com.panictracker.app.data.repo.SymptomStatsCalculator
import com.panictracker.app.data.repo.TrackerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

enum class StatsRange(val label: String, val days: Int) {
    D7("7일", 7),
    D30("30일", 30),
    D90("90일", 90),
}

data class StatsState(
    val range: StatsRange = StatsRange.D30,
    val sleep: SleepStats = SleepStats.EMPTY,
    val symptom: SymptomStats = SymptomStats.EMPTY,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(private val repo: TrackerRepository) : ViewModel() {

    private val range = MutableStateFlow(StatsRange.D30)

    val state: StateFlow<StatsState> = range
        .flatMapLatest { r ->
            val today = LocalDate.now()
            val start = today.minusDays((r.days - 1).toLong())
            val fromMs = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            combine(
                repo.sleepSince(fromMs),
                repo.symptomsSince(fromMs),
            ) { sessions, symptoms ->
                StatsState(
                    range = r,
                    // 진행 중인(아직 기상 전) 수면은 통계에서 뺍니다.
                    sleep = SleepStatsCalculator.compute(
                        sessions.filter { it.sleep.finalWakeAt != 0L },
                        start,
                        today,
                    ),
                    symptom = SymptomStatsCalculator.compute(symptoms, start, today, today),
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun setRange(r: StatsRange) {
        range.value = r
    }
}
