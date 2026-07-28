package com.countdown.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
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
import com.countdown.app.util.AlarmScheduler

class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START_ALARM = "com.countdown.app.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.countdown.app.action.STOP_ALARM"

        const val EXTRA_EVENT_CONTENT = "event_content"
        const val EXTRA_DAYS_REMAINING = "days_remaining"
        const val EXTRA_TARGET_REACHED = "target_reached"

        // Waveform: wait 0ms, vibrate 800ms, pause 400ms, vibrate 800ms, pause 400ms, vibrate 800ms
        // repeat = 0 means loop from the beginning indefinitely
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 400, 800, 400, 800)
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            null -> {
                // Service was restarted by the system after being killed (START_STICKY).
                // Without intent data we cannot restart the alarm, so stop gracefully.
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_ALARM -> {
                stopAlarm()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_ALARM -> {
                val eventContent = intent.getStringExtra(EXTRA_EVENT_CONTENT) ?: ""
                val daysRemaining = intent.getLongExtra(EXTRA_DAYS_REMAINING, 0)
                val targetReached = intent.getBooleanExtra(EXTRA_TARGET_REACHED, false)

                val notification = buildNotification(eventContent, daysRemaining, targetReached)
                startForegroundCompat(notification)

                startSound()
                startVibration()
                Log.d(TAG, "Alarm started: event=$eventContent, days=$daysRemaining, reached=$targetReached")
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startSound() {
        if (mediaPlayer != null) return
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: run {
                    Log.e(TAG, "No alarm ringtone URI available")
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
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm sound", e)
        }
    }

    private fun startVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibrator = vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (!vibrator!!.hasVibrator()) {
                Log.d(TAG, "Device does not have a vibrator")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VIBRATION_PATTERN, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CountdownApp:AlarmWakeLock"
            )
            wakeLock?.acquire()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun buildNotification(
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

        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_EVENT_CONTENT, eventContent)
            putExtra(AlarmActivity.EXTRA_DAYS_REMAINING, daysRemaining)
            putExtra(AlarmActivity.EXTRA_TARGET_REACHED, targetReached)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            1,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AlarmScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.full_screen_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSound(null)
            .setVibrate(null)
            .build()
    }

    /**
     * Stop all alarm effects: sound, vibration, wake lock, and foreground state.
     */
    fun stopAlarm() {
        // Stop and release MediaPlayer
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer", e)
        }
        mediaPlayer = null

        // Cancel vibration
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling vibration", e)
        }
        vibrator = null

        // Release WakeLock
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

        // Remove the foreground notification
        stopForeground(STOP_FOREGROUND_REMOVE)

        Log.d(TAG, "Alarm stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
