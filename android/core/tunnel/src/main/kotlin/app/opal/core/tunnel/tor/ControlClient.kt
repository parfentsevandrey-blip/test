package app.opal.core.tunnel.tor

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import app.opal.core.model.tor.ControlArgs
import app.opal.core.model.tor.ControlProtocolException
import app.opal.core.model.tor.ControlReply
import app.opal.core.model.tor.GetInfoParser
import app.opal.core.model.tor.ReplyAssembler
import app.opal.core.model.tor.TorEvent
import app.opal.core.model.tor.TorEventParser
import app.opal.core.model.tor.TorEventType
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class TorControlException(message: String, val reply: ControlReply? = null) : IOException(message)

/**
 * Coroutine client for Tor's control protocol over the owning-controller socket.
 *
 * - One reader coroutine assembles replies; synchronous replies complete the oldest pending command
 *   (the protocol answers commands in order), asynchronous 650 replies become [events].
 * - Commands are serialized by [writeLock]; each waits for its own reply.
 * - Closing shuts the socket down, which makes Tor exit (it owns the controller).
 */
internal class ControlClient
private constructor(
    private val pfd: ParcelFileDescriptor,
    private val output: OutputStream,
    scope: CoroutineScope,
) : Closeable {

    private val writeLock = Mutex()
    private val pending = ArrayDeque<CompletableDeferred<ControlReply>>()
    private val pendingLock = Any()

    private val _events =
        MutableSharedFlow<TorEvent>(
            extraBufferCapacity = 1024,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<TorEvent> = _events

    /** Completes when the connection is gone (Tor exited or [close] was called). */
    val closed = CompletableDeferred<Throwable?>()

    private val reader: Job =
        scope.launch(Dispatchers.IO) {
            val assembler = ReplyAssembler()
            var failure: Throwable? = null
            try {
                val lines =
                    BufferedReader(
                        InputStreamReader(PollingInputStream(pfd.fileDescriptor), Charsets.UTF_8)
                    )
                while (true) {
                    val line = lines.readLine() ?: break
                    val reply = assembler.feed(line) ?: continue
                    if (reply.isAsync) {
                        TorEventParser.parse(reply)?.let { _events.tryEmit(it) }
                    } else {
                        val waiter = synchronized(pendingLock) { pending.removeFirstOrNull() }
                        waiter?.complete(reply)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                failure = e
            } catch (e: ControlProtocolException) {
                failure = e
            } finally {
                val error = failure ?: TorControlException("Tor control connection closed")
                synchronized(pendingLock) {
                    pending.forEach { it.completeExceptionally(error) }
                    pending.clear()
                }
                closed.complete(failure)
            }
        }

    /** Sends one command line and returns its reply; throws [TorControlException] on 4xx/5xx. */
    suspend fun send(command: String, timeoutMillis: Long = COMMAND_TIMEOUT_MS): ControlReply {
        require('\n' !in command && '\r' !in command) { "Multi-line commands are not supported" }
        val waiter = CompletableDeferred<ControlReply>()
        writeLock.withLock {
            if (closed.isCompleted) throw TorControlException("Tor control connection closed")
            synchronized(pendingLock) { pending.addLast(waiter) }
            withContext(Dispatchers.IO) {
                output.write((command + "\r\n").toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }
        val reply = withTimeout(timeoutMillis) { waiter.await() }
        if (!reply.isSuccess) throw TorControlException("${reply.code} ${reply.message}", reply)
        return reply
    }

    suspend fun setEvents(
        types: Collection<TorEventType>,
        timeoutMillis: Long = COMMAND_TIMEOUT_MS,
    ) {
        send("SETEVENTS " + types.joinToString(" ") { it.keyword }, timeoutMillis)
    }

    suspend fun getInfo(vararg keys: String): Map<String, String> =
        getInfo(keys.toList(), COMMAND_TIMEOUT_MS)

    suspend fun getInfo(keys: List<String>, timeoutMillis: Long): Map<String, String> =
        GetInfoParser.parse(send("GETINFO " + keys.joinToString(" "), timeoutMillis))

    /** `SETCONF` with pre-rendered arguments (see [app.opal.core.model.tor.Torrc.toSetConf]). */
    suspend fun setConf(arguments: String) {
        if (arguments.isNotBlank()) send("SETCONF $arguments")
    }

    suspend fun setConf(key: String, value: String?) {
        send(if (value == null) "SETCONF $key" else "SETCONF $key=${ControlArgs.quote(value)}")
    }

    suspend fun signal(name: String) {
        send("SIGNAL $name")
    }

    override fun close() {
        // shutdown() wakes the reader blocked in read(); close() alone would not.
        try {
            Os.shutdown(pfd.fileDescriptor, OsConstants.SHUT_RDWR)
        } catch (_: ErrnoException) {}
        try {
            pfd.close()
        } catch (_: IOException) {}
        reader.cancel()
    }

    companion object {
        const val COMMAND_TIMEOUT_MS = 30_000L

        /** Adopts the raw (non-blocking) socket descriptor handed out by tor_api. */
        fun open(fd: Int, scope: CoroutineScope): ControlClient {
            val pfd = ParcelFileDescriptor.adoptFd(fd)
            return ControlClient(pfd, PollingOutputStream(pfd.fileDescriptor), scope)
        }
    }
}
