package com.countdown.app.data

import java.time.LocalDate
import java.time.LocalTime

data class CountdownData(
    val eventContent: String = "",
    val targetDate: LocalDate = LocalDate.now().plusDays(1),
    val reminderTimeHour: Int = 8,
    val reminderTimeMinute: Int = 0,
    val reminderEnabled: Boolean = false,
    val themeMode: Int = 0, // 0=system, 1=light, 2=dark
    // 铃声设置
    val ringtoneType: Int = RINGTYPE_DEFAULT,
    val ringtoneUri: String = "",
    val ringtoneName: String = "系统默认闹钟铃声"
) {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        // 铃声类型
        const val RINGTYPE_DEFAULT = 0       // 系统默认闹钟铃声
        const val RINGTYPE_SYSTEM = 1        // 系统铃声（通知/铃声/闹钟）
        const val RINGTYPE_LOCAL_FILE = 2    // 本地音频文件（MP3/WAV/OGG/FLAC等）
    }
}
