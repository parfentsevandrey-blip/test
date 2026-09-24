package app.rosa.weather.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import app.rosa.weather.R
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the adaptive launcher icon under circle and squircle masks to `build/screens/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = android.app.Application::class)
class LauncherIconTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `launcher icon renders under common masks`() {
        val size = 432
        val layers = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(layers).let { canvas ->
            listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach { id ->
                ContextCompat.getDrawable(context, id)!!.apply { setBounds(0, 0, size, size) }.draw(canvas)
            }
        }
        // The launcher shows the middle 72 of the 108 units.
        val visible = Bitmap.createBitmap(layers, size / 6, size / 6, size * 2 / 3, size * 2 / 3)
        val shown = visible.width.toFloat()
        val masks = listOf(
            Path().apply { addCircle(shown / 2, shown / 2, shown / 2, Path.Direction.CW) },
            Path().apply { addRoundRect(0f, 0f, shown, shown, shown * 0.3f, shown * 0.3f, Path.Direction.CW) },
        )
        val sheet = Bitmap.createBitmap((shown.toInt() + 24) * masks.size + 24, shown.toInt() + 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(236, 238, 244))
        masks.forEachIndexed { i, mask ->
            val tile = Bitmap.createBitmap(visible.width, visible.height, Bitmap.Config.ARGB_8888)
            Canvas(tile).apply {
                drawPath(mask, Paint(Paint.ANTI_ALIAS_FLAG))
                drawBitmap(visible, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
            }
            canvas.drawBitmap(tile, 24f + i * (shown + 24), 24f, null)
        }
        val out = File("build/screens").apply { mkdirs() }
        File(out, "launcher-icon.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        exportDocImage(sheet, "launcher-icon", 480)
        // The drop sits in the middle and is actually drawn: not a blank or clipped icon.
        assertThat(Color.alpha(layers.getPixel(size / 2, size / 2))).isEqualTo(255)
        assertThat(layers.getPixel(size / 2, size * 60 / 108)).isNotEqualTo(layers.getPixel(size / 8, size * 60 / 108))
    }
}
