package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ReminderLeadTime
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.smartTodoSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "smarttodo_settings"
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val reminderLeadTime: ReminderLeadTime = ReminderLeadTime.FIFTEEN_MINUTES
)

interface SettingsRepository {
    val settingsFlow: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setReminderLeadTime(leadTime: ReminderLeadTime)
}

class SettingsDataStore(context: Context) : SettingsRepository {
    private val dataStore = context.applicationContext.smartTodoSettingsDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val REMINDER_LEAD_MINUTES = intPreferencesKey("reminder_lead_minutes")
    }

    override val settingsFlow: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppSettings(
                themeMode = ThemeMode.fromRaw(prefs[Keys.THEME_MODE]),
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                reminderLeadTime = ReminderLeadTime.fromMinutes(prefs[Keys.REMINDER_LEAD_MINUTES])
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    override suspend fun setReminderLeadTime(leadTime: ReminderLeadTime) {
        dataStore.edit { prefs ->
            prefs[Keys.REMINDER_LEAD_MINUTES] = leadTime.minutesBefore
        }
    }
}
