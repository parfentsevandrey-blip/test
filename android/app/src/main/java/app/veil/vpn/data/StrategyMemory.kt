package app.veil.vpn.data

import android.content.Context
import app.veil.vpn.core.VeilLog
import app.veil.vpn.model.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * What has worked on which network.
 *
 * This no longer chooses anything. The app connects with the method the user
 * picked and only that, so the running total of what worked where is a record
 * rather than an input: the diagnostic prints it, and it is what the settings
 * screen forgets when asked. Kept because "meek has worked here nine times out
 * of ten and Snowflake never" is the single most useful sentence anyone can
 * have when deciding which method to pick, and nothing else in the app knows
 * it.
 *
 * Records are keyed by the opaque network fingerprint, which is built from the
 * link's own properties and never identifies the user.
 */
class StrategyMemory(context: Context) {

    private val file = File(context.filesDir, "strategy-memory.json")

    data class Record(
        val transport: Transport,
        val successes: Int,
        val failures: Int,
        val lastSuccessMillis: Long,
        val medianBootstrapMillis: Long,
    )

    private var records: MutableMap<String, MutableMap<Transport, Record>> = mutableMapOf()

    suspend fun load() = withContext(Dispatchers.IO) {
        records = runCatching { parse(file.readText()) }.getOrDefault(mutableMapOf())
    }

    suspend fun recordSuccess(fingerprint: String, transport: Transport, bootstrapMillis: Long) {
        update(fingerprint, transport) { existing ->
            val previous = existing?.medianBootstrapMillis ?: bootstrapMillis
            Record(
                transport = transport,
                successes = (existing?.successes ?: 0) + 1,
                failures = existing?.failures ?: 0,
                lastSuccessMillis = System.currentTimeMillis(),
                // A cheap running estimate; good enough to rank two rungs that
                // both work by which one is actually usable.
                medianBootstrapMillis = (previous + bootstrapMillis) / 2,
            )
        }
        VeilLog.i("memory", "${transport.torName} worked here in ${bootstrapMillis / 1000}s")
    }

    suspend fun recordFailure(fingerprint: String, transport: Transport) {
        update(fingerprint, transport) { existing ->
            Record(
                transport = transport,
                successes = existing?.successes ?: 0,
                failures = (existing?.failures ?: 0) + 1,
                lastSuccessMillis = existing?.lastSuccessMillis ?: 0,
                medianBootstrapMillis = existing?.medianBootstrapMillis ?: 0,
            )
        }
    }

    suspend fun forget() = withContext(Dispatchers.IO) {
        records.clear()
        runCatching { file.delete() }
        Unit
    }

    fun describe(fingerprint: String): List<Record> =
        records[fingerprint]?.values?.sortedByDescending { it.successes - it.failures }.orEmpty()

    private suspend fun update(
        fingerprint: String,
        transport: Transport,
        transform: (Record?) -> Record,
    ) = withContext(Dispatchers.IO) {
        val forNetwork = records.getOrPut(fingerprint) { mutableMapOf() }
        forNetwork[transport] = transform(forNetwork[transport])
        runCatching { file.writeText(serialise()) }
            .onFailure { VeilLog.w("memory", "could not persist: $it") }
        Unit
    }

    private fun serialise(): String {
        val root = JSONObject()
        records.forEach { (fingerprint, byTransport) ->
            val entry = JSONObject()
            byTransport.forEach { (transport, record) ->
                entry.put(
                    transport.name,
                    JSONObject()
                        .put("s", record.successes)
                        .put("f", record.failures)
                        .put("t", record.lastSuccessMillis)
                        .put("m", record.medianBootstrapMillis),
                )
            }
            root.put(fingerprint, entry)
        }
        return root.toString()
    }

    private fun parse(json: String): MutableMap<String, MutableMap<Transport, Record>> {
        val root = JSONObject(json)
        val result = mutableMapOf<String, MutableMap<Transport, Record>>()
        for (fingerprint in root.keys()) {
            val entry = root.optJSONObject(fingerprint) ?: continue
            val byTransport = mutableMapOf<Transport, Record>()
            for (name in entry.keys()) {
                val transport = runCatching { Transport.valueOf(name) }.getOrNull() ?: continue
                val record = entry.optJSONObject(name) ?: continue
                byTransport[transport] = Record(
                    transport = transport,
                    successes = record.optInt("s"),
                    failures = record.optInt("f"),
                    lastSuccessMillis = record.optLong("t"),
                    medianBootstrapMillis = record.optLong("m"),
                )
            }
            result[fingerprint] = byTransport
        }
        return result
    }

}
