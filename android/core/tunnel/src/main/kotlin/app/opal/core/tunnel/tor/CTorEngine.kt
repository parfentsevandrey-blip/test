package app.opal.core.tunnel.tor

import app.opal.core.model.tor.TorEvent
import app.opal.core.model.tor.TorEventType
import app.opal.core.model.tor.TorOption
import app.opal.core.model.tor.Torrc
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.torproject.jni.TorService

/**
 * C-tor running in-process through tor_api (`libtor.so`).
 *
 * Tor runs on its own thread; the control connection is the owning-controller socketpair created by
 * tor_api, so it is authenticated implicitly and invisible to other apps. Restarting in the same
 * process is supported by tor_api (`tor_run_main` may be called again after it returned); if an old
 * instance refuses to exit, [start] fails and the caller escalates to a process restart.
 */
internal class CTorEngine(
    private val files: TorFiles,
    private val scope: CoroutineScope,
    private val debuggable: Boolean,
) : TorEngine {

    private val _state = MutableStateFlow<EngineState>(EngineState.Stopped)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _events =
        MutableSharedFlow<TorEvent>(
            extraBufferCapacity = 1024,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val events: SharedFlow<TorEvent> = _events.asSharedFlow()

    private val lifecycle = Mutex()
    private var control: ControlClient? = null
    private var exited: CompletableDeferred<Int>? = null
    private var jobs: List<Job> = emptyList()

    override suspend fun start(config: Torrc): EngineState.Running = lifecycle.withLock {
        (_state.value as? EngineState.Running)?.let {
            return@withLock it
        }
        // A previous instance must be completely gone: tor_api allows one Tor per process.
        exited?.let { previous ->
            if (
                !previous.isCompleted &&
                    withTimeoutOrNull(STOP_TIMEOUT_MS) { previous.await() } == null
            ) {
                _state.value = EngineState.Failed("previous Tor instance did not exit")
                throw TorControlException("Previous Tor instance is still running")
            }
        }
        _state.value = EngineState.Starting
        try {
            launch(config)
        } catch (e: CancellationException) {
            cleanup()
            _state.value = EngineState.Stopped
            throw e
        } catch (e: Throwable) {
            cleanup()
            _state.value = EngineState.Failed(e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    private suspend fun launch(config: Torrc): EngineState.Running {
        withContext(Dispatchers.IO) {
            files.prepare()
            files.torrc.writeText(config.render())
        }
        val binding = TorService() // loads libtor.so
        val fdReady = CompletableDeferred<Int>()
        val done = CompletableDeferred<Int>()
        exited = done
        val thread =
            Thread(
                null,
                {
                    var created = false
                    try {
                        check(binding.createTorConfiguration()) { "createTorConfiguration failed" }
                        created = true
                        check(binding.mainConfigurationSetCommandLine(arguments())) {
                            "Bad Tor command line"
                        }
                        check(binding.mainConfigurationSetupControlSocket()) {
                            "Cannot create control socket"
                        }
                        fdReady.complete(binding.torControlFd)
                        done.complete(binding.runMain())
                    } catch (t: Throwable) {
                        fdReady.completeExceptionally(t)
                        done.complete(-1)
                    } finally {
                        if (created) binding.mainConfigurationFree()
                    }
                },
                "tor",
                TOR_THREAD_STACK_BYTES,
            )
        thread.start()

        val fd = withTimeout(START_TIMEOUT_MS) { fdReady.await() }
        val client = ControlClient.open(fd, scope)
        control = client
        jobs =
            listOf(
                scope.launch { client.events.collect { _events.emit(it) } },
                scope.launch {
                    val code = done.await()
                    onTorExited(code)
                },
            )
        // If Tor rejects its configuration it exits before ever reading the control socket; close
        // our end right away so the first command fails fast instead of waiting for its timeout.
        val earlyExit = scope.launch {
            done.await()
            client.close()
        }
        val info =
            try {
                client.setEvents(TorEventType.entries)
                client.getInfo("net/listeners/socks", "version")
            } finally {
                earlyExit.cancel()
            }
        val port =
            SOCKS_PORT.find(info["net/listeners/socks"].orEmpty())
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: throw TorControlException("Tor opened no SOCKS listener")
        return EngineState.Running(socksPort = port, version = info["version"].orEmpty()).also {
            _state.value = it
        }
    }

    private suspend fun onTorExited(code: Int) {
        lifecycle.withLock {
            // Unexpected exit while we believed Tor was running.
            if (_state.value is EngineState.Running || _state.value is EngineState.Starting) {
                cleanup()
                _state.value = EngineState.Failed("Tor exited with code $code")
            }
        }
    }

    override suspend fun reconfigure(config: Torrc, options: Collection<TorOption>) {
        requireControl().setConf(config.toSetConf(options))
    }

    override suspend fun signal(signal: TorSignal) {
        requireControl().signal(signal.command)
    }

    override suspend fun getInfo(vararg keys: String): Map<String, String> =
        requireControl().getInfo(*keys)

    /** Raw access for the few commands not covered by the interface (tests, diagnostics). */
    internal suspend fun setConf(key: String, value: String?) = requireControl().setConf(key, value)

    override suspend fun stop() {
        lifecycle.withLock {
            val client =
                control
                    ?: run {
                        _state.value = EngineState.Stopped
                        return@withLock
                    }
            // For a client "SHUTDOWN" exits immediately; closing the owning controller also would.
            runCatching { client.send("SIGNAL SHUTDOWN", timeoutMillis = 5_000) }
            client.close()
            withTimeoutOrNull(STOP_TIMEOUT_MS) { exited?.await() }
            cleanup()
            _state.value = EngineState.Stopped
        }
    }

    private fun cleanup() {
        control?.close()
        control = null
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }

    private fun requireControl(): ControlClient =
        control ?: throw TorControlException("Tor is not running")

    private fun arguments(): Array<String> =
        arrayOf(
            "tor",
            "--ignore-missing-torrc",
            "-f",
            files.torrc.absolutePath,
            "--defaults-torrc",
            files.defaultsTorrc.absolutePath,
            "--DataDirectory",
            files.dataDir.absolutePath,
            "--CacheDirectory",
            files.cacheDir.absolutePath,
            "--RunAsDaemon",
            "0",
            // Tor is a library here: signals belong to the app process.
            "--__DisableSignalHandlers",
            "1",
            // Release builds keep Tor's default (process not dumpable); debug builds stay
            // debuggable.
            "--DisableDebuggerAttachment",
            if (debuggable) "0" else "1",
        )

    companion object {
        private val SOCKS_PORT = Regex("127\\.0\\.0\\.1:(\\d+)")
        private const val START_TIMEOUT_MS = 15_000L
        private const val STOP_TIMEOUT_MS = 10_000L
        private const val TOR_THREAD_STACK_BYTES = 4L * 1024 * 1024
    }
}
