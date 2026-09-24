package app.rosa.weather.core.designsystem.motion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JankWatchTest {
    private fun JankWatch.feed(frames: Int, lateEvery: Int, from: Long = 10_000): List<Boolean> =
        (0 until frames).map { i -> record(from + i * 16L, lateEvery > 0 && i % lateEvery == 0) }

    @Test
    fun `smooth device never downgrades`() {
        val watch = JankWatch(startedAt = 0)
        assertThat(watch.feed(1_200, lateEvery = 20)).doesNotContain(true) // 5 % late
    }

    @Test
    fun `sustained jank downgrades exactly once`() {
        val watch = JankWatch(startedAt = 0)
        val verdicts = watch.feed(1_200, lateEvery = 2) // 50 % late
        assertThat(verdicts.count { it }).isEqualTo(1)
    }

    @Test
    fun `start-up frames are ignored`() {
        val watch = JankWatch(startedAt = 0, warmUpMillis = 5_000)
        assertThat(watch.feed(240, lateEvery = 1, from = 0)).doesNotContain(true) // all within warm-up
    }
}
