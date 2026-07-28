package com.countdown.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.countdown.app.MainActivity
import com.countdown.app.R
import com.countdown.app.receiver.AlarmActionReceiver
import com.countdown.app.ui.alarm.AlarmActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知帮助类（已重构）
 * 支持：
 * - 真正的 FullScreenIntent 通知
 * - 多通知渠道（闹钟渠道 + 普通提醒渠道）
 * - 通知操作按钮（关闭 / 稍后提醒）
 * - 高优先级 + 横幅 (Heads-up)
 * - 降级方案
 */
object NotificationHelper {

    // ==================== 通知渠道 ID ====================
    const val CHANNEL_ID_ALARM = "countdown_alarm_channel"
    const val CHANNEL_ID_REMINDER = "countdown_reminder_channel"
    const val CHANNEL_ID_SNOOZE = "countdown_snooze_channel"

    // ==================== 通知 ID ====================
    private const val NOTIFICATION_ID_ALARM = 3001
    private const val NOTIFICATION_ID_REMINDER = 3002
    private const val NOTIFICATION_ID_SNOOZE = 3003

    // ==================== 初始化通知渠道 ====================

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. 闹钟渠道 - 最高优先级，带声音和震动
        val alarmChannel = NotificationChannel(
            CHANNEL_ID_ALARM,
            context.getString(R.string.channel_name_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_desc_alarm)
            setSound(
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // 2. 普通提醒渠道 - 默认优先级
        val reminderChannel = NotificationChannel(
            CHANNEL_ID_REMINDER,
            context.getString(R.string.channel_name_reminder),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_desc_reminder)
        }

        // 3. 稍后提醒渠道 - 低优先级，仅提示
        val snoozeChannel = NotificationChannel(
            CHANNEL_ID_SNOOZE,
            context.getString(R.string.channel_name_snooze),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_desc_snooze)
        }

        notificationManager.createNotificationChannels(listOf(alarmChannel, reminderChannel, snoozeChannel))
    }

    // ==================== 核心闹钟通知（带 FullScreenIntent） ====================

    fun showAlarmNotification(
        context: Context,
        eventContent: String,
        daysRemaining: Long,
        targetReached: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentText = when {
            targetReached -> "【$eventContent】目标日期已到达！"
            daysRemaining == 0L -> "【$eventContent】就是今天！"
            daysRemaining < 0 -> "【$eventContent】已过去 ${-daysRemaining} 天"
            else -> "离【$eventContent】还有 ${daysRemaining} 天"
        }

        // --- PendingIntent: 点击通知打开全屏闹钟界面 ---
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(AlarmActivity.EXTRA_EVENT_CONTENT, eventContent)
            putExtra(AlarmActivity.EXTRA_DAYS_REMAINING, daysRemaining)
            putExtra(AlarmActivity.EXTRA_TARGET_REACHED, targetReached)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- PendingIntent: 点击通知内容打开主应用 ---
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- Action: 关闭闹钟 ---
        val dismissIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- Action: 稍后提醒 ---
        val snoozeIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_SNOOZE
            putExtra(AlarmActionReceiver.EXTRA_EVENT_CONTENT, eventContent)
            putExtra(AlarmActionReceiver.EXTRA_DAYS_REMAINING, daysRemaining)
            putExtra(AlarmActionReceiver.EXTRA_TARGET_REACHED, targetReached)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- 构建通知 ---
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.alarm_notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // FullScreenIntent：这是实现系统级闹钟体验的核心
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // 操作按钮
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.action_dismiss),
                dismissPendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.action_snooze),
                snoozePendingIntent
            )

        // 对于 Android 8 以下，设置默认声音和震动
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
        }

        notificationManager.notify(NOTIFICATION_ID_ALARM, builder.build())
    }

    // ==================== 普通提醒通知（无声音，仅提示） ====================

    fun showReminderNotification(
        context: Context,
        eventContent: String,
        daysRemaining: Long,
        targetReached: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentText = when {
            targetReached -> "【$eventContent】目标日期已到达！"
            daysRemaining == 0L -> "【$eventContent】就是今天！"
            daysRemaining < 0 -> "【$eventContent】已过去 ${-daysRemaining} 天"
            else -> "离【$eventContent】还有 ${daysRemaining} 天"
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            4,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID_REMINDER, builder.build())
    }

    // ==================== 稍后提醒已安排通知 ====================

    fun showSnoozeScheduledNotification(
        context: Context,
        eventContent: String,
        snoozeTimeMillis: Long
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(snoozeTimeMillis))

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SNOOZE)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(context.getString(R.string.snooze_scheduled_title))
            .setContentText("【$eventContent】将在 $timeStr 再次提醒")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID_SNOOZE, builder.build())
    }

    // ==================== 取消通知 ====================

    fun cancelAlarmNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_ALARM)
    }

    fun cancelAllNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }

    // ==================== 权限检测 ====================

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 检测闹钟通知渠道是否被关闭
     */
    fun isAlarmChannelBlocked(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID_ALARM)
            channel?.importance == NotificationManager.IMPORTANCE_NONE
        } else {
            false
        }
    }

    /**
     * 打开通知渠道设置
     */
    fun openAlarmChannelSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID_ALARM)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
