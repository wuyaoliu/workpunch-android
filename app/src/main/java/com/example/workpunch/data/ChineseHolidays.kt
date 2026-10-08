package com.example.workpunch.data

import android.icu.util.ChineseCalendar
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/** Returns the statutory public-holiday name for a Gregorian date, when applicable. */
fun chineseStatutoryHoliday(date: LocalDate): String? {
    if (date.monthValue == 1 && date.dayOfMonth == 1) return "元旦"
    if (date.monthValue == 5 && date.dayOfMonth in 1..2) return "劳动节"
    if (date.monthValue == 10 && date.dayOfMonth in 1..3) return "国庆节"
    if (date.monthValue == 4 && date.dayOfMonth == qingmingDay(date.year)) return "清明节"

    val lunar = lunarDate(date)
    if (date.year >= 2025 && isLunarNewYear(date.plusDays(1))) return "除夕"
    if (!lunar.isLeapMonth && lunar.month == 1 && lunar.day in 1..3) return "春节"
    if (!lunar.isLeapMonth && lunar.month == 5 && lunar.day == 5) return "端午节"
    if (!lunar.isLeapMonth && lunar.month == 8 && lunar.day == 15) return "中秋节"
    return null
}

private data class LunarDate(val month: Int, val day: Int, val isLeapMonth: Boolean)

private fun lunarDate(date: LocalDate): LunarDate {
    val calendar = ChineseCalendar().apply {
        timeInMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    return LunarDate(
        month = calendar.get(ChineseCalendar.MONTH) + 1,
        day = calendar.get(ChineseCalendar.DAY_OF_MONTH),
        isLeapMonth = calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1
    )
}

private fun isLunarNewYear(date: LocalDate): Boolean {
    val lunar = lunarDate(date)
    return !lunar.isLeapMonth && lunar.month == 1 && lunar.day == 1
}

private fun qingmingDay(year: Int): Int {
    val coefficient = if (year in 2000..2099) 4.81 else 5.59
    return floor((year % 100) * 0.2422 + coefficient).toInt() - (year % 100) / 4
}
