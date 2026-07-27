package com.countdown.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.countdown.app.data.CountdownRepository
import com.countdown.app.util.AlarmScheduler
import com.countdown.app.util.DateCalculator
import com.countdown.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DAILY_REMINDER = "com.countdown.app.ACTION_DAILY_REMINDER"
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DAILY_REMINDER) return

        Log.d(TAG, "Alarm received at ${System.currentTimeMillis()}")

        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                val repository = CountdownRepository.getInstance(context)
                val data = repository.getCountdownDataSync()

                if (!data.reminderEnabled) {
                    Log.d(TAG, "Reminder disabled, skipping")
                    return@launch
                }

                val daysRemaining = DateCalculator.daysRemaining(data.targetDate)
                val targetReached = DateCalculator.isTargetReached(data.targetDate)

                // Play sound and vibrate
                playAlarmEffects(context)

                // Show notification (with full-screen intent)
                NotificationHelper.showReminderNotification(
                    context,
                    data.eventContent.ifEmpty { "目标" },
                    daysRemaining,
                    targetReached
                )

                // Reschedule for next day
                AlarmScheduler.scheduleDailyAlarm(
                    context,
                    data.reminderTimeHour,
                    data.reminderTimeMinute
                )
                Log.d(TAG, "Rescheduled alarm for next day")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling alarm", e)
            }
        }
    }

    private fun playAlarmEffects(context: Context) {
        try {
            // Vibrate
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 500), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }

        try {
            // Play notification sound
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notification)
            ringtone?.let { rt ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    rt.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                rt.play()
                // Stop after 3 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try { rt.stop() } catch (_: Exception) {}
                }, 3000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sound play failed", e)
        }
    }
}
