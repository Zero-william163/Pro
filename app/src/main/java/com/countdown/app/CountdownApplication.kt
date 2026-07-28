package com.countdown.app

import android.app.Application
import com.countdown.app.data.CountdownRepository
import com.countdown.app.util.AlarmScheduler
import com.countdown.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CountdownApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // 创建通知渠道（使用新版多渠道方案）
        NotificationHelper.createNotificationChannels(this)

        // 确保闹钟在应用启动时已注册（如果提醒已启用）
        applicationScope.launch {
            val repository = CountdownRepository.getInstance(this@CountdownApplication)
            val data = repository.getCountdownDataSync()
            if (data.reminderEnabled) {
                AlarmScheduler.scheduleDailyAlarm(
                    this@CountdownApplication,
                    data.reminderTimeHour,
                    data.reminderTimeMinute
                )
            }
        }
    }
}
