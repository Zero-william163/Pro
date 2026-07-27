package com.countdown.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "countdown_prefs")

class CountdownRepository private constructor(private val context: Context) {

    private val dataStore = context.dataStore

    private val EVENT_CONTENT = stringPreferencesKey("event_content")
    private val TARGET_DATE = stringPreferencesKey("target_date")
    private val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    private val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    private val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
    private val THEME_MODE = intPreferencesKey("theme_mode")

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val countdownDataFlow: Flow<CountdownData> = dataStore.data.map { prefs ->
        CountdownData(
            eventContent = prefs[EVENT_CONTENT] ?: "",
            targetDate = parseDate(prefs[TARGET_DATE]),
            reminderTimeHour = prefs[REMINDER_HOUR] ?: 8,
            reminderTimeMinute = prefs[REMINDER_MINUTE] ?: 0,
            reminderEnabled = prefs[REMINDER_ENABLED] ?: false,
            themeMode = prefs[THEME_MODE] ?: 0
        )
    }

    fun getCountdownDataSync(): CountdownData = runBlocking(Dispatchers.IO) {
        countdownDataFlow.first()
    }

    suspend fun saveCountdownData(data: CountdownData) {
        dataStore.edit { prefs ->
            prefs[EVENT_CONTENT] = data.eventContent
            prefs[TARGET_DATE] = data.targetDate.format(dateFormatter)
            prefs[REMINDER_HOUR] = data.reminderTimeHour
            prefs[REMINDER_MINUTE] = data.reminderTimeMinute
            prefs[REMINDER_ENABLED] = data.reminderEnabled
            prefs[THEME_MODE] = data.themeMode
        }
    }

    suspend fun saveEventContent(content: String) {
        dataStore.edit { prefs -> prefs[EVENT_CONTENT] = content }
    }

    suspend fun saveTargetDate(date: LocalDate) {
        dataStore.edit { prefs -> prefs[TARGET_DATE] = date.format(dateFormatter) }
    }

    suspend fun saveReminderTime(hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[REMINDER_HOUR] = hour
            prefs[REMINDER_MINUTE] = minute
        }
    }

    suspend fun saveReminderEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[REMINDER_ENABLED] = enabled }
    }

    suspend fun saveThemeMode(mode: Int) {
        dataStore.edit { prefs -> prefs[THEME_MODE] = mode }
    }

    private fun parseDate(dateStr: String?): LocalDate {
        return try {
            dateStr?.let { LocalDate.parse(it, dateFormatter) } ?: LocalDate.now().plusDays(1)
        } catch (e: Exception) {
            LocalDate.now().plusDays(1)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: CountdownRepository? = null

        fun getInstance(context: Context): CountdownRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CountdownRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
