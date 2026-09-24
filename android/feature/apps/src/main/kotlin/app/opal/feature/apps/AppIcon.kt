package app.opal.feature.apps

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Launcher icons, decoded off the main thread at display size and kept in a small LRU cache. */
private object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(160)

    fun cached(pkg: String): ImageBitmap? = cache.get(pkg)

    suspend fun load(context: Context, pkg: String, sizePx: Int): ImageBitmap? =
        cache.get(pkg)
            ?: withContext(Dispatchers.IO) {
                try {
                    context.packageManager
                        .getApplicationIcon(pkg)
                        .toBitmap(sizePx, sizePx)
                        .asImageBitmap()
                        .also { cache.put(pkg, it) }
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }
}

@Composable
internal fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by
        produceState(AppIconCache.cached(packageName), packageName) {
            value = AppIconCache.load(context, packageName, px)
        }
    Box(modifier.size(size)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}
