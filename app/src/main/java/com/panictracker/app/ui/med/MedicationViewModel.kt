package com.panictracker.app.ui.med

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panictracker.app.data.MedicationPresets
import com.panictracker.app.data.entity.Medication
import com.panictracker.app.data.repo.TrackerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationViewModel(private val repo: TrackerRepository) : ViewModel() {

    val medications: StateFlow<List<Medication>> = repo.medications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 새 약에 자동으로 배정할 색 — 이미 쓰고 있는 색은 피합니다. */
    fun nextColor(): Int {
        val used = medications.value.map { it.colorArgb }.toSet()
        return MedicationPresets.palette.firstOrNull { it !in used }
            ?: MedicationPresets.colorFor(medications.value.size)
    }

    fun save(m: Medication) {
        viewModelScope.launch {
            if (m.id == 0L) repo.addMedication(m) else repo.updateMedication(m)
        }
    }

    fun delete(m: Medication) {
        viewModelScope.launch { repo.deleteMedication(m) }
    }

    fun toggleActive(m: Medication) {
        viewModelScope.launch { repo.updateMedication(m.copy(isActive = !m.isActive)) }
    }

    /** 과거 시각으로 투약을 기록합니다 (깜빡하고 나중에 적을 때). */
    fun logDose(medicationId: Long, atMs: Long, doseMg: Double) {
        viewModelScope.launch { repo.logDose(medicationId, atMs, doseMg) }
    }
}
