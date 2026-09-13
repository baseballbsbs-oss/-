package com.panictracker.app

import android.app.Application
import android.content.Context
import com.panictracker.app.data.AppDatabase
import com.panictracker.app.data.repo.TrackerRepository

/** 라이브러리 없이 쓰는 아주 작은 의존성 컨테이너. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.get(context)
    val repository: TrackerRepository = TrackerRepository(database)
}

class PanicTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** ViewModel 팩토리에서 Application 을 통해 컨테이너를 꺼내 씁니다. */
val Application.container: AppContainer
    get() = (this as PanicTrackerApp).container
