package app.rosa.weather.widget.layout

import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetDensity
import app.rosa.weather.core.model.WidgetModule
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Axis-aligned box in dp. */
data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
    val area get() = w * h

    fun inset(d: Float) = Box(x + d, y + d, max(0f, w - 2 * d), max(0f, h - 2 * d))
    fun top(height: Float) = Box(x, y, w, min(height, h))
    fun bottomFrom(offset: Float) = Box(x, y + offset, w, max(0f, h - offset))
    fun left(width: Float) = Box(x, y, min(width, w), h)
    fun rightFrom(offset: Float) = Box(x + offset, y, max(0f, w - offset), h)
}

enum class HeroVariant {
    /** Temperature only, as large as the space allows. */
    Micro,

    /** Glyph above a large temperature (square-ish or narrow cells). */
    Compact,

    /** Glyph beside the temperature (short, wide cells). */
    Inline,

    /** Place, glyph, temperature, condition, high/low and the headline. */
    Full,
}

enum class DetailKind { FeelsLike, Wind, Humidity, Precipitation, Uv, Pressure, SunEvent, Air, Visibility }

sealed interface Block {
    val box: Box

    data class Hero(
        override val box: Box,
        val variant: HeroVariant,
        val showCondition: Boolean,
        val showHeadline: Boolean,
        val showHighLow: Boolean,
    ) : Block

    data class HourlyStrip(override val box: Box, val count: Int, val withGlyph: Boolean, val withCurve: Boolean) : Block
    data class HourlyList(override val box: Box, val rows: Int) : Block
    data class DailyList(override val box: Box, val rows: Int, val withBars: Boolean) : Block
    data class DailyColumns(override val box: Box, val count: Int) : Block
    data class Details(override val box: Box, val columns: Int, val items: List<DetailKind>) : Block
    data class Nowcast(override val box: Box) : Block
    data class SunPath(override val box: Box) : Block
}

data class WidgetLayout(
    val widthDp: Float,
    val heightDp: Float,
    val padding: Float,
    val scale: Float,
    val blocks: List<Block>,
    val score: Float,
) {
    val hero: Block.Hero get() = blocks.first { it is Block.Hero } as Block.Hero
}

/** What the data can offer; the engine never allocates space for content that doesn't exist. */
data class LayoutContent(
    val hourlyAvailable: Int = 24,
    val dailyAvailable: Int = 10,
    val details: List<DetailKind> = DetailKind.entries,
    val precipitationSoon: Boolean = false,
    val hasHeadline: Boolean = true,
)

/**
 * Fits the richest sensible composition into *any* widget size — 1×1 up to a full tablet page,
 * portrait or landscape — instead of snapping to a handful of breakpoints.
 *
 * It enumerates a few arrangement templates (hero only, hero on top + stack, hero at the side +
 * stack, hero on top + two columns), fills each greedily in the user's module order, and keeps
 * the one with the best information score minus a penalty for wasted area. Pure Kotlin, so
 * the whole behaviour is unit-tested on the JVM.
 */
object WidgetLayoutEngine {

    fun layout(widthDp: Float, heightDp: Float, config: WidgetConfig, content: LayoutContent = LayoutContent()): WidgetLayout {
        val k = scaleFor(config)
        val minSide = min(widthDp, heightDp)
        val pad = (minSide * 0.085f).coerceIn(7f, 16f) * paddingFactor(config.density)
        val inner = Box(pad, pad, widthDp - 2 * pad, heightDp - 2 * pad)
        val ctx = Ctx(k, gap = (minSide * 0.05f).coerceIn(5f, 12f), config = config, content = content)

        val candidates = buildList {
            add(heroOnly(inner, ctx))
            addAll(verticalTemplates(inner, ctx))
            addAll(horizontalTemplates(inner, ctx))
            addAll(gridTemplates(inner, ctx))
        }.filterNotNull()

        // Honour the user's order: layouts that show their top module get a clear bonus.
        // (Small widgets are exempt: there the hero alone is the right answer, as on iOS.)
        val top = ctx.modules.firstOrNull().takeIf { inner.area >= 180f * 120f * k * k }
        val best = candidates.maxBy { (blocks, score) ->
            score + if (top != null && blocks.any { it.module == top }) 0.8f else 0f
        }
        return WidgetLayout(widthDp, heightDp, pad, k, best.first, best.second)
    }

