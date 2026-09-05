package com.example.mntnode.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Base name without extension, e.g. `mntnode_statistics_4th_March_2026_13.32` or `…_04.32` before noon.
 * Date uses English month words and day ordinals; time is 24h with zero-padded hours and minutes and a dot between them.
 */
object StatisticsExportFileNames {

    fun baseName(at: Date = Date(), locale: Locale = Locale.ENGLISH): String {
        val cal = Calendar.getInstance(locale)
        cal.time = at
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthName = SimpleDateFormat("MMMM", locale).format(at)
        val year = cal.get(Calendar.YEAR)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val ord = ordinalSuffix(day)
        val time = String.format(locale, "%02d.%02d", hour, minute)
        return "mntnode_statistics_${day}${ord}_${monthName}_${year}_$time"
    }

    private fun ordinalSuffix(day: Int): String {
        if (day in 11..13) return "th"
        return when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
}
