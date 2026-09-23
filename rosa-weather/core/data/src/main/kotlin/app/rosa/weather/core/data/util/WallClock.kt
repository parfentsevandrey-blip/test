package app.rosa.weather.core.data.util

/** Injectable time source so staleness logic is testable. */
fun interface WallClock {
    fun nowMillis(): Long

    fun nowEpochSeconds(): Long = nowMillis() / 1000

    companion object {
        val System = WallClock { java.lang.System.currentTimeMillis() }
    }
}