    private fun scaleFor(config: WidgetConfig): Float {
        val density = when (config.density) {
            WidgetDensity.Compact -> 0.88f
            WidgetDensity.Balanced -> 1f
            WidgetDensity.Airy -> 1.14f
        }
        return density * config.textScale.coerceIn(0.8f, 1.4f)
    }

    private fun paddingFactor(density: WidgetDensity) = when (density) {
        WidgetDensity.Compact -> 0.8f
        WidgetDensity.Balanced -> 1f
        WidgetDensity.Airy -> 1.2f
    }

    private class Ctx(val k: Float, val gap: Float, val config: WidgetConfig, val content: LayoutContent) {
        val modules: List<WidgetModule> = config.modules.filter { it != WidgetModule.Headline }.filter { m ->
            when (m) {
                WidgetModule.Nowcast -> content.precipitationSoon
                WidgetModule.Hourly -> content.hourlyAvailable >= 3
                WidgetModule.Daily -> content.dailyAvailable >= 2
                WidgetModule.Details -> content.details.isNotEmpty()
                else -> true
            }
        }
        val headline = WidgetModule.Headline in config.modules && content.hasHeadline

        fun weight(module: WidgetModule): Float {
            val idx = modules.indexOf(module)
            return if (idx < 0) 0f else max(0.35f, 1f - idx * 0.22f)
        }
    }

    // region Hero

    private fun heroFor(box: Box, ctx: Ctx): Block.Hero {
        val k = ctx.k
        val variant = when {
            box.w >= 150 * k && box.h >= 104 * k -> HeroVariant.Full
            box.w >= 96 * k && box.h >= 36 * k && box.w / box.h >= 1.45f -> HeroVariant.Inline
            box.w >= 54 * k && box.h >= 54 * k -> HeroVariant.Compact
            else -> HeroVariant.Micro
        }
        return Block.Hero(
            box = box,
            variant = variant,
            showCondition = when (variant) {
                HeroVariant.Full -> true
                HeroVariant.Inline -> box.w >= 170 * k
                HeroVariant.Compact -> box.h >= 100 * k && box.w >= 84 * k
                HeroVariant.Micro -> false
            },
            showHeadline = ctx.headline && when (variant) {
                HeroVariant.Full -> box.h >= 128 * k
                HeroVariant.Inline -> box.w >= 230 * k && box.h >= 52 * k
                HeroVariant.Compact -> box.h >= 128 * k && box.w >= 100 * k
                HeroVariant.Micro -> false
            },
            showHighLow = variant == HeroVariant.Full || (variant == HeroVariant.Compact && box.h >= 118 * k),
        )
    }

    private fun heroValue(hero: Block.Hero): Float {
        val base = when (hero.variant) {
            HeroVariant.Micro -> 1f
            HeroVariant.Compact -> 2.2f
            HeroVariant.Inline -> 2.8f
            HeroVariant.Full -> 3.4f
        }
        return base + (if (hero.showCondition) 0.5f else 0f) + (if (hero.showHeadline) 0.9f else 0f) +
            (if (hero.showHighLow) 0.3f else 0f)
    }

    private fun heroOnly(inner: Box, ctx: Ctx): Pair<List<Block>, Float>? {
        val hero = heroFor(inner, ctx)
        // A lone hero in a big widget wastes space; the penalty makes richer layouts win there.
        val waste = (inner.area / (ctx.k * ctx.k) - 170f * 150f).coerceAtLeast(0f) / (170f * 150f)
        return listOf<Block>(hero) to heroValue(hero) - waste * 1.4f
    }

    // endregion

    // region Templates

    private fun verticalTemplates(inner: Box, ctx: Ctx): List<Pair<List<Block>, Float>?> {
        val k = ctx.k
        if (ctx.modules.isEmpty() || inner.h < 96 * k) return emptyList()
        val heights = listOf(
            58f * k, 72f * k, 88f * k, 108f * k, 124f * k, 140f * k,
            inner.h * 0.42f, inner.h * 0.5f,
        ).filter { it >= 50 * k && inner.h - it - ctx.gap >= 34 * k }.distinct()
        return heights.map { hh ->
            val rest = inner.bottomFrom(hh + ctx.gap)
            val fill = stack(rest, ctx)
            if (fill.blocks.isEmpty()) return@map null
            // Space the modules can't use goes to the hero, which scales its type to fill it.
            val leftover = (rest.h - fill.used).coerceAtLeast(0f)
            val hero = heroFor(inner.top(hh + leftover), ctx)
            val shifted = fill.blocks.map { it.withBox(it.box.copy(y = it.box.y + leftover)) }
            listOf<Block>(hero) + shifted to heroValue(hero) + fill.value
        }
    }

