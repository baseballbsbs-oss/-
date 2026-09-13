package com.panictracker.app.data

import androidx.room.TypeConverter
import com.panictracker.app.data.entity.SleepKind
import com.panictracker.app.data.entity.SymptomType

class Converters {
    @TypeConverter fun symptomToString(v: SymptomType): String = v.name
    @TypeConverter fun stringToSymptom(v: String): SymptomType = SymptomType.valueOf(v)

    @TypeConverter fun sleepKindToString(v: SleepKind): String = v.name
    @TypeConverter fun stringToSleepKind(v: String): SleepKind = SleepKind.valueOf(v)
}
