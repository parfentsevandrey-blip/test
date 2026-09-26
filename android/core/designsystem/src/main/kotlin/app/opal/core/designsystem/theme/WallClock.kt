package app.opal.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Wall-clock time for displays that tick (session duration, the new-identity countdown). The system
 * clock in the app; a fixed instant in screenshot tests, so a slow run cannot move a second.
 */
val LocalWallClock = staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }
