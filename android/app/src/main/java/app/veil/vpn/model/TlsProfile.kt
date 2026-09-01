package app.veil.vpn.model

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
    val label: String,
    /** Value for lyrebird's `utls=` bridge argument. */
    val lyrebirdName: String,
    /** Value for Snowflake's `utls-imitate=` argument. */
    val snowflakeName: String,
    val rationale: String,
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
        label = "Automatic",
        lyrebirdName = "hellofirefox_auto",
        snowflakeName = "hellofirefox_auto",
        rationale = "Chosen once per installation, so no two devices look alike.",
    ),

    /**
     * Firefox is common enough to be unremarkable, and unlike Chrome it is not
     * what most proxy tooling reaches for first, so a static Firefox hello is a
     * weaker discriminator than a static Chrome one.
     */
    FIREFOX(
        label = "Firefox",
        lyrebirdName = "hellofirefox_auto",
        snowflakeName = "hellofirefox_auto",
        rationale = "Common, current, and not the profile proxy software defaults to.",
    ),

    CHROME(
        label = "Chrome",
        lyrebirdName = "hellochrome_auto",
        snowflakeName = "hellochrome_auto",
        rationale = "The most common browser, and for that reason the most imitated.",
    ),

    /** Snowflake has no Edge entry; Edge is Chromium, so Chrome is the honest fallback. */
    EDGE(
        label = "Edge",
        lyrebirdName = "helloedge_auto",
        snowflakeName = "hellochrome_auto",
        rationale = "Chromium under another name; unremarkable on Windows networks.",
    ),

    /**
     * An Android HTTP stack rather than a browser. The most plausible thing for
     * traffic that really is coming from a phone app — but uTLS only carries an
     * Android 11 era hello, and Snowflake carries none at all.
     */
    ANDROID_APP(
        label = "Android app",
        lyrebirdName = "helloandroid_11",
        snowflakeName = "hellofirefox_auto",
        rationale = "Matches what this actually is: an app, not a browser. The profile is dated.",
    ),

    /**
     * Fresh randomness per connection. Defeats an exact-hash blocklist, and is
     * itself a signal to anything that scores plausibility rather than matching
     * hashes — no real client produces a different hello every time.
     */
    RANDOMISED(
        label = "Randomised",
        lyrebirdName = "hellorandomizedalpn",
        snowflakeName = "hellorandomizedalpn",
        rationale = "Matches no blocklist entry, and matches no real client either.",
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
    val label: String,
    val argument: String,
    val rationale: String,
) {
    MIMIC(
        label = "Imitate a browser",
        argument = "mimic",
        rationale = "Looks like WebRTC from a real browser, which is what it is pretending to be.",
    ),
    RANDOMISE_MIMIC(
        label = "Imitate a random browser",
        argument = "randomizemimic",
        rationale = "A different browser each time: harder to blocklist, easier to notice.",
    ),
    RANDOMISE(
        label = "Randomise",
        argument = "randomize",
        rationale = "Matches nothing, including anything real.",
    ),
    OFF(
        label = "Leave as-is",
        argument = "disable",
        rationale = "The library's own handshake. Distinctive, and the fastest to set up.",
    );

    companion object {
        val Default = MIMIC
    }
}
