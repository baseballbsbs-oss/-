package com.panictracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.panictracker.app.data.dao.DoseDao
import com.panictracker.app.data.dao.MedicationDao
import com.panictracker.app.data.dao.SleepDao
import com.panictracker.app.data.dao.SymptomDao
import com.panictracker.app.data.entity.DoseLog
import com.panictracker.app.data.entity.Medication
import com.panictracker.app.data.entity.SleepAwakening
import com.panictracker.app.data.entity.SleepLog
import com.panictracker.app.data.entity.SymptomLog

@Database(
    entities = [
        Medication::class,
        DoseLog::class,
        SymptomLog::class,
        SleepLog::class,
        SleepAwakening::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseDao(): DoseDao
    abstract fun symptomDao(): SymptomDao
    abstract fun sleepDao(): SleepDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "panic_tracker.db",
            ).build().also { instance = it }
        }
    }
}
