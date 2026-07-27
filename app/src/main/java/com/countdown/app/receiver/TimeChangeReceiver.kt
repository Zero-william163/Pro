package com.countdown.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.countdown.app.data.CountdownRepository
import com.countdown.app.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimeChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimeChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
                Log.d(TAG, "Time/Date/Timezone changed: ${intent.action}")

                val scope = CoroutineScope(Dispatchers.Default)
                scope.launch {
                    try {
                        val repository = CountdownRepository.getInstance(context)
                        val data = repository.getCountdownDataSync()

                        if (data.reminderEnabled) {
                            AlarmScheduler.scheduleDailyAlarm(
                                context,
                                data.reminderTimeHour,
                                data.reminderTimeMinute
                            )
                            Log.d(TAG, "Alarm rescheduled after time change")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to reschedule alarm", e)
                    }
                }
            }
        }
    }
}
