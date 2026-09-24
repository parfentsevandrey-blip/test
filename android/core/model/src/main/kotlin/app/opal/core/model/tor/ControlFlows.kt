package app.opal.core.model.tor

import app.opal.core.model.tunnel.BootstrapInfo
import app.opal.core.model.tunnel.BootstrapPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/** Raw protocol lines (CRLF stripped) → complete replies, in order. */
fun Flow<String>.controlReplies(): Flow<ControlReply> = flow {
    val assembler = ReplyAssembler()
    collect { line -> assembler.feed(line)?.let { emit(it) } }
}

/** Keeps asynchronous replies (650) and parses them into typed events. */
fun Flow<ControlReply>.torEvents(): Flow<TorEvent> = mapNotNull(TorEventParser::parse)

/** Bootstrap progress as the UI shows it; repeated identical updates are dropped. */
fun Flow<TorEvent>.bootstrapInfo(): Flow<BootstrapInfo> =
    filterIsInstance<TorEvent.Bootstrap>()
        .map { BootstrapInfo(it.progress, BootstrapPhase.fromTag(it.tag)) }
        .distinctUntilChanged()
