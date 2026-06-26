package com.claude.tokenwidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "claude_usage")

/**
 * Persists the latest [UsageData] snapshot plus user configuration so the
 * widget can render instantly (from cache) and the worker can refresh in the
 * background. Backed by Preferences DataStore.
 */
class UsageDataStore(private val context: Context) {

    private object Keys {
        val SESSION_USED = longPreferencesKey("session_used")
        val SESSION_LIMIT = longPreferencesKey("session_limit")
        val WEEKLY_USED = longPreferencesKey("weekly_used")
        val WEEKLY_LIMIT = longPreferencesKey("weekly_limit")
        val SESSION_RESET = longPreferencesKey("session_reset")
        val WEEKLY_RESET = longPreferencesKey("weekly_reset")
        val UPDATED_AT = longPreferencesKey("updated_at")

        // Configuration
        val MODE = stringPreferencesKey("source_mode")      // "local" | "remote"
        val API_KEY = stringPreferencesKey("api_key")
        val API_ENDPOINT = stringPreferencesKey("api_endpoint")
    }

    val usageFlow: Flow<UsageData> = context.dataStore.data.map { p ->
        UsageData(
            sessionTokensUsed = p[Keys.SESSION_USED] ?: UsageData.PLACEHOLDER.sessionTokensUsed,
            sessionTokenLimit = p[Keys.SESSION_LIMIT] ?: UsageData.PLACEHOLDER.sessionTokenLimit,
            weeklyTokensUsed = p[Keys.WEEKLY_USED] ?: UsageData.PLACEHOLDER.weeklyTokensUsed,
            weeklyTokenLimit = p[Keys.WEEKLY_LIMIT] ?: UsageData.PLACEHOLDER.weeklyTokenLimit,
            sessionResetAt = p[Keys.SESSION_RESET] ?: 0,
            weeklyResetAt = p[Keys.WEEKLY_RESET] ?: 0,
            updatedAt = p[Keys.UPDATED_AT] ?: 0,
        )
    }

    val configFlow: Flow<UsageConfig> = context.dataStore.data.map { p ->
        UsageConfig(
            mode = SourceMode.fromKey(p[Keys.MODE]),
            apiKey = p[Keys.API_KEY].orEmpty(),
            apiEndpoint = p[Keys.API_ENDPOINT].orEmpty(),
        )
    }

    suspend fun save(data: UsageData) {
        context.dataStore.edit { p ->
            p[Keys.SESSION_USED] = data.sessionTokensUsed
            p[Keys.SESSION_LIMIT] = data.sessionTokenLimit
            p[Keys.WEEKLY_USED] = data.weeklyTokensUsed
            p[Keys.WEEKLY_LIMIT] = data.weeklyTokenLimit
            p[Keys.SESSION_RESET] = data.sessionResetAt
            p[Keys.WEEKLY_RESET] = data.weeklyResetAt
            p[Keys.UPDATED_AT] = data.updatedAt
        }
    }

    suspend fun saveConfig(config: UsageConfig) {
        context.dataStore.edit { p ->
            p[Keys.MODE] = config.mode.key
            p[Keys.API_KEY] = config.apiKey
            p[Keys.API_ENDPOINT] = config.apiEndpoint
        }
    }
}

enum class SourceMode(val key: String) {
    /** Snapshot is computed/edited on-device; no network. */
    LOCAL("local"),

    /** Snapshot is fetched from a configured HTTP endpoint. */
    REMOTE("remote");

    companion object {
        fun fromKey(key: String?): SourceMode =
            entries.firstOrNull { it.key == key } ?: LOCAL
    }
}

data class UsageConfig(
    val mode: SourceMode = SourceMode.LOCAL,
    val apiKey: String = "",
    val apiEndpoint: String = "",
)
