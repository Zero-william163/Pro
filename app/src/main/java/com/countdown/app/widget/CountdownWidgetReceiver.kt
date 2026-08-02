package com.countdown.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.countdown.app.MainActivity
import com.countdown.app.R
import com.countdown.app.data.CountdownRepository
import com.countdown.app.util.DateCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Countdown Widget Receiver — Ultra-Robust Implementation
 *
 * Design principles (in priority order):
 * 1. Stability — never crash, never show "Problem loading widget"
 * 2. Compatibility — works on all launchers (Huawei, MIUI, OneUI, Pixel, etc.)
 * 3. Performance — no main thread blocking, ever
 * 4. Aesthetics — modern card style
 *
 * Key implementation details:
 * - onUpdate uses goAsync() to avoid blocking BroadcastReceiver's main thread
 * - updateAllWidgets uses background coroutine — NEVER blocks main thread
 * - All RemoteViews operations wrapped in try-catch with fallback
 * - Data loading has 3-second timeout to prevent infinite hang
 * - PendingIntent uses FLAG_IMMUTABLE (required API 23+)
 * - No nested runBlocking — data loaded via suspend function
 */
class CountdownWidgetReceiver : AppWidgetProvider() {

    companion object {
        private const val TAG = "WidgetReceiver"
        private const val DATA_LOAD_TIMEOUT_MS = 3000L

        /**
         * Update all widgets of this provider.
         * Safe to call from the main thread — dispatches to background.
         */
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, CountdownWidgetReceiver::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)

                if (ids.isEmpty()) return

                // Always dispatch to background — never block main thread
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    for (id in ids) {
                        try {
                            updateWidgetSync(context, appWidgetManager, id)
                        } catch (e: Exception) {
                            Log.e(TAG, "updateWidgetSync failed for widget $id", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateAllWidgets failed", e)
            }
        }

        /**
         * Synchronous widget update — must be called from background thread.
         * Uses runBlocking with timeout for data loading.
         */
        fun updateWidgetSync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            try {
                val views = buildRemoteViews(context)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "updateWidgetSync failed for widget $appWidgetId", e)
                // Last-resort fallback: push a bare RemoteViews with no data
                try {
                    val fallback = RemoteViews(context.packageName, R.layout.widget_countdown)
                    appWidgetManager.updateAppWidget(appWidgetId, fallback)
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback also failed", e2)
                }
            }
        }

        /**
         * Build RemoteViews for the widget.
         * Every step is wrapped in try-catch.
         * Returns a RemoteViews that is always valid.
         *
         * NOTE: This method calls runBlocking for DataStore access.
         * It MUST be called from a background thread.
         */
        private fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown)

            // ===== Step 1: Set background based on dark/light mode =====
            try {
                val isDarkMode = (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

                val bgRes = if (isDarkMode) {
                    R.drawable.widget_background_dark
                } else {
                    R.drawable.widget_background
                }
                views.setInt(R.id.widget_container, "setBackgroundResource", bgRes)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set widget background", e)
                // Background is already set in XML, so this is non-fatal
            }

            // ===== Step 2: Load countdown data =====
            // runBlocking is safe here because this method is always called
            // from Dispatchers.IO (via onUpdate's goAsync or updateAllWidgets' coroutine)
            val data = try {
                runBlocking {
                    withTimeoutOrNull(DATA_LOAD_TIMEOUT_MS) {
                        CountdownRepository.getInstance(context).getCountdownDataSync()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load countdown data", e)
                null
            }

            // ===== Step 3: Populate RemoteViews with data or fallback =====
            if (data != null) {
                try {
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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to populate widget data", e)
                    setFallbackText(views)
                }
            } else {
                setFallbackText(views)
            }

            // ===== Step 4: Set click intent to open app =====
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set click intent", e)
            }

            return views
        }

        private fun setFallbackText(views: RemoteViews) {
            try {
                views.setTextViewText(R.id.widget_event, "目标倒计时")
                views.setTextViewText(R.id.widget_days, "--")
                views.setTextViewText(R.id.widget_label, "天")
                views.setTextViewText(R.id.widget_target, "打开应用设置")
                views.setTextViewText(R.id.widget_reminder_time, "未设置提醒")
            } catch (e: Exception) {
                Log.e(TAG, "Even setFallbackText failed", e)
            }
        }
    }

    // ==================== Lifecycle Callbacks ====================

    /**
     * Called when widget is added or updated.
     * Uses goAsync() to allow background processing without ANR.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Use goAsync to avoid ANR on strict launchers (Huawei, MIUI, etc.)
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateWidgetSync(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "onUpdate failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Called when widget is resized.
     * Re-update to ensure layout fits the new size.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Use goAsync for the same reason as onUpdate
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updateWidgetSync(context, appWidgetManager, appWidgetId)
            } catch (e: Exception) {
                Log.e(TAG, "onAppWidgetOptionsChanged failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Called when the first widget is placed.
     */
    override fun onEnabled(context: Context) {
        Log.d(TAG, "First widget placed")
    }

    /**
     * Called when the last widget is removed.
     */
    override fun onDisabled(context: Context) {
        Log.d(TAG, "Last widget removed")
    }

    /**
     * Called when widgets are restored (e.g. after app reinstallation).
     */
    override fun onRestored(
        context: Context,
        oldWidgetIds: IntArray,
        newWidgetIds: IntArray
    ) {
        Log.d(TAG, "Widgets restored: ${oldWidgetIds.size} -> ${newWidgetIds.size}")
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for (id in newWidgetIds) {
                    updateWidgetSync(context, appWidgetManager, id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "onRestored failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