    private fun horizontalTemplates(inner: Box, ctx: Ctx): List<Pair<List<Block>, Float>?> {
        val k = ctx.k
        if (ctx.modules.isEmpty() || inner.w < 170 * k) return emptyList()
        val widths = listOf(
            inner.h * 1.3f, inner.h * 1.7f, 96f * k, 128f * k, 160f * k, inner.w * 0.38f, inner.w * 0.46f,
        ).filter { it >= 70 * k && inner.w - it - ctx.gap >= 110 * k }.distinct()
        return widths.map { hw ->
            val hero = heroFor(inner.left(hw), ctx)
            val rest = inner.rightFrom(hw + ctx.gap)
            val fill = if (rest.h < 96 * k) row(rest, ctx) else stack(rest, ctx)
            if (fill.blocks.isEmpty()) return@map null
            val leftover = (rest.h - fill.used).coerceAtLeast(0f)
            val centered = fill.blocks.map { it.withBox(it.box.copy(y = it.box.y + leftover / 2)) }
            val waste = leftover / inner.h
            listOf<Block>(hero) + centered to heroValue(hero) + fill.value - waste * 2f
        }
    }

    private fun gridTemplates(inner: Box, ctx: Ctx): List<Pair<List<Block>, Float>?> {
        val k = ctx.k
        if (ctx.modules.size < 2 || inner.w < 330 * k || inner.h < 300 * k) return emptyList()
        return listOf(0.34f, 0.42f).map { frac ->
            val hh = (inner.h * frac).coerceIn(110 * k, 170 * k)
            val hero = heroFor(inner.top(hh), ctx)
            val rest = inner.bottomFrom(hh + ctx.gap)
            // Strip across the full width, then two columns under it.
            var y = 0f
            val blocks = mutableListOf<Block>()
            var value = 0f
            if (WidgetModule.Hourly in ctx.modules) {
                val stripH = min(110 * k, rest.h * 0.36f)
                val strip = hourlyStrip(rest.top(stripH), ctx)
                if (strip != null) {
                    blocks += strip.first
                    value += strip.second
                    y = stripH + ctx.gap
                }
            }
            val lower = rest.bottomFrom(y)
            val colW = (lower.w - ctx.gap) / 2
            val leftCol = lower.left(colW)
            val rightCol = lower.rightFrom(colW + ctx.gap)
            val remaining = ctx.modules.filter { it != WidgetModule.Hourly || blocks.none { b -> b is Block.HourlyStrip } }
            val half = remaining.size.coerceAtLeast(2) / 2
            val left = stack(leftCol, ctx, remaining.take(half.coerceAtLeast(1)))
            val right = stack(rightCol, ctx, remaining.drop(half.coerceAtLeast(1)))
            if (left.blocks.isEmpty() && right.blocks.isEmpty()) return@map null
            val waste = ((lower.h - left.used) + (lower.h - right.used)).coerceAtLeast(0f) / (2 * inner.h)
            listOf<Block>(hero) + blocks + left.blocks + right.blocks to
                heroValue(hero) + value + left.value + right.value - waste * 2.2f
        }
    }

    // endregion

    // region Filling

    private data class Fill(val blocks: List<Block>, val value: Float, val used: Float)

