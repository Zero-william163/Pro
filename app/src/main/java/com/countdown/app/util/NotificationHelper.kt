package com.countdown.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
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
 * 通知帮助类（重构 v2）
 *
 * 核心设计原则：
 * 1. 闹钟前台服务通知 = 唯一的闹钟通知（不再创建第二个通知）
 * 2. 闹钟渠道不设声音/震动（由 AlarmService 通过 MediaPlayer/Vibrator 直接控制）
 * 3. FullScreenIntent 只出现在唯一的通知上
 * 4. 通知操作按钮（关闭/稍后提醒）直接在通知上
 *
 * 之前的 bug：
 * - 创建了两个通知（前台通知 + showAlarmNotification），两个都有 FullScreenIntent，导致系统不触发
 * - 渠道有声音 + MediaPlayer 也有声音 = 双重声音
 */
object NotificationHelper {

    // ==================== 通知渠道 ID ====================
    const val CHANNEL_ID_ALARM = "countdown_alarm_channel"
    const val CHANNEL_ID_REMINDER = "countdown_reminder_channel"
    const val CHANNEL_ID_SNOOZE = "countdown_snooze_channel"

    // ==================== 统一通知 ID ====================
    /**
     * 闹钟通知的唯一 ID。
     * AlarmService 前台通知和取消操作都使用此 ID。
     */
    const val ALARM_NOTIFICATION_ID = 2001

    private const val NOTIFICATION_ID_REMINDER = 3002
    private const val NOTIFICATION_ID_SNOOZE = 3003

    // ==================== 初始化通知渠道 ====================

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. 闹钟渠道 - IMPORTANCE_HIGH（用于触发 Heads-up 和 FullScreenIntent）
        //    【关键】不设声音和震动，由 AlarmService 通过 MediaPlayer/Vibrator 直接控制
        //    这样避免渠道声音 + MediaPlayer 声音的双重播放
        val alarmChannel = NotificationChannel(
            CHANNEL_ID_ALARM,
            context.getString(R.string.channel_name_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_desc_alarm)
            // 不设声音 - 由 Service 的 MediaPlayer 播放
            // 不设震动 - 由 Service 的 Vibrator 控制
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true) // 闹钟绕过勿扰模式
            enableLights(true) // 闪烁呼吸灯
            lightColor = 0xFFFF5252.toInt()
        }

        // 2. 普通提醒渠道 - 默认优先级
        val reminderChannel = NotificationChannel(
            CHANNEL_ID_REMINDER,
            context.getString(R.string.channel_name_reminder),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_desc_reminder)
        }

        // 3. 稍后提醒渠道 - 低优先级
        val snoozeChannel = NotificationChannel(
            CHANNEL_ID_SNOOZE,
            context.getString(R.string.channel_name_snooze),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_desc_snooze)
        }

        notificationManager.createNotificationChannels(listOf(alarmChannel, reminderChannel, snoozeChannel))
    }

    // ==================== 构建闹钟通知（供 AlarmService 前台服务使用） ====================

    /**
     * 构建唯一的闹钟通知。
     *
     * 此通知同时用作：
     * - 前台服务通知（startForeground）
     * - FullScreenIntent 载体
     * - 通知操作按钮（关闭/稍后提醒）
     *
     * 声音和震动由 AlarmService 直接控制，不通过渠道。
     */
    fun buildAlarmNotification(
        context: Context,
        eventContent: String,
        daysRemaining: Long,
        targetReached: Boolean
    ): Notification {
        val contentText = buildContentText(eventContent, daysRemaining, targetReached)

        // --- FullScreenIntent: 锁屏时直接弹出全屏闹钟界面 ---
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
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

        // --- ContentIntent: 点击通知内容打开主应用 ---
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
            // 【核心】FullScreenIntent - 锁屏时直接弹出全屏界面
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // 声音和震动不通过通知控制（由 Service 的 MediaPlayer/Vibrator 处理）
            .setSound(null)
            .setVibrate(longArrayOf(0L))
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

        // Android 8 以下设置默认声音和震动（不会有渠道覆盖）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setDefaults(0) // 由 Service 控制
        }

        return builder.build()
    }

    // ==================== 普通提醒通知（无声音，仅提示） ====================

    fun showReminderNotification(
        context: Context,
        eventContent: String,
        daysRemaining: Long,
        targetReached: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentText = buildContentText(eventContent, daysRemaining, targetReached)

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

    /**
     * 取消闹钟通知（统一 ID）
     */
    fun cancelAlarmNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ALARM_NOTIFICATION_ID)
    }

    fun cancelAllNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }

    // ==================== 权限检测 ====================

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun isAlarmChannelBlocked(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID_ALARM)
            channel?.importance == NotificationManager.IMPORTANCE_NONE
        } else {
            false
        }
    }

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

    // ==================== 辅助方法 ====================

    private fun buildContentText(
        eventContent: String,
        daysRemaining: Long,
        targetReached: Boolean
    ): String {
        return when {
            targetReached -> "【$eventContent】目标日期已到达！"
            daysRemaining == 0L -> "【$eventContent】就是今天！"
            daysRemaining < 0 -> "【$eventContent】已过去 ${-daysRemaining} 天"
            else -> "离【$eventContent】还有 $daysRemaining 天"
        }
    }
}
