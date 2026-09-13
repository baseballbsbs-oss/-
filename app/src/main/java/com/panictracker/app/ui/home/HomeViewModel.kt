package com.panictracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panictracker.app.data.MedicationPresets
import com.panictracker.app.data.entity.DoseLog
import com.panictracker.app.data.entity.Medication
import com.panictracker.app.data.entity.SymptomLog
import com.panictracker.app.data.repo.TrackerRepository
import com.panictracker.app.pk.PkEngine
import com.panictracker.app.pk.PkSeries
import com.panictracker.app.pk.PkStatus
import com.panictracker.app.ui.common.DoseMarker
import com.panictracker.app.util.TimeFmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 곡선을 얼마나 넓게 볼지. 과거 쪽이 더 길고, 앞으로의 예측도 조금 보여 줍니다. */
enum class TimeWindow(val label: String, val pastHours: Int, val futureHours: Int) {
    H12("12시간", 8, 4),
    H24("24시간", 16, 8),
    D3("3일", 48, 24),
    D7("7일", 144, 24),
}

data class HomeState(
    val loading: Boolean = true,
    val medications: List<Medication> = emptyList(),
    val series: List<PkSeries> = emptyList(),
    val statuses: List<PkStatus> = emptyList(),
    val doseMarkers: List<DoseMarker> = emptyList(),
    val recentDoses: List<DoseLog> = emptyList(),
    val todayDoseCount: Int = 0,
    val todaySymptoms: List<SymptomLog> = emptyList(),
    val window: TimeWindow = TimeWindow.H24,
    val nowMs: Long = System.currentTimeMillis(),
) {
    val fromMs: Long get() = nowMs - window.pastHours * 3_600_000L
    val toMs: Long get() = nowMs + window.futureHours * 3_600_000L
}

class HomeViewModel(private val repo: TrackerRepository) : ViewModel() {

    private val window = MutableStateFlow(TimeWindow.H24)
    private val now = MutableStateFlow(System.currentTimeMillis())

    init {
        // 1분마다 "지금" 선과 상태 카드를 갱신합니다.
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                now.value = System.currentTimeMillis()
            }
        }
    }

    val state: StateFlow<HomeState> = combine(
        repo.activeMedications(),
        repo.recentDoses(500),
        repo.symptomsSince(startOfTodayMs() - 2 * 86_400_000L),
        window,
        now,
    ) { meds, doses, symptoms, win, nowMs ->
        // 반감기·Tmax 가 0 이하이면 곡선을 만들 수 없으므로 건너뜁니다.
        val usable = meds.filter { it.hasUsablePk }
        val pkMeds = usable.map { it.toPk() }
        val pkDoses = doses.map { it.toPk() }
        val from = nowMs - win.pastHours * 3_600_000L
        val to = nowMs + win.futureHours * 3_600_000L
        // 화면 폭이 대략 400 px 이므로 그보다 촘촘히 그릴 필요는 없습니다.
        val stepMinutes = (((to - from) / 60_000L) / 360L).toInt().coerceIn(1, 120)

        val byId = meds.associateBy { it.id }
        HomeState(
            loading = false,
            medications = usable,
            series = PkEngine.seriesForAll(pkMeds, pkDoses, from, to, stepMinutes),
            statuses = pkMeds.map { PkEngine.status(it, pkDoses, nowMs) },
            doseMarkers = doses.filter { it.takenAt in from..to }.mapNotNull { d ->
                byId[d.medicationId]?.let {
                    DoseMarker(d.takenAt, it.colorArgb, "${it.displayName} ${fmtMg(d.doseMg)}")
                }
            },
            recentDoses = doses.take(12),
            todayDoseCount = doses.count {
                it.takenAt <= nowMs && TimeFmt.date(it.takenAt) == TimeFmt.date(nowMs)
            },
            todaySymptoms = symptoms.filter {
                TimeFmt.date(it.occurredAt) == TimeFmt.date(nowMs)
            },
            window = win,
            nowMs = nowMs,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setWindow(w: TimeWindow) {
        window.value = w
    }

    /** 대시보드에서 바로 "지금 복용" 기록. */
    fun logDoseNow(medicationId: Long) {
        viewModelScope.launch {
            repo.logDose(medicationId)
            now.value = System.currentTimeMillis()
        }
    }

    fun deleteDose(d: DoseLog) {
        viewModelScope.launch { repo.deleteDose(d) }
    }

    /** 첫 화면에서 바로 약을 등록할 수 있게 합니다 — 약물 탭까지 가지 않아도 됩니다. */
    fun addMedication(m: Medication) {
        viewModelScope.launch { repo.addMedication(m) }
    }

    /** 이미 쓰고 있는 색은 피해서 새 약에 색을 배정합니다. */
    fun nextColor(): Int {
        val used = state.value.medications.map { it.colorArgb }.toSet()
        return MedicationPresets.palette.firstOrNull { it !in used }
            ?: MedicationPresets.colorFor(used.size)
    }

    /** 직접 입력용 빈 약 템플릿. */
    fun blankMedication(): Medication = Medication(
        ingredientName = "",
        brandName = "",
        doseMg = 1.0,
        halfLifeHours = 12.0,
        tmaxHours = 1.5,
        onsetMinutes = 30,
        durationHours = 6.0,
        colorArgb = nextColor(),
    )

    private fun startOfTodayMs(): Long =
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
}

/** 0.25 처럼 소수가 필요한 용량은 그대로, 10.0 같은 값은 정수로 보여 줍니다. */
fun fmtMg(mg: Double): String =
    if (mg == mg.toLong().toDouble()) "${mg.toLong()}mg" else "${mg}mg"