    /** Vertical stack of modules in priority order; returns the blocks, their value and height used. */
    private fun stack(area: Box, ctx: Ctx, modules: List<WidgetModule> = ctx.modules): Fill {
        val k = ctx.k
        val blocks = mutableListOf<Block>()
        var value = 0f
        var y = 0f
        val pending = modules.toMutableList()
        while (pending.isNotEmpty()) {
            val module = pending.removeAt(0)
            val remaining = area.bottomFrom(y)
            if (remaining.h < 26 * k) break
            val hasFollowers = pending.any { fits(it, area.bottomFrom(y), ctx) }
            val placed: Pair<Block, Float>? = when (module) {
                WidgetModule.Hourly -> if (remaining.w >= 132 * k) {
                    val target = if (hasFollowers) min(remaining.h, 100 * k) else min(remaining.h, 124 * k)
                    hourlyStrip(remaining.top(target), ctx)
                } else {
                    hourlyList(remaining, ctx, maxRows = if (hasFollowers) 6 else 12)
                }
                WidgetModule.Daily -> dailyList(remaining, ctx, maxRows = if (hasFollowers) 5 else ctx.content.dailyAvailable)
                WidgetModule.Details -> details(remaining, ctx, maxRows = if (hasFollowers) 1 else 3)
                WidgetModule.Nowcast -> if (remaining.w >= 140 * k && remaining.h >= 38 * k) {
                    Block.Nowcast(remaining.top(min(52 * k, remaining.h))) to 1.6f
                } else {
                    null
                }
                WidgetModule.SunPath -> if (remaining.w >= 150 * k && remaining.h >= 60 * k) {
                    Block.SunPath(remaining.top(min(84 * k, remaining.h))) to 0.8f
                } else {
                    null
                }
                WidgetModule.Headline -> null
            }
            if (placed != null) {
                blocks += placed.first
                value += placed.second * ctx.weight(module)
                y += placed.first.box.h + ctx.gap
            }
        }
        val used = (y - ctx.gap).coerceAtLeast(0f)
        val arranged = distributeLeftover(blocks, area, used, ctx)
        val finalUsed = arranged.lastOrNull()?.let { it.box.bottom - area.y } ?: 0f
        return Fill(arranged, value, finalUsed)
    }

    /** A single horizontal module beside an inline hero (4×1-style widgets). */
    private fun row(area: Box, ctx: Ctx): Fill {
        for (module in ctx.modules) {
            val placed = when (module) {
                WidgetModule.Hourly -> hourlyStrip(area, ctx)
                WidgetModule.Daily -> dailyColumns(area, ctx)
                WidgetModule.Details -> details(area, ctx, maxRows = 1)
                WidgetModule.Nowcast -> if (area.w >= 140 * ctx.k && area.h >= 36 * ctx.k) Block.Nowcast(area) to 1.6f else null
                else -> null
            }
            if (placed != null) {
                val block = placed.first
                return Fill(listOf(block), placed.second * ctx.weight(module), block.box.h)
            }
        }
        return Fill(emptyList(), 0f, 0f)
    }

    private fun fits(module: WidgetModule, area: Box, ctx: Ctx): Boolean {
        val k = ctx.k
        return when (module) {
            WidgetModule.Hourly -> area.h >= 50 * k && area.w >= 54 * k
            WidgetModule.Daily -> area.h >= 54 * k && area.w >= 56 * k
            WidgetModule.Details -> area.h >= 46 * k && area.w >= 150 * k
            WidgetModule.Nowcast -> area.h >= 38 * k && area.w >= 140 * k
            WidgetModule.SunPath -> area.h >= 60 * k && area.w >= 150 * k
            WidgetModule.Headline -> false
        }
    }

    private fun hourlyStrip(area: Box, ctx: Ctx): Pair<Block, Float>? {
        val k = ctx.k
        if (area.h < 40 * k || area.w < 110 * k) return null
        val withGlyph = area.h >= 56 * k
        val itemW = if (withGlyph) 46 * k else 40 * k
        val count = floor(area.w / itemW).toInt().coerceAtMost(ctx.content.hourlyAvailable).coerceAtMost(12)
        if (count < 3) return null
        // A curve through three points says nothing; it needs a real span of hours.
        val withCurve = area.h >= 92 * k && count >= 5
        val height = when {
            withCurve -> min(area.h, 128 * k)
            withGlyph -> min(area.h, 76 * k)
            else -> min(area.h, 52 * k)
        }
        val value = min(count, 8) * 0.35f + max(0, count - 8) * 0.1f + (if (withCurve) 0.9f else 0f) + (if (withGlyph) 0.4f else 0f)
        return Block.HourlyStrip(area.top(height), count, withGlyph, withCurve) to value
    }

    private fun hourlyList(area: Box, ctx: Ctx, maxRows: Int): Pair<Block, Float>? {
        val k = ctx.k
        if (area.w < 54 * k) return null
        val rowH = 25 * k
        val rows = floor(area.h / rowH).toInt().coerceAtMost(maxRows).coerceAtMost(ctx.content.hourlyAvailable)
        if (rows < 2) return null
        return Block.HourlyList(area.top(rows * rowH), rows) to rows * 0.3f
    }

