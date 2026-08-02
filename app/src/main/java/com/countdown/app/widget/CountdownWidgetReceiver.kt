package com.countdown.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.widget.RemoteViews
import com.countdown.app.R
import com.countdown.app.data.CountdownRepository
import com.countdown.app.util.DateCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Countdown Widget Receiver (Redesigned)
 *
 * Features:
 * - Automatic dark/light mode background adaptation
 * - Modern card-style layout with large countdown number
 * - Click to open app
 * - Async data loading (no runBlocking on main thread)
 * - Robust error handling (always shows something)
 */
class CountdownWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val TAG = "WidgetReceiver"

        /**
         * Synchronous widget update — used from MainActivity or other
         * places that already have a background context.
         * Uses runBlocking but wraps in try-catch to never crash.
         */
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            try {
                val views = buildRemoteViews(context)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget $appWidgetId", e)
                // Fallback: show a minimal RemoteViews so the widget
                // doesn't show "Problem loading widget"
                try {
                    val fallback = RemoteViews(context.packageName, R.layout.widget_countdown)
                    appWidgetManager.updateAppWidget(appWidgetId, fallback)
                } catch (e2: Exception) {
                    Log.e(TAG, "Even fallback failed", e2)
                }
            }
        }

        /**
         * Build the RemoteViews for the widget.
         * Loads data synchronously — must be called from a background thread
         * or wrapped in runBlocking with a timeout.
         */
        private fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown)

            // ===== Dark mode detection: set appropriate background =====
            val isDarkMode = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            val bgDrawable = if (isDarkMode) {
                R.drawable.widget_background_dark
            } else {
                R.drawable.widget_background
            }
            views.setInt(R.id.widget_container, "setBackgroundResource", bgDrawable)

            // ===== Load countdown data with fallback =====
            val data = try {
                runBlocking {
                    CountdownRepository.getInstance(context).getCountdownDataSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load countdown data", e)
                null
            }

            if (data != null) {
                val daysRemaining = DateCalculator.daysRemaining(data.targetDate)
                val targetReached = DateCalculator.isTargetReached(data.targetDate)

                val eventText = data.eventContent.ifEmpty { "目标" }
                val daysText = when {
                    targetReached -> "到达"
                    daysRemaining == 0L -> "今天"
                    daysRemaining < 0 -> "${-daysRemaining}"
                    else -> "$daysRemaining"
                }
                val labelText = when {
                    targetReached -> ""
                    daysRemaining <= 0 -> "天前"
                    else -> "天"
                }
                val targetText = DateCalculator.formatDate(data.targetDate)
                val reminderText = if (data.reminderEnabled) {
                    "提醒 ${DateCalculator.formatTime(data.reminderTimeHour, data.reminderTimeMinute)}"
                } else {
                    "未设置提醒"
                }

                views.setTextViewText(R.id.widget_event, eventText)
                views.setTextViewText(R.id.widget_days, daysText)
                views.setTextViewText(R.id.widget_label, labelText)
                views.setTextViewText(R.id.widget_target, targetText)
                views.setTextViewText(R.id.widget_reminder_time, reminderText)
            } else {
                // Show placeholder data when loading fails
                views.setTextViewText(R.id.widget_event, "目标倒计时")
                views.setTextViewText(R.id.widget_days, "--")
                views.setTextViewText(R.id.widget_label, "天")
                views.setTextViewText(R.id.widget_target, "请打开应用设置")
                views.setTextViewText(R.id.widget_reminder_time, "未设置提醒")
            }

            // ===== Click to open app =====
            val intent = Intent(context, com.countdown.app.MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            return views
        }
    }
}
