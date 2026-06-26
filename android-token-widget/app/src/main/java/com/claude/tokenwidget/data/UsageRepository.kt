package com.claude.tokenwidget.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Single source of truth for the widget's usage snapshot.
 *
 * [refresh] recomputes a snapshot according to the active [SourceMode] and
 * persists it through [UsageDataStore]. [current] reads the last cached value
 * (used for instant rendering on the widget thread).
 *
 * NOTE ON DATA SOURCE — Anthropic does not currently expose a public API for a
 * consumer Claude plan's session / weekly rate-limit consumption, so [LOCAL]
 * mode is the default and ships working out of the box: it advances the cached
 * snapshot deterministically over time so "dynamic updates" are visible without
 * any backend. [REMOTE] mode is a ready seam — point [UsageConfig.apiEndpoint]
 * at any JSON endpoint that returns the [UsageData] shape (e.g. a small relay
 * you run that scrapes Claude Code's local logs, or the Anthropic Admin Usage &
 * Cost API mapped into this model).
 */
class UsageRepository(context: Context) {

    private val store = UsageDataStore(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun current(): UsageData = store.usageFlow.first()

    /** Recompute + persist a fresh snapshot. Returns the new value. */
    suspend fun refresh(nowMillis: Long): UsageData {
        val config = store.configFlow.first()
        val previous = store.usageFlow.first()
        val next = when (config.mode) {
            SourceMode.REMOTE -> fetchRemote(config, nowMillis) ?: simulate(previous, nowMillis)
            SourceMode.LOCAL -> simulate(previous, nowMillis)
        }
        store.save(next)
        return next
    }

    /** Persist a manually edited snapshot (from the config screen). */
    suspend fun saveManual(data: UsageData) = store.save(data.copy(updatedAt = data.updatedAt))

    /**
     * Local fallback: nudges usage upward and rolls each window over at its
     * reset instant, so the widget visibly changes on every refresh tick.
     */
    private fun simulate(previous: UsageData, now: Long): UsageData {
        val base = if (previous.sessionTokenLimit <= 1) UsageData.PLACEHOLDER else previous

        val sessionWindowMs = 5 * 60 * 60 * 1000L  // 5h rolling session
        val weeklyWindowMs = 7 * 24 * 60 * 60 * 1000L

        var sessionReset = if (base.sessionResetAt == 0L) now + sessionWindowMs else base.sessionResetAt
        var weeklyReset = if (base.weeklyResetAt == 0L) now + weeklyWindowMs else base.weeklyResetAt

        var sessionUsed = base.sessionTokensUsed + (base.sessionTokenLimit / 80)
        var weeklyUsed = base.weeklyTokensUsed + (base.weeklyTokenLimit / 400)

        if (now >= sessionReset) {
            sessionUsed = base.sessionTokenLimit / 25
            sessionReset = now + sessionWindowMs
        }
        if (now >= weeklyReset) {
            weeklyUsed = base.weeklyTokenLimit / 60
            weeklyReset = now + weeklyWindowMs
        }

        return base.copy(
            sessionTokensUsed = sessionUsed.coerceAtMost(base.sessionTokenLimit),
            weeklyTokensUsed = weeklyUsed.coerceAtMost(base.weeklyTokenLimit),
            sessionResetAt = sessionReset,
            weeklyResetAt = weeklyReset,
            updatedAt = now,
        )
    }

    /**
     * Remote source: GETs [UsageConfig.apiEndpoint] and decodes the [UsageData]
     * JSON shape. Returns null on any failure so the caller can fall back.
     */
    private suspend fun fetchRemote(config: UsageConfig, now: Long): UsageData? =
        withContext(Dispatchers.IO) {
            if (config.apiEndpoint.isBlank()) return@withContext null
            runCatching {
                val conn = (URL(config.apiEndpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                    if (config.apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                        setRequestProperty("x-api-key", config.apiKey)
                    }
                }
                conn.use {
                    if (it.responseCode !in 200..299) return@use null
                    val body = it.inputStream.bufferedReader().use { r -> r.readText() }
                    json.decodeFromString<UsageData>(body).copy(updatedAt = now)
                }
            }.getOrNull()
        }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}
