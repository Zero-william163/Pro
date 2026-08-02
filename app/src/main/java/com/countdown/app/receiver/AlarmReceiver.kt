package com.countdown.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.countdown.app.data.CountdownRepository
import com.countdown.app.service.AlarmService
import com.countdown.app.util.AlarmScheduler
import com.countdown.app.util.DateCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟触发接收器（重构 v2）
 *
 * 职责：
 * - 接收每日闹钟广播
 * - 启动 AlarmService 播放声音、震动、显示全屏通知
 * - 自动重新注册第二天的闹钟
 * - 处理一次性闹钟（稍后提醒）：优先从 Intent extras 读取数据
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DAILY_REMINDER = "com.countdown.app.ACTION_DAILY_REMINDER"

        // Intent extras keys（用于稍后提醒传递数据）
        const val EXTRA_EVENT_CONTENT = "event_content"
        const val EXTRA_DAYS_REMAINING = "days_remaining"
        const val EXTRA_TARGET_REACHED = "target_reached"

        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DAILY_REMINDER) {
            Log.w(TAG, "Received unknown action: ${intent.action}")
            return
        }

        Log.d(TAG, "Alarm received at ${System.currentTimeMillis()}")

        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                // 检查 Intent 是否携带稍后提醒的数据
                val hasExtras = intent.hasExtra(EXTRA_EVENT_CONTENT)

                if (hasExtras) {
                    // 稍后提醒：直接使用 Intent extras 中的数据
                    val eventContent = intent.getStringExtra(EXTRA_EVENT_CONTENT) ?: "目标"
                    val daysRemaining = intent.getLongExtra(EXTRA_DAYS_REMAINING, 0)
                    val targetReached = intent.getBooleanExtra(EXTRA_TARGET_REACHED, false)

                    Log.d(TAG, "Snooze alarm: event=$eventContent, days=$daysRemaining, reached=$targetReached")

                    startAlarmService(context, eventContent, daysRemaining, targetReached)
                } else {
                    // 每日闹钟：从 Repository 读取数据
                    val repository = CountdownRepository.getInstance(context)
                    val data = repository.getCountdownDataSync()

                    // 检查提醒是否启用
                    if (!data.reminderEnabled) {
                        Log.d(TAG, "Reminder disabled, skipping")
                        return@launch
                    }

                    // 计算倒计时数据
                    val daysRemaining = DateCalculator.daysRemaining(data.targetDate)
                    val targetReached = DateCalculator.isTargetReached(data.targetDate)
                    val eventContent = data.eventContent.ifEmpty { "目标" }

                    Log.d(TAG, "Daily alarm: event=$eventContent, days=$daysRemaining, reached=$targetReached")

                    startAlarmService(context, eventContent, daysRemaining, targetReached)

                    // 重新注册明天的闹钟（仅每日闹钟需要重新注册）
                    AlarmScheduler.scheduleDailyAlarm(
                        context,
                        data.reminderTimeHour,
                        data.reminderTimeMinute
                    )
                    Log.d(TAG, "Rescheduled alarm for next day")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling alarm", e)
            }
        }
    }

    /**
     * 启动 AlarmService
     */
    private fun startAlarmService(
        context: Context,
        eventContent: String,
        daysRemaining: Long,
        targetReached: Boolean
    ) {
        val alarmIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START_ALARM
            putExtra(AlarmService.EXTRA_EVENT_CONTENT, eventContent)
            putExtra(AlarmService.EXTRA_DAYS_REMAINING, daysRemaining)
            putExtra(AlarmService.EXTRA_TARGET_REACHED, targetReached)
        }
        ContextCompat.startForegroundService(context, alarmIntent)
        Log.d(TAG, "AlarmService started")
    }
}
