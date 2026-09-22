package app.papersky.weather.screenshots

import android.graphics.Bitmap
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.FileOutputStream

/** Screenshot helpers. Tests only write images when run with -Ppapersky.screenshots. */
object Shots {
    val enabled: Boolean get() = System.getProperty("papersky.screenshots") == "true"

    val dir: File by lazy { File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() } }

    fun assumeEnabled() = assumeTrue("Run with -Ppapersky.screenshots to render images", enabled)

    fun save(name: String, bitmap: Bitmap): File {
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
