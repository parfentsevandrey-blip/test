package app.rosa.weather.widget.layout

import app.rosa.weather.core.model.WidgetConfig
import org.junit.Test

class LayoutDump {
    @Test
    fun dump() {
        val sizes = listOf(57 to 60, 72 to 76, 150 to 76, 230 to 76, 320 to 76, 150 to 170, 230 to 170, 330 to 170, 330 to 260, 330 to 400, 80 to 330, 150 to 330, 230 to 330, 400 to 600, 600 to 400)
        val out = StringBuilder()
        for ((w, h) in sizes) {
            val l = WidgetLayoutEngine.layout(w.toFloat(), h.toFloat(), WidgetConfig(), LayoutContent(precipitationSoon = true))
            out.appendLine("== ${w}x$h score=${"%.2f".format(l.score)}")
            l.blocks.forEach { b ->
                val bx = b.box
                out.appendLine("   ${b::class.simpleName} [${bx.x.toInt()},${bx.y.toInt()} ${bx.w.toInt()}x${bx.h.toInt()}] " + when (b) {
                    is Block.Hero -> "${b.variant} cond=${b.showCondition} head=${b.showHeadline} hl=${b.showHighLow}"
                    is Block.HourlyStrip -> "n=${b.count} glyph=${b.withGlyph} curve=${b.withCurve}"
                    is Block.DailyList -> "rows=${b.rows} bars=${b.withBars}"
                    is Block.HourlyList -> "rows=${b.rows}"
                    is Block.Details -> "cols=${b.columns} items=${b.items.size}"
                    is Block.DailyColumns -> "n=${b.count}"
                    else -> ""
                })
            }
        }
        java.io.File("build/layout-dump.txt").writeText(out.toString())
    }
}
