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
                val eventContent = data.eventContent.ifEmpty { "目标" }

                // Start AlarmService to play sound, vibrate, and show full-screen notification
                val alarmIntent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_START_ALARM
                    putExtra(AlarmService.EXTRA_EVENT_CONTENT, eventContent)
                    putExtra(AlarmService.EXTRA_DAYS_REMAINING, daysRemaining)
                    putExtra(AlarmService.EXTRA_TARGET_REACHED, targetReached)
                }
                ContextCompat.startForegroundService(context, alarmIntent)

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
}
