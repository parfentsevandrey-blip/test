package app.opal.core.tunnel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogBufferTest {

    @Test
    fun `export redacts addresses fingerprints and bridge secrets`() {
        val text =
            "Bridge obfs4 203.0.113.5:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=abc iat-mode=0 " +
                "via [2001:db8::1]:443 url=https://secret.example/path"
        val redacted = LogBuffer.redact(text)
        assertFalse(redacted.contains("203.0.113.5"))
        assertFalse(redacted.contains("0123456789ABCDEF"))
        assertFalse(redacted.contains("cert=abc"))
        assertFalse(redacted.contains("2001:db8"))
        assertFalse(redacted.contains("secret.example"))
    }

    @Test
    fun `ring buffer keeps the newest entries`() {
        val log = LogBuffer(capacity = 3)
        repeat(5) { log.i("t", "m$it") }
        assertEquals(listOf("m2", "m3", "m4"), log.snapshot().map { it.message })
    }
}
