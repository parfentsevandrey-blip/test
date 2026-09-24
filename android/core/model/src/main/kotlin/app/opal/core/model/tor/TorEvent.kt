package app.opal.core.model.tor

/** Asynchronous control-port events we subscribe to (`SETEVENTS`). */
enum class TorEventType(val keyword: String) {
    StatusClient("STATUS_CLIENT"),
    StatusGeneral("STATUS_GENERAL"),
    Bandwidth("BW"),
    Circuit("CIRC"),
    Stream("STREAM"),
    OrConn("ORCONN"),
    Guard("GUARD"),
    NetworkLiveness("NETWORK_LIVENESS"),
    Notice("NOTICE"),
    Warn("WARN"),
    Err("ERR"),
}

/** One relay in a circuit path, e.g. `$ABCD...~nickname`. */
data class RelayRef(val fingerprint: String, val nickname: String?) {
    companion object {
        fun parse(token: String): RelayRef {
            val t = token.removePrefix("$")
            val sep = t.indexOfFirst { it == '~' || it == '=' }
            return if (sep < 0) RelayRef(t.uppercase(), null)
            else RelayRef(t.substring(0, sep).uppercase(), t.substring(sep + 1))
        }
    }
}

sealed interface TorEvent {

    /**
     * `STATUS_CLIENT NOTICE BOOTSTRAP PROGRESS=.. TAG=.. SUMMARY=..` (+ WARNING/REASON on
     * problems).
     */
    data class Bootstrap(
        val progress: Int,
        val tag: String,
        val summary: String,
        val warning: String? = null,
        val reason: String? = null,
        val count: Int? = null,
        val recommendation: String? = null,
        val host: String? = null,
        val hostAddress: String? = null,
    ) : TorEvent {
        val isProblem: Boolean
            get() = warning != null
    }

    data object CircuitEstablished : TorEvent

    data class CircuitNotEstablished(val reason: String?) : TorEvent

    data class ClientStatus(
        val severity: String,
        val action: String,
        val args: Map<String, String>,
    ) : TorEvent

    data class GeneralStatus(
        val severity: String,
        val action: String,
        val args: Map<String, String>,
    ) : TorEvent

    /** Bytes read/written in the last second (`BW`). */
    data class Bandwidth(val read: Long, val written: Long) : TorEvent

    data class Circuit(
        val id: String,
        val status: CircuitStatus,
        val path: List<RelayRef>,
        val purpose: String?,
        val buildFlags: Set<String>,
        val reason: String?,
        val remoteReason: String?,
    ) : TorEvent

    data class Stream(
        val id: String,
        val status: StreamStatus,
        val circuitId: String,
        val target: String,
        val reason: String?,
        val remoteReason: String?,
    ) : TorEvent

    /** OR connection to a relay/bridge; [target] is `$FP~nick` or `addr:port`. */
    data class OrConn(
        val target: String,
        val status: OrConnStatus,
        val reason: String?,
        val connectionId: String?,
    ) : TorEvent

    data class Guard(val name: String, val status: String) : TorEvent

    data class NetworkLiveness(val up: Boolean) : TorEvent

    data class Log(val severity: LogSeverity, val message: String) : TorEvent

    data class Unknown(val keyword: String, val raw: String) : TorEvent
}

enum class CircuitStatus {
    LAUNCHED,
    BUILT,
    GUARD_WAIT,
    EXTENDED,
    FAILED,
    CLOSED,
    UNKNOWN,
}

enum class StreamStatus {
    NEW,
    NEWRESOLVE,
    REMAP,
    SENTCONNECT,
    SENTRESOLVE,
    SUCCEEDED,
    FAILED,
    CLOSED,
    DETACHED,
    CONTROLLER_WAIT,
    XOFF_SENT,
    XOFF_RECV,
    XON_SENT,
    XON_RECV,
    UNKNOWN,
}

enum class OrConnStatus {
    NEW,
    LAUNCHED,
    CONNECTED,
    FAILED,
    CLOSED,
    UNKNOWN,
}

enum class LogSeverity {
    NOTICE,
    WARN,
    ERR,
}

object TorEventParser {

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    fun parse(reply: ControlReply): TorEvent? {
        if (!reply.isAsync) return null
        val first = reply.lines.first()
        // Events with a data block (e.g. long log messages) carry the payload in `data`.
        val keyword = first.text.substringBefore(' ')
        val rest =
            when {
                first.data != null -> first.data
                else -> first.text.substringAfter(' ', "")
            }
        return parse(keyword, rest)
    }

