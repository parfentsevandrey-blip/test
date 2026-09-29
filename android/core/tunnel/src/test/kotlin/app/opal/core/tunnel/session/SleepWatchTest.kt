package app.opal.core.tunnel.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepWatchTest {
    private val watch = SleepWatch()
    private val snowflake = SNOWFLAKE_SESSION_EXPIRY_MS

    @Test
    fun `a sleep shorter than the server keeps the session changes nothing`() {
        assertFalse(watch.onTick(snowflake - 1, snowflake, watching = true))
        assertFalse(watch.renewNow { true })
    }

    @Test
    fun `after a long sleep the session is renewed once the screen is on`() {
        assertTrue(watch.onTick(5 * 60_000L, snowflake, watching = true))
        assertFalse(watch.renewNow { false })
        // Another tick of the same sleep does not report it again.
        assertFalse(watch.onTick(10 * 60_000L, snowflake, watching = true))
        assertTrue(watch.renewNow { true })
        assertFalse(watch.renewNow { true })
    }

    @Test
    fun `bytes from the bridge prove the session alive`() {
        watch.onTick(5 * 60_000L, snowflake, watching = true)
        watch.clear()
        assertFalse(watch.renewNow { true })
    }

    /** Not ready, or TCP transports: their connections are not a session the servers forget. */
    @Test
    fun `only a ready session transport is watched`() {
        assertFalse(watch.onTick(60 * 60_000L, snowflake, watching = false))
        assertFalse(watch.stale)
    }

    @Test
    fun `dnstt sessions expire sooner`() {
        assertTrue(watch.onTick(DNSTT_SESSION_EXPIRY_MS, DNSTT_SESSION_EXPIRY_MS, watching = true))
    }
}
