package app.opal.core.tunnel.tor

import app.opal.core.model.tor.ControlReply
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A stalled Tor must surface as a failure. Before, the command timeout was a
 * TimeoutCancellationException: every caller rethrew it as a cancellation, and the Tor session
 * start stopped silently, stuck at 0 % (found in the Android 17 emulator).
 */
class ControlTimeoutTest {

    @Test
    fun `no reply in time is a TorControlException, not a cancellation`() = runTest {
        val never = CompletableDeferred<ControlReply>()
        val error = runCatching {
            awaitReply(never, "SETEVENTS CIRC STREAM", timeoutMillis = 1_000)
        }
            .exceptionOrNull()
        assertTrue("$error", error is TorControlException)
        assertFalse(error is CancellationException)
        assertEquals("No reply to SETEVENTS within 1 s", error?.message)
    }

    @Test
    fun `a caller that rethrows cancellations still sees the failure`() = runTest {
        // The pattern every caller uses: rethrow cancellation, handle everything else.
        var handled: Throwable? = null
        val job = launch {
            try {
                awaitReply(CompletableDeferred(), "GETINFO version", timeoutMillis = 500)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handled = e
            }
        }
        job.join()
        assertFalse(job.isCancelled)
        assertTrue("$handled", handled is TorControlException)
    }
}
