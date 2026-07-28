package com.countdown.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.countdown.app.service.AlarmService
import com.countdown.app.util.AlarmScheduler
import com.countdown.app.util.NotificationHelper

/**
 * 处理通知操作按钮的广播接收器
 * - 关闭闹钟 (Dismiss)
 * - 稍后提醒 (Snooze)
 */
class AlarmActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmActionReceiver"

        const val ACTION_DISMISS = "com.countdown.app.action.DISMISS_ALARM"
        const val ACTION_SNOOZE = "com.countdown.app.action.SNOOZE_ALARM"

        const val EXTRA_EVENT_CONTENT = "event_content"
        const val EXTRA_DAYS_REMAINING = "days_remaining"
        const val EXTRA_TARGET_REACHED = "target_reached"

        // 稍后提醒时间：5分钟
        private const val SNOOZE_MILLIS = 5 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS -> handleDismiss(context)
            ACTION_SNOOZE -> handleSnooze(context, intent)
            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    /**
     * 关闭闹钟：停止声音、震动、通知、服务
     */
    private fun handleDismiss(context: Context) {
        Log.d(TAG, "Dismiss alarm requested from notification action")

        // 1. 停止 AlarmService
        val stopIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        ContextCompat.startForegroundService(context, stopIntent)

        // 2. 取消通知
        NotificationHelper.cancelAlarmNotification(context)

        // 3. 发送广播关闭 AlarmActivity
        val closeActivityIntent = Intent(AlarmService.ACTION_CLOSE_ALARM_ACTIVITY)
        closeActivityIntent.setPackage(context.packageName)
        context.sendBroadcast(closeActivityIntent)

        Log.d(TAG, "Alarm dismissed successfully")
    }

    /**
     * 稍后提醒：停止当前闹钟，5分钟后再次触发
     */
    private fun handleSnooze(context: Context, intent: Intent) {
        Log.d(TAG, "Snooze alarm requested")

        // 1. 先关闭当前闹钟
        handleDismiss(context)

        // 2. 获取事件信息
        val eventContent = intent.getStringExtra(EXTRA_EVENT_CONTENT) ?: "目标"
        val daysRemaining = intent.getLongExtra(EXTRA_DAYS_REMAINING, 0)
        val targetReached = intent.getBooleanExtra(EXTRA_TARGET_REACHED, false)

        // 3. 发送稍后提醒通知
        val snoozeTime = System.currentTimeMillis() + SNOOZE_MILLIS
        NotificationHelper.showSnoozeScheduledNotification(
            context,
            eventContent,
            snoozeTime
        )

        // 4. 安排5分钟后再次触发
        AlarmScheduler.scheduleOneShotAlarm(
            context,
            snoozeTime,
            eventContent = eventContent,
            daysRemaining = daysRemaining,
            targetReached = targetReached
        )

        Log.d(TAG, "Snooze scheduled for 5 minutes later")
    }
}
