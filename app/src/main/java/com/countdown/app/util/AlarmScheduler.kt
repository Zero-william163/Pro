package com.countdown.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.countdown.app.receiver.AlarmReceiver

/**
 * 闹钟调度器（已重构）
 *
 * 支持：
 * - 每日重复闹钟（使用 setAlarmClock 最可靠）
 * - 一次性闹钟（用于稍后提醒）
 * - 多版本兼容（Android 8 ~ Android 16）
 * - 自动降级策略
 */
object AlarmScheduler {

    // 渠道 ID 统一在 NotificationHelper 中定义，此处不再重复
    private const val REQUEST_CODE_DAILY = 1001
    private const val REQUEST_CODE_ONESHOT = 1002
    private const val TAG = "AlarmScheduler"

    // ==================== 每日重复闹钟 ====================

    fun scheduleDailyAlarm(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 先取消已有的每日闹钟
        cancelDailyAlarm(context)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DAILY_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = DateCalculator.getNextAlarmTimeMillis(hour, minute)
        Log.d(TAG, "Scheduling daily alarm at ${DateCalculator.formatDateTime(triggerTime)}")

        try {
            scheduleExactAlarmCompat(alarmManager, triggerTime, pendingIntent)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling daily alarm", e)
            scheduleInexactAlarmFallback(alarmManager, triggerTime, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Exception scheduling daily alarm", e)
        }
    }

    fun cancelDailyAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DAILY_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Cancelled daily alarm")
    }

    // ==================== 一次性闹钟（稍后提醒用） ====================

    fun scheduleOneShotAlarm(
        context: Context,
        triggerTimeMillis: Long,
        eventContent: String = "",
        daysRemaining: Long = 0,
        targetReached: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DAILY_REMINDER
            putExtra(AlarmReceiver.EXTRA_EVENT_CONTENT, eventContent)
            putExtra(AlarmReceiver.EXTRA_DAYS_REMAINING, daysRemaining)
            putExtra(AlarmReceiver.EXTRA_TARGET_REACHED, targetReached)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ONESHOT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d(TAG, "Scheduling one-shot alarm at ${DateCalculator.formatDateTime(triggerTimeMillis)}")

        try {
            scheduleExactAlarmCompat(alarmManager, triggerTimeMillis, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule one-shot alarm", e)
            scheduleInexactAlarmFallback(alarmManager, triggerTimeMillis, pendingIntent)
        }
    }

    // ==================== 取消所有闹钟 ====================

    fun cancelAlarm(context: Context) {
        cancelDailyAlarm(context)

        // 同时取消一次性闹钟
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DAILY_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ONESHOT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    // ==================== 兼容性调度方法 ====================

    private fun scheduleExactAlarmCompat(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent
    ) {
        when {
            // Android 12+ (API 31+): 优先使用 setAlarmClock，最可靠
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled via setAlarmClock (API 31+)")
                } else {
                    // 没有精确闹钟权限，使用降级方案
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled via setAndAllowWhileIdle (no exact permission)")
                }
            }
            // Android 6+ (API 23+): setExactAndAllowWhileIdle
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled via setExactAndAllowWhileIdle (API 23+)")
            }
            // Android 5 (API 21-22): setExact
            else -> {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled via setExact")
            }
        }
    }

    private fun scheduleInexactAlarmFallback(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled fallback inexact alarm")
        } catch (e: Exception) {
            Log.e(TAG, "Fallback scheduling also failed", e)
        }
    }

    // ==================== 权限检测 ====================

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    // ==================== 打开设置页面 ====================

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
