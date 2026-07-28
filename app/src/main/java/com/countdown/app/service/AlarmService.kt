package com.countdown.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.countdown.app.R
import com.countdown.app.ui.alarm.AlarmActivity
import com.countdown.app.util.NotificationHelper

/**
 * 闹钟前台服务（已重构）
 *
 * 职责：
 * 1. 播放闹钟声音（循环 + 音频焦点）
 * 2. 触发震动（循环波形）
 * 3. 保持 WakeLock 防止设备休眠
 * 4. 显示前台通知（带全屏意图和操作按钮）
 * 5. 可靠的关闭逻辑（停止声音、震动、释放资源）
 * 6. 降级方案（当 FullScreenIntent 不可用时）
 */
class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START_ALARM = "com.countdown.app.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.countdown.app.action.STOP_ALARM"
        const val ACTION_CLOSE_ALARM_ACTIVITY = "com.countdown.app.action.CLOSE_ALARM_ACTIVITY"

        const val EXTRA_EVENT_CONTENT = "event_content"
        const val EXTRA_DAYS_REMAINING = "days_remaining"
        const val EXTRA_TARGET_REACHED = "target_reached"

        // 震动波形: [等待, 震动, 暂停, 震动, 暂停, 震动] (ms)
        // repeat = 0 表示从索引0开始无限循环
        private val VIBRATION_PATTERN = longArrayOf(0, 1000, 500, 1000, 500, 1000)
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var closeReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmService onCreate")
        acquireWakeLock()
        registerCloseReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            null -> {
                // 系统杀死后重启服务，但没有数据，直接停止
                Log.w(TAG, "Service restarted without intent, stopping")
                stopAlarmAndCleanup()
                return START_NOT_STICKY
            }
            ACTION_STOP_ALARM -> {
                Log.d(TAG, "Received STOP_ALARM action")
                stopAlarmAndCleanup()
                return START_NOT_STICKY
            }
            ACTION_START_ALARM -> {
                val eventContent = intent.getStringExtra(EXTRA_EVENT_CONTENT) ?: "目标"
                val daysRemaining = intent.getLongExtra(EXTRA_DAYS_REMAINING, 0)
                val targetReached = intent.getBooleanExtra(EXTRA_TARGET_REACHED, false)

                Log.d(TAG, "Starting alarm: event=$eventContent, days=$daysRemaining, reached=$targetReached")

                // 构建前台通知
                val notification = buildForegroundNotification(eventContent, daysRemaining, targetReached)
                startForegroundCompat(notification)

                // 显示全屏通知（系统级闹钟体验）
                NotificationHelper.showAlarmNotification(
                    this,
                    eventContent,
                    daysRemaining,
                    targetReached
                )

                // 播放声音
                startSoundWithAudioFocus()

                // 开始震动
                startVibration()

                // 如果是降级方案（无法全屏），尝试直接启动 AlarmActivity
                tryStartFullScreenActivity(eventContent, daysRemaining, targetReached)
            }
        }
        return START_STICKY
    }

    // ==================== 前台服务启动 ====================

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
            // 降级：如果前台服务失败，至少尝试显示通知
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    // ==================== 声音播放（带音频焦点） ====================

    private fun startSoundWithAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 请求音频焦点
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            Log.d(TAG, "Audio focus lost, pausing sound")
                            mediaPlayer?.pause()
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            Log.d(TAG, "Audio focus gained, resuming sound")
                            mediaPlayer?.start()
                        }
                    }
                }
                .build()
            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        startSound()
    }

    private fun startSound() {
        if (mediaPlayer != null) return

        try {
            // 优先使用闹钟铃声，其次通知铃声
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            if (alarmUri == null) {
                Log.e(TAG, "No default ringtone URI available")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
            Log.d(TAG, "Alarm sound started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm sound", e)
            // 降级：使用系统默认通知声音
            tryFallbackSound()
        }
    }

    private fun tryFallbackSound() {
        try {
            val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (fallbackUri != null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmService, fallbackUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d(TAG, "Fallback sound started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback sound also failed", e)
        }
    }

    // ==================== 震动控制 ====================

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator?.hasVibrator() != true) {
                Log.d(TAG, "Device has no vibrator")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VIBRATION_PATTERN, 0)
            }
            Log.d(TAG, "Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }
    }

    // ==================== WakeLock（防止设备休眠） ====================

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CountdownApp:AlarmWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 最长保持10分钟
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    // ==================== 注册关闭广播接收器 ====================

    private fun registerCloseReceiver() {
        closeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_CLOSE_ALARM_ACTIVITY) {
                    Log.d(TAG, "Received CLOSE_ALARM_ACTIVITY broadcast")
                    // 什么都不需要做，AlarmActivity 会自己处理关闭
                }
            }
        }
        val filter = IntentFilter(ACTION_CLOSE_ALARM_ACTIVITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(closeReceiver, filter)
        }
    }

    // ==================== 降级方案：直接启动 Activity ====================

    private fun tryStartFullScreenActivity(
        eventContent: String,
        daysRemaining: Long,
        targetReached: Boolean
    ) {
        // 如果设备不支持 FullScreenIntent 或者权限不足，尝试直接启动 AlarmActivity
        try {
            val activityIntent = Intent(this, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra(AlarmActivity.EXTRA_EVENT_CONTENT, eventContent)
                putExtra(AlarmActivity.EXTRA_DAYS_REMAINING, daysRemaining)
                putExtra(AlarmActivity.EXTRA_TARGET_REACHED, targetReached)
            }
            startActivity(activityIntent)
            Log.d(TAG, "Started AlarmActivity directly as fallback")
        } catch (e: Exception) {
            Log.w(TAG, "Could not start AlarmActivity directly", e)
        }
    }

    // ==================== 前台通知构建 ====================

    private fun buildForegroundNotification(
        eventContent: String,
        daysRemaining: Long,
        targetReached: Boolean
    ): Notification {
        val contentText = when {
            targetReached -> "【$eventContent】目标日期已到达！"
            daysRemaining == 0L -> "【$eventContent】就是今天！"
            daysRemaining < 0 -> "【$eventContent】已过去 ${-daysRemaining} 天"
            else -> "离【$eventContent】还有 $daysRemaining 天"
        }

        // 点击打开全屏界面
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_EVENT_CONTENT, eventContent)
            putExtra(AlarmActivity.EXTRA_DAYS_REMAINING, daysRemaining)
            putExtra(AlarmActivity.EXTRA_TARGET_REACHED, targetReached)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            10,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.alarm_service_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(null)
            .setVibrate(null)
            .build()
    }

    // ==================== 停止闹钟并清理资源 ====================

    private fun stopAlarmAndCleanup() {
        Log.d(TAG, "Stopping alarm and cleaning up resources")

        // 1. 停止并释放 MediaPlayer
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.reset()
                mp.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer", e)
        }
        mediaPlayer = null

        // 2. 取消震动
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling vibration", e)
        }
        vibrator = null

        // 3. 释放音频焦点
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus", e)
        }
        audioFocusRequest = null

        // 4. 释放 WakeLock
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
        wakeLock = null

        // 5. 取消通知
        NotificationHelper.cancelAlarmNotification(this)

        // 6. 停止前台服务
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }

        // 7. 停止服务自身
        stopSelf()

        Log.d(TAG, "Alarm stopped and all resources released")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AlarmService onDestroy")
        // 确保资源被释放
        try {
            mediaPlayer?.release()
            vibrator?.cancel()
            wakeLock?.let { if (it.isHeld) it.release() }
            closeReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy cleanup", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
