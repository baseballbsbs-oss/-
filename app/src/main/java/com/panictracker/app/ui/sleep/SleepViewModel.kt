package com.panictracker.app.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panictracker.app.data.entity.SleepAwakening
import com.panictracker.app.data.entity.SleepKind
import com.panictracker.app.data.entity.SleepLog
import com.panictracker.app.data.entity.SleepSession
import com.panictracker.app.data.repo.TrackerRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SleepViewModel(private val repo: TrackerRepository) : ViewModel() {

    /** 아직 "완전히 기상" 을 누르지 않은 진행 중인 수면. */
    val ongoing: StateFlow<SleepSession?> = repo.ongoingSleep()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recent: StateFlow<List<SleepSession>> = repo.recentSleep(90)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 기상 직후 "잠든 시각 · 수면의 질" 을 물어볼 대상. */
    private val _justFinished = Channel<Long>(Channel.BUFFERED)
    val justFinished: Flow<Long> = _justFinished.receiveAsFlow()

    fun startSleep(kind: SleepKind) {
        viewModelScope.launch { repo.startSleep(kind) }
    }

    fun wakeUpNow() {
        viewModelScope.launch {
            val s = ongoing.value ?: return@launch
            // 아직 열려 있는 "깬 구간" 이 있으면 기상 시각으로 닫아 줍니다.
            s.awakenings.firstOrNull { it.backToSleepAt == null }?.let {
                repo.updateAwakening(it.copy(backToSleepAt = System.currentTimeMillis()))
            }
            repo.finishSleep(s.sleep.id)
            _justFinished.send(s.sleep.id)
        }
    }

    /** 밤중에 깼을 때. 다시 잠들면 [backToSleepNow] 로 닫습니다. */
    fun logAwakeningNow() {
        viewModelScope.launch {
            val s = ongoing.value ?: return@launch
            if (s.awakenings.any { it.backToSleepAt == null }) return@launch
            repo.logAwakening(s.sleep.id, System.currentTimeMillis())
        }
    }

    fun backToSleepNow() {
        viewModelScope.launch {
            val s = ongoing.value ?: return@launch
            val open = s.awakenings.firstOrNull { it.backToSleepAt == null } ?: return@launch
            repo.updateAwakening(open.copy(backToSleepAt = System.currentTimeMillis()))
        }
    }

    fun cancelOngoing() {
        viewModelScope.launch {
            ongoing.value?.let { repo.deleteSleep(it.sleep) }
        }
    }

    fun save(log: SleepLog) {
        viewModelScope.launch { repo.saveSleep(log) }
    }

    fun delete(log: SleepLog) {
        viewModelScope.launch { repo.deleteSleep(log) }
    }

    /** 편집 화면에서 수면 기록과 깬 구간들을 한 번에 저장합니다. */
    fun saveSession(log: SleepLog, awakenings: List<SleepAwakening>) {
        viewModelScope.launch { repo.saveSleepSession(log, awakenings) }
    }

    suspend fun session(id: Long): SleepSession? = repo.sleepSession(id)
}
