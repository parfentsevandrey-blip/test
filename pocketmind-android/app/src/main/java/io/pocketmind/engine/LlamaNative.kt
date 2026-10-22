package io.pocketmind.engine

/** JNI bridge to the llama.cpp-based chat session in `pocketmind_jni.cpp`. */
internal object LlamaNative {
    init {
        System.loadLibrary("pocketmind")
    }

    external fun init(nativeLibDir: String)
    external fun systemInfo(): String
    external fun load(path: String, contextSize: Int, threads: Int): Long
    external fun free(handle: Long)
    external fun beginTurn(handle: Long, roles: Array<String>, contents: Array<String>, enableThinking: Boolean): Int
    external fun nextToken(handle: Long): ByteArray?
    external fun cancel(handle: Long)
    external fun contextFull(handle: Long): Boolean
    external fun contextSize(handle: Long): Int
    external fun describe(handle: Long): String
    external fun benchmarkMemory(megabytes: Int, threads: Int): Double

    const val TURN_CONTEXT_OVERFLOW = -2
    const val TURN_CANCELLED = -4
}
