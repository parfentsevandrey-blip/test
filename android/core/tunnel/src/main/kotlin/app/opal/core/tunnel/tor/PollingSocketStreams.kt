package app.opal.core.tunnel.tor

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream

/**
 * Blocking streams over a *non-blocking* socket descriptor (tor_api hands out the controller socket
 * with O_NONBLOCK, and clearing it needs `Os.fcntlInt`, API 30+). Waits with poll(2) instead, which
 * works on every supported API level and returns immediately after `shutdown()`.
 */
internal class PollingInputStream(private val fd: FileDescriptor) : InputStream() {
    private val one = ByteArray(1)

    override fun read(): Int = if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xff

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (true) {
            awaitEvent(fd, OsConstants.POLLIN.toShort())
            try {
                val n = Os.read(fd, b, off, len)
                return if (n == 0) -1 else n
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) continue
                throw IOException(e)
            }
        }
    }
}

internal class PollingOutputStream(private val fd: FileDescriptor) : OutputStream() {
    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        var written = 0
        while (written < len) {
            try {
                written += Os.write(fd, b, off + written, len - written)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) {
                    awaitEvent(fd, OsConstants.POLLOUT.toShort())
                    continue
                }
                throw IOException(e)
            }
        }
    }
}

private fun awaitEvent(fd: FileDescriptor, events: Short) {
    val pollfd =
        StructPollfd().apply {
            this.fd = fd
            this.events = events
        }
    while (true) {
        try {
            // Infinite timeout: shutdown()/close of the peer produces POLLHUP/POLLIN and wakes us.
            Os.poll(arrayOf(pollfd), -1)
            return
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.EINTR) continue
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            throw IOException(e)
        }
    }
}
