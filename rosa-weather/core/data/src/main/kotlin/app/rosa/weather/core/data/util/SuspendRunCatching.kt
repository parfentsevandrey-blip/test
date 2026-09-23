package app.rosa.weather.core.data.util

import kotlin.coroutines.cancellation.CancellationException

/** Like [runCatching] but never swallows coroutine cancellation. */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
