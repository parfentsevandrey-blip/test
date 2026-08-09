package app.quire.engine.design

import android.content.Context
import android.os.Build
import androidx.annotation.ColorInt

/**
 * The Material 3 colour scheme the device is wearing, read straight off the platform.
 *
 * Android publishes its whole scheme as ordinary colour resources — `system_primary_light`,
 * `system_surface_container_dark` and the rest — recomputed from the wallpaper whenever it
 * changes. They have been public since API 34, so this is a real scheme rather than an
 * approximation of one: the numbers here are the numbers a Material component would use.
 *
 * Both halves are read at once and the caller picks, because a widget on a dark wallpaper and an
 * app following the system can want different halves of the same scheme in the same second.
 *
 * Null below API 34, and null on a device that has the resources but no wallpaper-derived values
 * for them — in both cases the app falls back to deriving its own palette from a seed, which it
 * can always do.
 */
/*
 * The constructor is internal rather than private so a test can hand the theme a scheme that is
 * deliberately illegible and watch it be refused. There is no other way to prove the fallback,
 * since a real device's scheme always passes.
 */
class SystemScheme internal constructor(
    @get:ColorInt val primary: Int,
    @get:ColorInt val onPrimary: Int,
    @get:ColorInt val primaryContainer: Int,
    @get:ColorInt val secondaryContainer: Int,
    @get:ColorInt val tertiaryContainer: Int,
    @get:ColorInt val surface: Int,
    @get:ColorInt val surfaceContainer: Int,
    @get:ColorInt val surfaceContainerHigh: Int,
    @get:ColorInt val onSurface: Int,
    @get:ColorInt val onSurfaceVariant: Int,
    @get:ColorInt val outline: Int,
    @get:ColorInt val outlineVariant: Int,
) {

    /** Two schemes are equal when every role is, so a theme can skip an identical rebuild. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SystemScheme) return false
        return primary == other.primary &&
            onPrimary == other.onPrimary &&
            primaryContainer == other.primaryContainer &&
            secondaryContainer == other.secondaryContainer &&
            tertiaryContainer == other.tertiaryContainer &&
            surface == other.surface &&
            surfaceContainer == other.surfaceContainer &&
            surfaceContainerHigh == other.surfaceContainerHigh &&
            onSurface == other.onSurface &&
            onSurfaceVariant == other.onSurfaceVariant &&
            outline == other.outline &&
            outlineVariant == other.outlineVariant
    }

    override fun hashCode(): Int {
        var result = primary
        result = 31 * result + onPrimary
        result = 31 * result + primaryContainer
        result = 31 * result + secondaryContainer
        result = 31 * result + tertiaryContainer
        result = 31 * result + surface
        result = 31 * result + surfaceContainer
        result = 31 * result + surfaceContainerHigh
        result = 31 * result + onSurface
        result = 31 * result + onSurfaceVariant
        result = 31 * result + outline
        result = 31 * result + outlineVariant
        return result
    }

    override fun toString(): String =
        "SystemScheme(primary=#${(primary.toLong() and 0xFFFFFFFFL).toString(16)})"

    companion object {

        /** Whether this device publishes a scheme at all. */
        val supported: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

        /**
         * Reads the half of the scheme matching [dark], or null where there is none to read.
         *
         * Every lookup is wrapped: a resource that is public in the SDK can still be missing on a
         * device whose vendor stripped it, and a calendar that will not open because a colour was
         * absent would be a poor trade for a palette.
         */
        fun read(context: Context, dark: Boolean): SystemScheme? {
            if (!supported) return null
            return runCatching {
                val res = context.resources
                fun colour(light: Int, night: Int): Int =
                    res.getColor(if (dark) night else light, context.theme)

                SystemScheme(
                    primary = colour(
                        android.R.color.system_primary_light,
                        android.R.color.system_primary_dark,
                    ),
                    onPrimary = colour(
                        android.R.color.system_on_primary_light,
                        android.R.color.system_on_primary_dark,
                    ),
                    primaryContainer = colour(
                        android.R.color.system_primary_container_light,
                        android.R.color.system_primary_container_dark,
                    ),
                    secondaryContainer = colour(
                        android.R.color.system_secondary_container_light,
                        android.R.color.system_secondary_container_dark,
                    ),
                    tertiaryContainer = colour(
                        android.R.color.system_tertiary_container_light,
                        android.R.color.system_tertiary_container_dark,
                    ),
                    surface = colour(
                        android.R.color.system_surface_light,
                        android.R.color.system_surface_dark,
                    ),
                    surfaceContainer = colour(
                        android.R.color.system_surface_container_light,
                        android.R.color.system_surface_container_dark,
                    ),
                    surfaceContainerHigh = colour(
                        android.R.color.system_surface_container_high_light,
                        android.R.color.system_surface_container_high_dark,
                    ),
                    onSurface = colour(
                        android.R.color.system_on_surface_light,
                        android.R.color.system_on_surface_dark,
                    ),
                    onSurfaceVariant = colour(
                        android.R.color.system_on_surface_variant_light,
                        android.R.color.system_on_surface_variant_dark,
                    ),
                    outline = colour(
                        android.R.color.system_outline_light,
                        android.R.color.system_outline_dark,
                    ),
                    outlineVariant = colour(
                        android.R.color.system_outline_variant_light,
                        android.R.color.system_outline_variant_dark,
                    ),
                )
            }.getOrNull()
        }
    }
}
