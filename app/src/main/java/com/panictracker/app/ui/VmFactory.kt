package com.panictracker.app.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.panictracker.app.container
import com.panictracker.app.data.repo.TrackerRepository
import com.panictracker.app.ui.home.HomeViewModel
import com.panictracker.app.ui.med.MedicationViewModel
import com.panictracker.app.ui.sleep.SleepViewModel
import com.panictracker.app.ui.stats.StatsViewModel
import com.panictracker.app.ui.symptom.SymptomViewModel

private val CreationExtras.repo: TrackerRepository
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
        .container.repository

/** 라이브러리 없이 쓰는 ViewModel 팩토리 모음. */
object Vm {
    val factory = viewModelFactory {
        initializer { HomeViewModel(repo) }
        initializer { SymptomViewModel(repo) }
        initializer { SleepViewModel(repo) }
        initializer { MedicationViewModel(repo) }
        initializer { StatsViewModel(repo) }
    }
}
