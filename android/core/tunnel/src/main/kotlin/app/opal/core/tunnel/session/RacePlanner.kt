package app.opal.core.tunnel.session

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.ConnectionMode
import app.opal.core.model.settings.TunnelMemory
import app.opal.core.model.tunnel.NetworkKind

/** Which bridges to give Tor first and which to add if that does not make progress. */
internal data class BridgePlan(
    val initial: List<BridgeLine>,
    /** Added all at once when [initial] shows no bootstrap progress for a while (Auto only). */
    val expansion: List<BridgeLine>,
    /** The Settings API may add bridges for this mode (not for Custom). */
    val settingsApiAllowed: Boolean,
) {
    val all: List<BridgeLine>
        get() = (initial + expansion).distinctBy { it.raw }

    val initialKinds: Set<TransportKind>
        get() = initial.map { it.transport }.toSet()
}

internal object RacePlanner {

    /** Transport order for the very first race (all at once; order only affects SETCONF order). */
    private val PREFERENCE =
        listOf(
            TransportKind.Snowflake,
            TransportKind.WebTunnel,
            TransportKind.Obfs4,
            TransportKind.Meek,
            TransportKind.Vanilla,
        )

    fun plan(
        settings: AppSettings,
        memory: TunnelMemory,
        network: NetworkKind?,
        candidates: Map<TransportKind, List<BridgeLine>>,
        custom: List<BridgeLine>,
    ): BridgePlan {
        val ordered = PREFERENCE.filter { candidates[it].orEmpty().isNotEmpty() }
        fun lines(kinds: Collection<TransportKind>) = kinds.flatMap { candidates[it].orEmpty() }
        return when (val mode = settings.connectionMode) {
            ConnectionMode.Custom -> BridgePlan(custom, emptyList(), settingsApiAllowed = false)
            ConnectionMode.Auto -> {
                val winner =
                    (network?.let { memory.winners[it] } ?: memory.winners.values.firstOrNull())
                        ?.takeIf { it in ordered }
                if (winner == null) {
                    // First run on this device (or winner no longer available): race everything
                    // now.
                    BridgePlan(lines(ordered), emptyList(), settingsApiAllowed = true)
                } else {
                    val preferredBridge = network?.let { memory.lastWorkingBridge[it] }
                    val winnerLines =
                        candidates[winner].orEmpty().sortedByDescending { it.id == preferredBridge }
                    BridgePlan(winnerLines, lines(ordered - winner), settingsApiAllowed = true)
                }
            }
            else -> {
                val kind = requireNotNull(mode.transport)
                BridgePlan(
                    candidates[kind].orEmpty(),
                    emptyList(),
                    settingsApiAllowed = ModePolicy.of(mode).settingsApiBeforeConnect,
                )
            }
        }
    }
}
