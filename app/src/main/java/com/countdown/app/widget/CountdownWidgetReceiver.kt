package com.countdown.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.countdown.app.R
import com.countdown.app.data.CountdownRepository
import com.countdown.app.util.DateCalculator
import kotlinx.coroutines.runBlocking

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

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Widget added
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Last widget removed
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown)

            val data = runBlocking {
                try {
                    CountdownRepository.getInstance(context).getCountdownDataSync()
                } catch (e: Exception) {
                    null
                }
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
                val targetText = "目标: ${DateCalculator.formatDate(data.targetDate)}"
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
                views.setTextViewText(R.id.widget_event, "目标倒计时")
                views.setTextViewText(R.id.widget_days, "--")
                views.setTextViewText(R.id.widget_label, "天")
                views.setTextViewText(R.id.widget_target, "请打开应用设置")
                views.setTextViewText(R.id.widget_reminder_time, "未设置提醒")
            }

            // Click to open app
            val intent = Intent(context, com.countdown.app.MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
