package app.opal.core.tunnel.hev

/**
 * JNI entry points of `libhev-socks5-tunnel.so`. They are bound in the library's `JNI_OnLoad` with
 * `RegisterNatives` against this exact class (`-DPKGNAME=app/opal/core/tunnel/hev
 * -DCLSNAME=HevNative` in core/tunnel/build.gradle.kts), so the class and method names must not
 * change or be obfuscated.
 */
@Suppress("FunctionName")
internal object HevNative {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    /** Starts the tunnel on a worker thread. The TUN [fd] stays owned by the caller. */
    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int): Boolean

    /** Stops and joins the worker thread (blocking). */
    @JvmStatic external fun TProxyStopService(): Boolean

    @JvmStatic external fun TProxyIsRunning(): Boolean

    /** `[txPackets, txBytes, rxPackets, rxBytes]` since start. */
    @JvmStatic external fun TProxyGetStats(): LongArray
}
