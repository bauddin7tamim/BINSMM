package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.LocalAppSettings
import com.example.data.model.ServiceCategory
import com.example.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "binsmm_settings")

class SettingsPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DEFAULT_CATEGORY = stringPreferencesKey("default_category")
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val COMPACT_CARDS = booleanPreferencesKey("compact_order_cards")
    }

    val settingsFlow: Flow<LocalAppSettings> = context.dataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME] ?: ThemePreference.SYSTEM.name
        val categoryKey = prefs[Keys.DEFAULT_CATEGORY] ?: ServiceCategory.INSTAGRAM.key
        LocalAppSettings(
            themePreference = runCatching { ThemePreference.valueOf(themeName) }
                .getOrDefault(ThemePreference.SYSTEM),
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            defaultCategory = ServiceCategory.fromKey(categoryKey),
            currencySymbol = prefs[Keys.CURRENCY_SYMBOL] ?: "$",
            compactOrderCards = prefs[Keys.COMPACT_CARDS] ?: false
        )
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = preference.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setDefaultCategory(category: ServiceCategory) {
        context.dataStore.edit { it[Keys.DEFAULT_CATEGORY] = category.key }
    }

    suspend fun setCurrencySymbol(symbol: String) {
        context.dataStore.edit { it[Keys.CURRENCY_SYMBOL] = symbol }
    }

    suspend fun setCompactOrderCards(compact: Boolean) {
        context.dataStore.edit { it[Keys.COMPACT_CARDS] = compact }
    }
}
