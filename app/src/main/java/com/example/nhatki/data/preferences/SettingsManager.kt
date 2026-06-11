package com.example.nhatki.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val BACKGROUND_URI = stringPreferencesKey("background_uri")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_ENABLED] ?: false
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[APP_LOCK_ENABLED] = enabled
        }
    }

    val themeColor: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_COLOR] ?: "Blue"
    }

    val backgroundUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[BACKGROUND_URI]
    }

    suspend fun setThemeColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_COLOR] = color
        }
    }

    suspend fun setBackgroundUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(BACKGROUND_URI)
            } else {
                preferences[BACKGROUND_URI] = uri
            }
        }
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "System"
    }

    val language: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE] ?: "vi"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE] = lang
        }
    }
}
