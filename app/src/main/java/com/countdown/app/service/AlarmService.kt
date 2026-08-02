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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.countdown.app.data.CountdownData
import com.countdown.app.data.CountdownRepository
import com.countdown.app.util.NotificationHelper
import com.countdown.app.util.PermissionChecker
import com.countdown.app.util.RingtoneManager as AppRingtoneManager

/**
 * 闹钟前台服务（重构 v4）
 *
 * 核心修复：
 * 1. WakeLock 时序修复：先发布通知（触发 FullScreenIntent），再唤醒屏幕
 *    - 之前 ACQUIRE_CAUSES_WAKEUP 在 onCreate 提前唤醒屏幕
 *    - 导致通知发布时屏幕已亮，FullScreenIntent 被降级为 Heads-up
 *    - 修复：先 PARTIAL_WAKE_LOCK 保持 CPU，通知发布后再唤醒屏幕
 *
 * 2. 停止服务崩溃修复：STOP_ALARM 时先 startForeground 再 stopForeground
 *    - 之前 startForegroundService 发送 STOP 后直接 stopForeground
 *    - Android 8+ 要求 startForegroundService 后必须调用 startForeground
 *    - 修复：STOP 时也先调用 startForeground，再停止
 *
 * 3. FullScreenIntent 回退机制：2 秒后检测 Activity 是否启动
 *    - 如果 FullScreenIntent 未触发（厂商限制等），手动启动 AlarmActivity
 *    - 添加详细诊断日志分析失败原因
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
        private val VIBRATION_PATTERN = longArrayOf(0, 1000, 500, 1000, 500, 1000)

        // FullScreenIntent 回退检测延迟（1.5秒，平衡响应速度和 FullScreenIntent 触发时间）
        private const val FULLSCREEN_FALLBACK_DELAY_MS = 1500L

        // AlarmActivity 是否已启动的标志（由 AlarmActivity 设置）
        @Volatile
        @JvmStatic
        var isAlarmActivityActive = false
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmService onCreate")
        // 安全网：确保通知渠道已创建（防止 Application 未初始化或被系统杀死后重建）
        NotificationHelper.createNotificationChannels(this)
        // 只获取 PARTIAL_WAKE_LOCK 保持 CPU 运行，不提前唤醒屏幕
        acquirePartialWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            null -> {
                Log.w(TAG, "Service restarted without intent, stopping")
                stopAlarmAndCleanup()
                return START_NOT_STICKY
            }
            ACTION_STOP_ALARM -> {
                Log.d(TAG, "Received STOP_ALARM action")
                // 【关键修复】startForegroundService 必须先调用 startForeground
                // 否则 Android 8+ 会抛出 ForegroundServiceDidNotStartInTimeException
                startForegroundCompatForStop()
                stopAlarmAndCleanup()
                return START_NOT_STICKY
            }
            ACTION_START_ALARM -> {
                val eventContent = intent.getStringExtra(EXTRA_EVENT_CONTENT) ?: "目标"
                val daysRemaining = intent.getLongExtra(EXTRA_DAYS_REMAINING, 0)
                val targetReached = intent.getBooleanExtra(EXTRA_TARGET_REACHED, false)

                Log.i(TAG, "=== 开始闹钟触发 ===")
                Log.i(TAG, "事件: $eventContent, 剩余天数: $daysRemaining, 已到达: $targetReached")
                Log.i(TAG, "设备品牌: ${PermissionChecker.getDeviceBrand()}")
                Log.i(TAG, "Android 版本: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")

                // 诊断：全面分析 FullScreenIntent 相关权限和状态
                val diag = analyzeFullScreenIntent()
                logDiagnostics(diag)

                // 重置 Activity 标志
                isAlarmActivityActive = false

                // 构建通知（包含 FullScreenIntent）
                val notification = NotificationHelper.buildAlarmNotification(
                    this,
                    eventContent,
                    daysRemaining,
                    targetReached
                )

                // 先调用 startForeground 发布通知
                startForegroundCompat(notification)
                Log.i(TAG, "前台服务通知已发布，FullScreenIntent 已附加")

                // 播放声音
                startSoundWithAudioFocus()

                // 开始震动
                startVibration()

                // 延迟唤醒屏幕（让 FullScreenIntent 先尝试触发）
                handler.postDelayed({
                    acquireScreenWakeLock()
                }, 500)

                // FullScreenIntent 回退机制：1.5秒后检测 Activity 是否启动
                handler.postDelayed({
                    if (!isAlarmActivityActive) {
                        Log.w(TAG, "=== FullScreenIntent 未触发，启动回退机制 ===")
                        startAlarmActivityFallback(eventContent, daysRemaining, targetReached, diag)
                    } else {
                        Log.i(TAG, "AlarmActivity 已通过 FullScreenIntent 启动")
                    }
                }, FULLSCREEN_FALLBACK_DELAY_MS)
            }
        }
        return START_STICKY
    }

    // ==================== FullScreenIntent 全面诊断分析 ====================

    /**
     * 诊断结果数据类
     */
    data class DiagnosticResult(
        val notificationEnabled: Boolean,
        val channelExists: Boolean,
        val channelImportance: Int,
        val channelImportanceSufficient: Boolean,
        val canUseFullScreenIntent: Boolean,
        val canScheduleExactAlarms: Boolean,
        val isIgnoringBatteryOptimizations: Boolean,
        val isScreenOn: Boolean,
        val isLocked: Boolean,
        val expectedBehavior: String,
        val failureReasons: List<String>,
        val missingPermissions: List<String>
    )

    /**
     * 全面分析 FullScreenIntent 是否能成功触发
     * 返回诊断结果，包含失败原因和缺失权限
     */
    private fun analyzeFullScreenIntent(): DiagnosticResult {
        val reasons = mutableListOf<String>()
        val missingPerms = mutableListOf<String>()

        // 1. 通知权限
        val notifEnabled = NotificationHelper.areNotificationsEnabled(this)
        if (!notifEnabled) {
            reasons.add("通知权限未开启：系统将拦截所有通知，FullScreenIntent 无法触发")
            missingPerms.add("通知权限")
        }

        // 2. 通知渠道
        var channelExists = false
        var channelImportance = 0
        var channelImportanceSufficient = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = nm.getNotificationChannel(NotificationHelper.CHANNEL_ID_ALARM)
            channelExists = channel != null
            if (channel != null) {
                channelImportance = channel.importance
                channelImportanceSufficient = channel.importance >= NotificationManager.IMPORTANCE_HIGH
                if (!channelImportanceSufficient) {
                    reasons.add("闹钟通知渠道优先级不足：当前=${channel.importance}，需要 IMPORTANCE_HIGH(${NotificationManager.IMPORTANCE_HIGH})")
                }
                if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                    reasons.add("闹钟通知渠道被用户完全关闭")
                    missingPerms.add("闹钟通知渠道")
                }
            } else {
                reasons.add("闹钟通知渠道不存在：通知将无法显示")
            }
        } else {
            channelExists = true
            channelImportanceSufficient = true
        }

        // 3. 全屏通知权限 (Android 14+)
        var canUseFSI = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            canUseFSI = nm.canUseFullScreenIntent()
            if (!canUseFSI) {
                reasons.add("USE_FULL_SCREEN_INTENT 权限未授予（Android 14+ 要求）：系统将忽略 FullScreenIntent")
                missingPerms.add("全屏通知权限")
            }
        }

        // 4. 精确闹钟权限
        var canSchedule = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            canSchedule = am.canScheduleExactAlarms()
            if (!canSchedule) {
                reasons.add("精确闹钟权限未授予：闹钟可能延迟触发")
                missingPerms.add("精确闹钟权限")
            }
        }

        // 5. 电池优化
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
        if (!ignoringBattery) {
            reasons.add("未忽略电池优化：系统可能在后台杀死应用，导致闹钟不触发")
            missingPerms.add("忽略电池优化")
        }

        // 6. 屏幕和锁屏状态
        val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val isLocked = km.isKeyguardLocked
        val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            pm.isInteractive
        } else {
            @Suppress("DEPRECATION")
            pm.isScreenOn
        }

        val expectedBehavior = when {
            !notifEnabled -> "完全阻止（通知权限关闭）"
            !channelExists -> "完全阻止（渠道不存在）"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !canUseFSI -> "降级为横幅通知（缺少全屏权限）"
            isScreenOn && !isLocked -> "降级为 Heads-up 横幅通知（屏幕亮且未锁屏，属正常行为）"
            else -> "应触发全屏界面"
        }

        // 7. 厂商限制提示
        val brand = PermissionChecker.getDeviceBrand()
        if (brand == "华为" || brand == "荣耀") {
            if (!PermissionChecker.isIgnoringBatteryOptimizations(this)) {
                reasons.add("华为/荣耀设备限制：建议在「应用启动管理」中允许自启动和后台活动")
            }
        } else if (brand == "小米" || brand == "红米") {
            reasons.add("小米/红米设备限制：建议在「自启动管理」中允许自启动")
        }

        return DiagnosticResult(
            notificationEnabled = notifEnabled,
            channelExists = channelExists,
            channelImportance = channelImportance,
            channelImportanceSufficient = channelImportanceSufficient,
            canUseFullScreenIntent = canUseFSI,
            canScheduleExactAlarms = canSchedule,
            isIgnoringBatteryOptimizations = ignoringBattery,
            isScreenOn = isScreenOn,
            isLocked = isLocked,
            expectedBehavior = expectedBehavior,
            failureReasons = reasons,
            missingPermissions = missingPerms
        )
    }

    /**
     * 输出诊断日志（详细分析）
     */
    private fun logDiagnostics(diag: DiagnosticResult) {
        Log.i(TAG, "========== FullScreenIntent 诊断分析 ==========")
        Log.i(TAG, "设备品牌: ${PermissionChecker.getDeviceBrand()}")
        Log.i(TAG, "Android 版本: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        Log.i(TAG, "--- 权限状态 ---")
        Log.i(TAG, "  通知权限: ${if (diag.notificationEnabled) "已开启" else "未开启"}")
        Log.i(TAG, "  闹钟渠道存在: ${diag.channelExists}")
        Log.i(TAG, "  渠道优先级: ${diag.channelImportance} (需要 >= ${NotificationManager.IMPORTANCE_HIGH})")
        Log.i(TAG, "  渠道优先级充足: ${diag.channelImportanceSufficient}")
        Log.i(TAG, "  全屏通知权限: ${if (diag.canUseFullScreenIntent) "已开启" else "未开启"}")
        Log.i(TAG, "  精确闹钟权限: ${if (diag.canScheduleExactAlarms) "已开启" else "未开启"}")
        Log.i(TAG, "  忽略电池优化: ${if (diag.isIgnoringBatteryOptimizations) "已开启" else "未开启"}")
        Log.i(TAG, "--- 屏幕状态 ---")
        Log.i(TAG, "  屏幕状态: ${if (diag.isScreenOn) "亮屏" else "息屏"}")
        Log.i(TAG, "  锁屏状态: ${if (diag.isLocked) "已锁" else "未锁"}")
        Log.i(TAG, "--- 预期行为 ---")
        Log.i(TAG, "  FullScreenIntent 预期: ${diag.expectedBehavior}")

        if (diag.failureReasons.isNotEmpty()) {
            Log.w(TAG, "--- 失败原因分析 ---")
            diag.failureReasons.forEachIndexed { i, reason ->
                Log.w(TAG, "  ${i + 1}. $reason")
            }
        } else {
            Log.i(TAG, "--- 未检测到明显失败原因 ---")
        }

        if (diag.missingPermissions.isNotEmpty()) {
            Log.w(TAG, "--- 缺失权限 ---")
            diag.missingPermissions.forEach {
                Log.w(TAG, "  - $it")
            }
        }
        Log.i(TAG, "========== 诊断结束 ==========")
    }

    // ==================== FullScreenIntent 回退：手动启动 Activity + 诊断通知 ====================

    private fun startAlarmActivityFallback(
        eventContent: String,
        daysRemaining: Long,
        targetReached: Boolean,
        diag: DiagnosticResult
    ) {
        Log.w(TAG, "=== FullScreenIntent 回退机制启动 ===")
        Log.w(TAG, "AlarmActivity 未在 ${FULLSCREEN_FALLBACK_DELAY_MS}ms 内启动")
        Log.w(TAG, "预期行为: ${diag.expectedBehavior}")

        try {
            val intent = android.content.Intent(this, com.countdown.app.ui.alarm.AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_EVENT_CONTENT, eventContent)
                putExtra(EXTRA_DAYS_REMAINING, daysRemaining)
                putExtra(EXTRA_TARGET_REACHED, targetReached)
            }
            startActivity(intent)
            Log.i(TAG, "回退：AlarmActivity 已手动启动")
        } catch (e: Exception) {
            Log.e(TAG, "回退：手动启动 AlarmActivity 失败", e)
            Log.e(TAG, "这通常是因为厂商系统限制了后台启动 Activity")
            Log.e(TAG, "建议用户检查：")
            Log.e(TAG, "  - 华为：应用启动管理 → 允许自启动 + 后台活动")
            Log.e(TAG, "  - 小米：自启动管理 → 允许自启动")
            Log.e(TAG, "  - OPPO/vivo：自启动/后台弹窗管理")

            // 向用户显示诊断通知（不静默失败）
            if (diag.failureReasons.isNotEmpty() || diag.missingPermissions.isNotEmpty()) {
                NotificationHelper.showDiagnosticNotification(
                    this,
                    reasons = diag.failureReasons.ifEmpty {
                        listOf(
                            "厂商系统限制了后台启动 Activity",
                            "当前设备: ${PermissionChecker.getDeviceBrand()}",
                            "请在系统设置中允许本应用自启动和后台活动"
                        )
                    },
                    missingPermissions = diag.missingPermissions
                )
            }
        }
    }

    // ==================== 前台服务启动 ====================

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.ALARM_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NotificationHelper.ALARM_NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Foreground service started with alarm notification")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NotificationHelper.ALARM_NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "Even notification fallback failed", e2)
            }
        }
    }

    /**
     * 为 STOP 操作调用 startForeground
     *
     * 当通过 startForegroundService 启动服务时（即使服务已在运行），
     * Android 8+ 要求在 onStartCommand 中必须调用 startForeground。
     * 否则会抛出 ForegroundServiceDidNotStartInTimeException。
     */
    private fun startForegroundCompatForStop() {
        try {
            // 使用最小化通知满足 startForegroundService 要求，避免闪烁
            val notification = NotificationHelper.buildStopNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.ALARM_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NotificationHelper.ALARM_NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "startForeground called for STOP action (minimal notification)")
        } catch (e: Exception) {
            Log.w(TAG, "startForeground for STOP failed (service may already be stopping)", e)
        }
    }

    // ==================== 声音播放（带音频焦点） ====================

    private fun startSoundWithAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
            // 检查当前铃声是否有效（用户可能删除了文件或权限失效）
            val isRingtoneValid = AppRingtoneManager.isCurrentRingtoneValid(this)
            if (!isRingtoneValid) {
                // 铃声失效，提示用户已恢复默认
                Log.w(TAG, "Custom ringtone is invalid, falling back to default")
                handler.post {
                    Toast.makeText(
                        this@AlarmService,
                        "当前铃声不存在，已恢复为默认闹钟铃声",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // 获取铃声 URI（RingtoneManager 会自动处理回退到默认）
            val alarmUri = AppRingtoneManager.getAlarmRingtoneUri(this)

            Log.d(TAG, "Using ringtone URI: $alarmUri")

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
            tryFallbackSound()
        }
    }

    private fun tryFallbackSound() {
        try {
            val fallbackUri = AppRingtoneManager.getDefaultAlarmUri()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, fallbackUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.d(TAG, "Fallback sound started with default alarm ringtone")
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

    // ==================== WakeLock ====================

    /**
     * PARTIAL_WAKE_LOCK：只保持 CPU 运行，不点亮屏幕
     * 在 onCreate 时获取，确保闹钟逻辑能执行
     */
    private fun acquirePartialWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CountdownApp:AlarmCpuWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L)
            Log.d(TAG, "Partial WakeLock acquired (CPU only)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire partial wake lock", e)
        }
    }

    /**
     * SCREEN_BRIGHT_WAKE_LOCK：点亮屏幕并保持常亮
     * 在通知发布后延迟获取，避免干扰 FullScreenIntent
     */
    private fun acquireScreenWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CountdownApp:AlarmScreenWakeLock"
            )
            screenWakeLock?.acquire(10 * 60 * 1000L)
            Log.d(TAG, "Screen WakeLock acquired (screen on)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire screen wake lock", e)
        }
    }

    // ==================== 停止闹钟并清理所有资源 ====================

    private fun stopAlarmAndCleanup() {
        Log.d(TAG, "=== 停止闹钟，清理所有资源 ===")

        // 移除所有延迟任务
        handler.removeCallbacksAndMessages(null)

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
            Log.e(TAG, "Error releasing partial wake lock", e)
        }
        wakeLock = null

        try {
            screenWakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing screen wake lock", e)
        }
        screenWakeLock = null

        // 5. 发送广播关闭 AlarmActivity（如果还在显示）
        try {
            val closeIntent = Intent(ACTION_CLOSE_ALARM_ACTIVITY).apply {
                setPackage(packageName)
            }
            sendBroadcast(closeIntent)
            Log.d(TAG, "Sent close broadcast to AlarmActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending close broadcast", e)
        }

        // 6. 取消所有闹钟相关通知（闹钟通知 + 诊断通知）
        NotificationHelper.cancelAllAlarmNotifications(this)

        // 7. 停止前台服务
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }

        // 8. 停止服务自身
        stopSelf()

        Log.d(TAG, "=== 闹钟已停止，所有资源已释放 ===")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AlarmService onDestroy")
        handler.removeCallbacksAndMessages(null)
        try {
            mediaPlayer?.release()
            vibrator?.cancel()
            wakeLock?.let { if (it.isHeld) it.release() }
            screenWakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy cleanup", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
