package com.countdown.app.service

import android.app.Notification
import android.app.NotificationManager
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
import com.countdown.app.util.NotificationHelper

/**
 * 闹钟前台服务（重构 v3）
 *
 * 核心设计原则（遵循 Android 官方最佳实践）：
 *
 * 1. 【单一通知原则】前台服务通知 = 唯一的闹钟通知，不再创建第二个通知
 *    - 之前创建两个通知（前台通知 + showAlarmNotification），两个都有 FullScreenIntent
 *    - 系统遇到多个 FullScreenIntent 时会冲突，导致全屏界面无法弹出
 *
 * 2. 【FullScreenIntent 自行处理】不手动 startActivity 启动 AlarmActivity
 *    - Android 官方文档：setFullScreenIntent() 在锁屏时会自动启动全屏 Activity
 *    - 手动 startActivity 会与 FullScreenIntent 冲突，导致行为不确定
 *    - 非锁屏时，FullScreenIntent 降级为 Heads-up Notification（正确行为）
 *
 * 3. 【声音/震动由 Service 直接控制】通知渠道不设声音和震动
 *    - 避免 MediaPlayer 声音 + 渠道声音 = 双重声音
 *    - MediaPlayer 使用 USAGE_ALARM 属性，系统级闹钟体验
 *
 * 4. 【可靠资源清理】关闭时释放所有资源
 *    - MediaPlayer.release()
 *    - Vibrator.cancel()
 *    - WakeLock.release()
 *    - AudioFocus.abandon()
 *    - Notification.cancel()
 *    - stopForeground() + stopSelf()
 */
class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"

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

                // 【关键】使用 NotificationHelper 构建唯一的通知
                // 此通知同时用作：
                // - 前台服务通知（startForeground）
                // - FullScreenIntent 载体（锁屏时自动弹出全屏界面）
                // - 通知操作按钮（关闭/稍后提醒）
                val notification = NotificationHelper.buildAlarmNotification(
                    this,
                    eventContent,
                    daysRemaining,
                    targetReached
                )
                startForegroundCompat(notification)

                // 播放声音（MediaPlayer + 音频焦点）
                startSoundWithAudioFocus()

                // 开始震动
                startVibration()

                // 【关键】不手动启动 AlarmActivity！
                // FullScreenIntent 会自动处理：
                // - 锁屏时：直接启动全屏 AlarmActivity
                // - 非锁屏时：显示 Heads-up Notification（横幅通知）
                // 手动 startActivity 会与 FullScreenIntent 冲突，导致系统不确定行为
            }
        }
        return START_STICKY
    }

    // ==================== 前台服务启动 ====================

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ 必须指定前台服务类型
                startForeground(
                    NotificationHelper.ALARM_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NotificationHelper.ALARM_NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Foreground service started with unified alarm notification")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            // 降级：如果前台服务失败，至少尝试显示通知
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NotificationHelper.ALARM_NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "Even notification fallback failed", e2)
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
                    // AlarmActivity 会自行处理关闭，这里不需要做额外操作
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

    // ==================== 停止闹钟并清理所有资源 ====================

    private fun stopAlarmAndCleanup() {
        Log.d(TAG, "Stopping alarm and cleaning up all resources")

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

        // 5. 取消通知（使用统一 ID）
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
