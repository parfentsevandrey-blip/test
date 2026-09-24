package app.opal.core.tunnel

import android.content.Context
import androidx.annotation.StringRes
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.tunnel.BootstrapPhase
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Localized labels shared by notifications, the tile and the UI. */
object Labels {

    @StringRes
    fun phase(phase: BootstrapPhase): Int =
        when (phase) {
            BootstrapPhase.Starting -> R.string.phase_starting
            BootstrapPhase.ConnectingToBridge -> R.string.phase_connecting_bridge
            BootstrapPhase.Handshake -> R.string.phase_handshake
            BootstrapPhase.LoadingDirectory -> R.string.phase_loading_directory
            BootstrapPhase.LoadingRelays -> R.string.phase_loading_relays
            BootstrapPhase.ConnectingToNetwork -> R.string.phase_connecting_network
            BootstrapPhase.BuildingCircuit -> R.string.phase_building_circuit
            BootstrapPhase.Done -> R.string.phase_done
        }

    @StringRes
    fun transport(kind: TransportKind): Int =
        when (kind) {
            TransportKind.Snowflake -> R.string.transport_snowflake
            TransportKind.WebTunnel -> R.string.transport_webtunnel
            TransportKind.Obfs4 -> R.string.transport_obfs4
            TransportKind.Meek -> R.string.transport_meek
            TransportKind.Vanilla -> R.string.transport_vanilla
        }

    /** Human readable throughput, e.g. "1,2 МБ/с". */
    fun speed(context: Context, bytesPerSecond: Long): String {
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val format = DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale))
        return when {
            bytesPerSecond < 1_000 ->
                context.getString(R.string.unit_bytes_per_second, bytesPerSecond.toString())
            bytesPerSecond < 1_000_000 ->
                context.getString(
                    R.string.unit_kilobytes_per_second,
                    format.format(bytesPerSecond / 1_000.0),
                )
            else ->
                context.getString(
                    R.string.unit_megabytes_per_second,
                    format.format(bytesPerSecond / 1_000_000.0),
                )
        }
    }
}
