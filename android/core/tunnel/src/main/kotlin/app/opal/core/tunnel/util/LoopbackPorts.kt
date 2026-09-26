package app.opal.core.tunnel.util

import java.net.InetAddress
import java.net.ServerSocket

internal object LoopbackPorts {
    private val LOOPBACK = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    /** A TCP port on 127.0.0.1 that is free right now, picked at random by the kernel. */
    fun free(): Int = ServerSocket(0, 1, LOOPBACK).use { it.localPort }
}
