package com.countdown.app.data

import java.time.LocalDate
import java.time.LocalTime

data class CountdownData(
    val eventContent: String = "",
    val targetDate: LocalDate = LocalDate.now().plusDays(1),
    val reminderTimeHour: Int = 8,
    val reminderTimeMinute: Int = 0,
    val reminderEnabled: Boolean = false,
    val themeMode: Int = 0 // 0=system, 1=light, 2=dark
) {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }
}
