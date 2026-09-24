package org.torproject.jni

/**
 * JNI binding for `libtor.so` from tor-android (Guardian Project).
 *
 * Every name here is dictated by the native library: it exports
 * `Java_org_torproject_jni_TorService_*` functions and reads/writes the instance fields
 * [torConfiguration] and [torControlFd] through `GetFieldID`. It is *not* an Android service — the
 * library's own `TorService` component is intentionally not used (see CLAUDE.md, ADR 1). Keep the
 * R8 rules in `consumer-rules.pro` in sync with this class.
 */
@Suppress("FunctionName", "MemberVisibilityCanBePrivate")
class TorService {

    /**
     * Opaque `tor_main_configuration_t*`; the native code requires a non-zero value before
     * creation.
     */
    @JvmField var torConfiguration: Long = -1

    /**
     * Our end of the owning-controller socketpair, filled by [mainConfigurationSetupControlSocket].
     */
    @JvmField var torControlFd: Int = -1

    external fun createTorConfiguration(): Boolean

    external fun mainConfigurationFree()

    external fun mainConfigurationSetCommandLine(args: Array<String>): Boolean

    external fun mainConfigurationSetupControlSocket(): Boolean

    /** Runs Tor's main loop on the calling thread; returns its exit status. */
    external fun runMain(): Int

    external fun apiGetProviderVersion(): String

    external fun libeventVersion(): String

    external fun opensslVersion(): String

    external fun zlibVersion(): String

    external fun zstdVersion(): String

    companion object {
        init {
            System.loadLibrary("tor")
        }
    }
}
