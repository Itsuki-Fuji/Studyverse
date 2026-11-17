package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DarkModePref
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "studyverse_prefs")

class UserPreferencesStore(private val context: Context) {
    private val darkModeKey = stringPreferencesKey("dark_mode_pref")
    private val focusMinutesKey = intPreferencesKey("focus_minutes")
    private val shortBreakKey = intPreferencesKey("short_break_minutes")
    private val longBreakKey = intPreferencesKey("long_break_minutes")
    private val ambientSoundKey = booleanPreferencesKey("ambient_sound")
    private val ttsEnabledKey = booleanPreferencesKey("tts_enabled")

    val darkModePref: Flow<DarkModePref> = context.dataStore.data.map { prefs ->
        DarkModePref.safeParse(prefs[darkModeKey])
    }

    val focusMinutes: Flow<Int> = context.dataStore.data.map { it[focusMinutesKey] ?: 25 }
    val shortBreakMinutes: Flow<Int> = context.dataStore.data.map { it[shortBreakKey] ?: 5 }
    val longBreakMinutes: Flow<Int> = context.dataStore.data.map { it[longBreakKey] ?: 15 }
    val ambientSoundEnabled: Flow<Boolean> = context.dataStore.data.map { it[ambientSoundKey] ?: false }
    val ttsEnabled: Flow<Boolean> = context.dataStore.data.map { it[ttsEnabledKey] ?: false }

    suspend fun setDarkModePref(pref: DarkModePref) {
        context.dataStore.edit { it[darkModeKey] = pref.name }
    }

    suspend fun setTimerDurations(focus: Int, shortBreak: Int, longBreak: Int) {
        context.dataStore.edit {
            it[focusMinutesKey] = focus.coerceIn(5, 120)
            it[shortBreakKey] = shortBreak.coerceIn(1, 30)
            it[longBreakKey] = longBreak.coerceIn(5, 60)
        }
    }

    suspend fun setAmbientSound(enabled: Boolean) {
        context.dataStore.edit { it[ambientSoundKey] = enabled }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ttsEnabledKey] = enabled }
    }
}
