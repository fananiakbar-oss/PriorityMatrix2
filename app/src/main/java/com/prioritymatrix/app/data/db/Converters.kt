package com.prioritymatrix.app.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
    @TypeConverter fun timeToInt(t: LocalTime?): Int? = t?.toSecondOfDay()
    @TypeConverter fun intToTime(v: Int?): LocalTime? = v?.let { LocalTime.ofSecondOfDay(it.toLong()) }
}
