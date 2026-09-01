package app.veil.vpn.model

import androidx.annotation.StringRes
import app.veil.vpn.R

/**
 * Which TLS Client Hello the fronted transports imitate.
 *
 * This exists because modern DPI does not look for a protocol signature any
 * more — it hashes the Client Hello (JA3, and since 2023 JA4) and asks whether
 * that fingerprint is plausible for the network it came from. Two failure modes
 * matter:
 *
 *  - A library's own fingerprint. Go's crypto/tls has a static, well-known
 *    hash, and anything carrying it is a proxy with near-certainty.
 *  - A fingerprint that is plausible but *stale*: pinning an old browser build
 *    is its own tell, because nobody is still running that version.
 *
 * So every profile here maps to uTLS's `_Auto` variant where one exists, which
 * tracks the newest Client Hello the bundled uTLS knows.
 *
 * The two transports do not accept the same names. lyrebird (obfs4, meek_lite,
 * webtunnel) carries uTLS's full table; Snowflake ships a much shorter one with
 * no Edge and no Android entry. Sending a name Snowflake does not know makes it
 * refuse to start, so each profile declares its own fallback.
 */
enum class TlsProfile(
    @StringRes val labelRes: Int,
    /** Value for lyrebird's `utls=` bridge argument. */
    val lyrebirdName: String,
    /** Value for Snowflake's `utls-imitate=` argument. */
    val snowflakeName: String,
    @StringRes val rationaleRes: Int,
) {
    /**
     * The default: pick one of the plausible profiles per installation.
     *
     * A single global default is its own tell. Analysis of proxy detection
     * describes the giveaway as "dozens of clients connecting to one address
     * with an identical fingerprint of the same, often outdated, browser" — and
     * that is precisely what every copy of an app shipping one hard-coded hello
     * produces in aggregate. Choosing per install spreads the population out,
     * while staying stable on any one device, which matters because changing
     * fingerprint mid-session is reported to make a blocking penalty longer
     * rather than shorter.
     */
    AUTO(
        labelRes = R.string.tls_auto,
        lyrebirdName = "hellofirefox_auto",
        snowflakeName = "hellofirefox_auto",
        rationaleRes = R.string.tls_auto_desc,
    ),

    /**
     * Firefox is common enough to be unremarkable, and unlike Chrome it is not
     * what most proxy tooling reaches for first, so a static Firefox hello is a
     * weaker discriminator than a static Chrome one.
     */
    FIREFOX(
        labelRes = R.string.tls_firefox,
        lyrebirdName = "hellofirefox_auto",
        snowflakeName = "hellofirefox_auto",
        rationaleRes = R.string.tls_firefox_desc,
    ),

    CHROME(
        labelRes = R.string.tls_chrome,
        lyrebirdName = "hellochrome_auto",
        snowflakeName = "hellochrome_auto",
        rationaleRes = R.string.tls_chrome_desc,
    ),

    /** Snowflake has no Edge entry; Edge is Chromium, so Chrome is the honest fallback. */
    EDGE(
        labelRes = R.string.tls_edge,
        lyrebirdName = "helloedge_auto",
        snowflakeName = "hellochrome_auto",
        rationaleRes = R.string.tls_edge_desc,
    ),

    /**
     * An Android HTTP stack rather than a browser. The most plausible thing for
     * traffic that really is coming from a phone app — but uTLS only carries an
     * Android 11 era hello, and Snowflake carries none at all.
     */
    ANDROID_APP(
        labelRes = R.string.tls_android,
        lyrebirdName = "helloandroid_11",
        snowflakeName = "hellofirefox_auto",
        rationaleRes = R.string.tls_android_desc,
    ),

    /**
     * Fresh randomness per connection. Defeats an exact-hash blocklist, and is
     * itself a signal to anything that scores plausibility rather than matching
     * hashes — no real client produces a different hello every time.
     */
    RANDOMISED(
        labelRes = R.string.tls_random,
        lyrebirdName = "hellorandomizedalpn",
        snowflakeName = "hellorandomizedalpn",
        rationaleRes = R.string.tls_random_desc,
    );

    companion object {
        val Default = AUTO

        /**
         * The profiles [AUTO] chooses between. Randomisation is excluded on
         * purpose: it is a plausibility signal in its own right.
         */
        private val PLAUSIBLE = listOf(FIREFOX, CHROME, EDGE, ANDROID_APP)

        /**
         * Resolves [AUTO] against a value that is stable for this installation
         * and meaningless anywhere else.
         */
        fun resolve(profile: TlsProfile, installSeed: Int): TlsProfile =
            if (profile != AUTO) {
                profile
            } else {
                PLAUSIBLE[Math.floorMod(installSeed, PLAUSIBLE.size)]
            }
    }
}

/**
 * How Snowflake's DTLS Client Hello is shaped.
 *
 * Snowflake's data path is WebRTC over UDP, so it never meets the TCP/TLS
 * handshake heuristics at all — but the DTLS handshake carries a fingerprint of
 * its own, and pion's default is distinctive. Mimicking a real browser's DTLS
 * hello is the closer analogue of what the TLS profile does above.
 */
enum class DtlsProfile(
    @StringRes val labelRes: Int,
    val argument: String,
    @StringRes val rationaleRes: Int,
) {
    MIMIC(
        labelRes = R.string.dtls_mimic,
        argument = "mimic",
        rationaleRes = R.string.dtls_mimic_desc,
    ),
    RANDOMISE_MIMIC(
        labelRes = R.string.dtls_random_mimic,
        argument = "randomizemimic",
        rationaleRes = R.string.dtls_random_mimic_desc,
    ),
    RANDOMISE(
        labelRes = R.string.dtls_random,
        argument = "randomize",
        rationaleRes = R.string.dtls_random_desc,
    ),
    OFF(
        labelRes = R.string.dtls_off,
        argument = "disable",
        rationaleRes = R.string.dtls_off_desc,
    );

    companion object {
        val Default = MIMIC
    }
}