    fun parse(keyword: String, rest: String): TorEvent =
        when (keyword) {
            TorEventType.Bandwidth.keyword -> parseBandwidth(rest)
            TorEventType.StatusClient.keyword -> parseStatusClient(rest)
            TorEventType.StatusGeneral.keyword -> {
                val args = ControlArgs.parse(rest)
                TorEvent.GeneralStatus(
                    severity = args.positional.getOrElse(0) { "" },
                    action = args.positional.getOrElse(1) { "" },
                    args = args.keywords,
                )
            }
            TorEventType.Circuit.keyword -> parseCircuit(rest)
            TorEventType.Stream.keyword -> parseStream(rest)
            TorEventType.OrConn.keyword -> parseOrConn(rest)
            TorEventType.Guard.keyword -> {
                val args = ControlArgs.parse(rest)
                TorEvent.Guard(
                    name = args.positional.getOrElse(1) { "" },
                    status = args.positional.getOrElse(2) { "" },
                )
            }
            TorEventType.NetworkLiveness.keyword -> TorEvent.NetworkLiveness(rest.trim() == "UP")
            TorEventType.Notice.keyword -> TorEvent.Log(LogSeverity.NOTICE, rest)
            TorEventType.Warn.keyword -> TorEvent.Log(LogSeverity.WARN, rest)
            TorEventType.Err.keyword -> TorEvent.Log(LogSeverity.ERR, rest)
            else -> TorEvent.Unknown(keyword, rest)
        }

    private fun parseBandwidth(rest: String): TorEvent {
        val parts = rest.trim().split(' ')
        return TorEvent.Bandwidth(
            read = parts.getOrNull(0)?.toLongOrNull() ?: 0,
            written = parts.getOrNull(1)?.toLongOrNull() ?: 0,
        )
    }

    private fun parseStatusClient(rest: String): TorEvent {
        val args = ControlArgs.parse(rest)
        val severity = args.positional.getOrElse(0) { "" }
        val action = args.positional.getOrElse(1) { "" }
        val kw = args.keywords
        return when (action) {
            "BOOTSTRAP" ->
                TorEvent.Bootstrap(
                    progress = kw["PROGRESS"]?.toIntOrNull() ?: 0,
                    tag = kw["TAG"].orEmpty(),
                    summary = kw["SUMMARY"].orEmpty(),
                    warning = kw["WARNING"],
                    reason = kw["REASON"],
                    count = kw["COUNT"]?.toIntOrNull(),
                    recommendation = kw["RECOMMENDATION"],
                    host = kw["HOST"],
                    hostAddress = kw["HOSTADDR"],
                )
            "CIRCUIT_ESTABLISHED" -> TorEvent.CircuitEstablished
            "CIRCUIT_NOT_ESTABLISHED" -> TorEvent.CircuitNotEstablished(kw["REASON"])
            else -> TorEvent.ClientStatus(severity, action, kw)
        }
    }

    private fun parseCircuit(rest: String): TorEvent {
        val args = ControlArgs.parse(rest)
        val id = args.positional.getOrElse(0) { "" }
        val status = enumOr(args.positional.getOrNull(1), CircuitStatus.UNKNOWN)
        // The path is optional; when present it is the third positional token.
        val pathToken = args.positional.getOrNull(2)
        val path =
            pathToken?.takeIf { it.startsWith("$") }?.split(',')?.map(RelayRef::parse).orEmpty()
        return TorEvent.Circuit(
            id = id,
            status = status,
            path = path,
            purpose = args.keywords["PURPOSE"],
            buildFlags = args.keywords["BUILD_FLAGS"]?.split(',')?.toSet().orEmpty(),
            reason = args.keywords["REASON"],
            remoteReason = args.keywords["REMOTE_REASON"],
        )
    }

    private fun parseStream(rest: String): TorEvent {
        val args = ControlArgs.parse(rest)
        return TorEvent.Stream(
            id = args.positional.getOrElse(0) { "" },
            status = enumOr(args.positional.getOrNull(1), StreamStatus.UNKNOWN),
            circuitId = args.positional.getOrElse(2) { "0" },
            target = args.positional.getOrElse(3) { "" },
            reason = args.keywords["REASON"],
            remoteReason = args.keywords["REMOTE_REASON"],
        )
    }

    private fun parseOrConn(rest: String): TorEvent {
        val args = ControlArgs.parse(rest)
        return TorEvent.OrConn(
            target = args.positional.getOrElse(0) { "" },
            status = enumOr(args.positional.getOrNull(1), OrConnStatus.UNKNOWN),
            reason = args.keywords["REASON"],
            connectionId = args.keywords["ID"],
        )
    }
}
