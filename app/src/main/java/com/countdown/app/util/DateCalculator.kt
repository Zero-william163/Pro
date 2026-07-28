package com.countdown.app.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object DateCalculator {

    /**
     * Calculate days remaining from today (in device timezone) to target date.
     * Returns 0 if target is today, negative if past.
     */
    fun daysRemaining(targetDate: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val today = LocalDate.now(zoneId)
        return ChronoUnit.DAYS.between(today, targetDate)
    }

    /**
     * Check if target date has been reached (today or past)
     */
    fun isTargetReached(targetDate: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        val today = LocalDate.now(zoneId)
        return !targetDate.isAfter(today)
    }

    /**
     * Get the next alarm time in millis.
     * If the scheduled time for today has passed, schedule for tomorrow.
     */
    fun getNextAlarmTimeMillis(
        hour: Int,
        minute: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val now = ZonedDateTime.now(zoneId)
        var next = now.with(LocalTime.of(hour, minute, 0, 0))
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return next.toInstant().toEpochMilli()
    }

    /**
     * Format LocalDate for display
     */
    fun formatDate(date: LocalDate): String {
        return String.format(
            "%d年%02d月%02d日",
            date.year,
            date.monthValue,
            date.dayOfMonth
        )
    }

    /**
     * Format LocalTime for display
     */
    fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }

    /**
     * Format current date time for display
     */
    fun formatCurrentDateTime(zoneId: ZoneId = ZoneId.systemDefault()): String {
        val now = LocalDateTime.now(zoneId)
        return String.format(
            "%d年%02d月%02d日 %02d:%02d:%02d",
            now.year,
            now.monthValue,
            now.dayOfMonth,
            now.hour,
            now.minute,
            now.second
        )
    }

    /**
     * Format current time only, updates every second
     */
    fun formatCurrentTime(zoneId: ZoneId = ZoneId.systemDefault()): String {
        val now = LocalDateTime.now(zoneId)
        return String.format("%02d:%02d:%02d", now.hour, now.minute, now.second)
    }

    /**
     * Get next reminder time as string
     */
    fun getNextReminderString(hour: Int, minute: Int, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val now = ZonedDateTime.now(zoneId)
        var next = now.with(LocalTime.of(hour, minute, 0, 0))
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return String.format(
            "%d年%02d月%02d日 %02d:%02d",
            next.year,
            next.monthValue,
            next.dayOfMonth,
            next.hour,
            next.minute
        )
    }

    /**
     * Format epoch millis to date time string
     */
    fun formatDateTime(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zoneId)
        return String.format(
            "%d年%02d月%02d日 %02d:%02d",
            dt.year,
            dt.monthValue,
            dt.dayOfMonth,
            dt.hour,
            dt.minute
        )
    }
}
