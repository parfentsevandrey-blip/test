package app.opal.feature.home

import app.opal.core.model.tunnel.TrafficSample
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedHistoryTest {
    @Test
    fun `keeps the last minute, oldest first`() {
        var h = SpeedHistory()
        repeat(75) { i ->
            h +=
                TrafficSample(
                    read = i.toLong(),
                    written = 2L * i,
                    totalRead = 0,
                    totalWritten = 0,
                    seq = i.toLong(),
                )
        }
        assertEquals(SpeedHistory.CAPACITY, h.down.size)
        assertEquals(15L, h.down.first())
        assertEquals(74L, h.down.last())
        assertEquals(148L, h.up.last())
    }
}
