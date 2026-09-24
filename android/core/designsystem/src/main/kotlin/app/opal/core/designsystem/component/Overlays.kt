package app.opal.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.opal.core.designsystem.glass.ToastState

/**
 * Floating overlays live above the content layer (they refract it), so screens do not draw them
 * themselves: they ask the host at the root of the app.
 */
@Stable
class SheetHost {
    var current by mutableStateOf<SheetSpec?>(null)
        private set

    fun show(title: String?, content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit) {
        current = SheetSpec(title, content)
    }

    fun dismiss() {
        current = null
    }
}

class SheetSpec(
    val title: String?,
    val content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
)

val LocalSheetHost = staticCompositionLocalOf { SheetHost() }

val LocalToastState = staticCompositionLocalOf { ToastState() }