    private fun dailyList(area: Box, ctx: Ctx, maxRows: Int): Pair<Block, Float>? {
        val k = ctx.k
        if (area.w < 56 * k) return null
        val rowH = 27 * k
        val rows = floor(area.h / rowH).toInt().coerceAtMost(maxRows).coerceAtMost(ctx.content.dailyAvailable)
        if (rows < 2) return null
        val withBars = area.w >= 196 * k
        val perRow = if (withBars) 0.45f else 0.32f
        val value = min(rows, 7) * perRow + max(0, rows - 7) * 0.12f + if (withBars) 0.6f else 0f
        return Block.DailyList(area.top(rows * rowH), rows, withBars) to value
    }

    private fun dailyColumns(area: Box, ctx: Ctx): Pair<Block, Float>? {
        val k = ctx.k
        if (area.h < 58 * k) return null
        val count = floor(area.w / (46 * k)).toInt().coerceAtMost(ctx.content.dailyAvailable).coerceAtMost(7)
        if (count < 3) return null
        return Block.DailyColumns(area.top(min(area.h, 92 * k)), count) to count * 0.38f
    }

    private fun details(area: Box, ctx: Ctx, maxRows: Int): Pair<Block, Float>? {
        val k = ctx.k
        val columns = floor(area.w / (84 * k)).toInt().coerceIn(0, 4)
        val tileH = 48 * k
        val rows = floor((area.h + ctx.gap) / (tileH + ctx.gap)).toInt().coerceAtMost(maxRows)
        if (columns < 2 || rows < 1) return null
        val items = ctx.content.details.take(columns * rows)
        if (items.size < 2) return null
        val usedRows = (items.size + columns - 1) / columns
        val h = usedRows * tileH + (usedRows - 1) * ctx.gap
        return Block.Details(area.top(h), columns, items) to items.size * 0.3f
    }

    /**
     * Grows flexible blocks into leftover height (curves get taller, rows breathe), then spreads
     * whatever is left as even spacing so nothing looks stranded at the top.
     */
    private fun distributeLeftover(blocks: List<Block>, area: Box, used: Float, ctx: Ctx): List<Block> {
        if (blocks.isEmpty()) return blocks
        var leftover = area.h - used
        if (leftover <= 1f) return reposition(blocks, area, ctx.gap)
        val grown = blocks.map { b ->
            if (leftover <= 0f) return@map b
            when (b) {
                is Block.HourlyStrip -> {
                    val max = if (b.withCurve) 150 * ctx.k else if (b.withGlyph) 84 * ctx.k else 56 * ctx.k
                    val add = min(leftover, (max - b.box.h).coerceAtLeast(0f))
                    leftover -= add
                    b.copy(box = b.box.copy(h = b.box.h + add))
                }
                is Block.DailyList -> {
                    val add = min(leftover, b.rows * 9 * ctx.k)
                    leftover -= add
                    b.copy(box = b.box.copy(h = b.box.h + add))
                }
                is Block.HourlyList -> {
                    val add = min(leftover, b.rows * 7 * ctx.k)
                    leftover -= add
                    b.copy(box = b.box.copy(h = b.box.h + add))
                }
                is Block.SunPath -> {
                    val add = min(leftover, 30 * ctx.k)
                    leftover -= add
                    b.copy(box = b.box.copy(h = b.box.h + add))
                }
                else -> b
            }
        }
        val extraGap = if (grown.size > 1) min(leftover / (grown.size - 1), 14 * ctx.k) else 0f
        return reposition(grown, area, ctx.gap + extraGap)
    }

    private fun reposition(blocks: List<Block>, area: Box, gap: Float): List<Block> {
        var y = area.y
        return blocks.map { b ->
            val moved = b.withBox(b.box.copy(x = area.x, y = y, w = area.w))
            y += b.box.h + gap
            moved
        }
    }

    private val Block.module: WidgetModule?
        get() = when (this) {
            is Block.Hero -> null
            is Block.HourlyStrip, is Block.HourlyList -> WidgetModule.Hourly
            is Block.DailyList, is Block.DailyColumns -> WidgetModule.Daily
            is Block.Details -> WidgetModule.Details
            is Block.Nowcast -> WidgetModule.Nowcast
            is Block.SunPath -> WidgetModule.SunPath
        }

    private fun Block.withBox(box: Box): Block = when (this) {
        is Block.Hero -> copy(box = box)
        is Block.HourlyStrip -> copy(box = box)
        is Block.HourlyList -> copy(box = box)
        is Block.DailyList -> copy(box = box)
        is Block.DailyColumns -> copy(box = box)
        is Block.Details -> copy(box = box)
        is Block.Nowcast -> copy(box = box)
        is Block.SunPath -> copy(box = box)
    }

    // endregion
}
