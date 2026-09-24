package app.rosa.weather.widget.motion

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * The live weather tiles in `res/` are exactly what [MotionResources] generates. Run with
 * `-Prosa.motion` to regenerate them after changing the generator.
 */
class MotionResourcesTest {
    private val res = File("src/main/res")

    @Test
    fun resourcesMatchTheGenerator() {
        val files = MotionResources.files()
        if (System.getProperty("rosa.motion") == "true") {
            files.forEach { (path, xml) -> File(res, path).apply { parentFile.mkdirs() }.writeText(xml) }
            println("Wrote ${files.size} files, ${files.values.sumOf { it.length } / 1024} KB")
            return
        }
        files.forEach { (path, xml) ->
            val file = File(res, path)
            assertWithMessage("$path is missing; regenerate with -Prosa.motion").that(file.exists()).isTrue()
            assertWithMessage("$path is stale; regenerate with -Prosa.motion").that(file.readText()).isEqualTo(xml)
        }
        // Nothing generated earlier may linger once the generator stops producing it.
        val expected = files.keys.map { File(res, it).canonicalPath }.toSet()
        val strays = listOf("drawable", "layout", "color").flatMap { dir ->
            File(res, dir).listFiles { f -> f.name.startsWith("motion_") }.orEmpty().filter { it.canonicalPath !in expected }
        }
        assertWithMessage("stray generated files").that(strays.map { it.name }).isEmpty()
    }
}
