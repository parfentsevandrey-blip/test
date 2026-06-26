package com.claude.tokenwidget.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * A single point-in-time snapshot of Claude token consumption.
 *
 * The model is deliberately source-agnostic: it carries raw used/limit counts
 * for both the current (rolling) session window and the weekly window, plus the
 * epoch-millis instants at which each window resets. Whatever feeds it — manual
 * entry, a simulator, or a real API — only has to populate these fields.
 */
@Serializable
data class UsageData(
    val sessionTokensUsed: Long = 0,
    val sessionTokenLimit: Long = 1,
    val weeklyTokensUsed: Long = 0,
    val weeklyTokenLimit: Long = 1,
    /** Epoch millis when the rolling session window resets. */
    val sessionResetAt: Long = 0,
    /** Epoch millis when the weekly window resets. */
    val weeklyResetAt: Long = 0,
    /** Epoch millis the snapshot was produced. */
    val updatedAt: Long = 0,
) {
    /** Session usage as a fraction in [0f, 1f]. */
    val sessionFraction: Float
        get() = fraction(sessionTokensUsed, sessionTokenLimit)

    /** Weekly usage as a fraction in [0f, 1f]. */
    val weeklyFraction: Float
        get() = fraction(weeklyTokensUsed, weeklyTokenLimit)

    val sessionPercent: Int get() = (sessionFraction * 100).roundToInt()
    val weeklyPercent: Int get() = (weeklyFraction * 100).roundToInt()

    private fun fraction(used: Long, limit: Long): Float {
        if (limit <= 0) return 0f
        return (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        /** A pleasant default so a freshly placed widget never looks empty. */
        val PLACEHOLDER = UsageData(
            sessionTokensUsed = 84_000,
            sessionTokenLimit = 200_000,
            weeklyTokensUsed = 1_260_000,
            weeklyTokenLimit = 7_000_000,
        )
    }
}
