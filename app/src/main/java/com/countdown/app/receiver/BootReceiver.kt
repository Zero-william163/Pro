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

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d(TAG, "Boot completed, restoring alarms")

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
                        Log.d(TAG, "Alarm restored after boot")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore alarm", e)
                }
            }
        }
    }
}
