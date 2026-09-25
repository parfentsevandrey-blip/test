package app.rosa.weather.widget.calendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.LruCache
import android.util.SizeF
import androidx.core.graphics.createBitmap
import app.rosa.weather.widget.motion.LiveWeather
import app.rosa.weather.widget.render.calendar.CalendarTargets
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.YearMonth

/**
 * Months drawn ahead, whole: for each calendar widget the months either side of the one it
 * shows, exactly as the launcher gets them — the picture and where it answers taps, at every
 * size. An arrow then only hands the launcher what is already drawn: no painting, no waiting.
 *
 * Kept in memory and on disk, since the process is often long gone when someone taps. A page is
 * good for the day it was drawn on and the widget's settings and sizes then ([Page.stamp]); what
 * it shows of the weather and the phone's events ([Page.shows]) is checked when there is time.
 */
internal object CalendarPages {
    private const val MAGIC = 0x524F5341
    private const val FORMAT = 2

    class Size(val size: SizeF, val bitmap: Bitmap, val targets: CalendarTargets)

    class Page(
        val month: YearMonth,
        val today: LocalDate,
        /** Everything it depends on but its month, its day and [shows]: settings, sizes, theme. */
        val stamp: Int,
        /** What it shows of the weather and the phone's events. */
        val shows: Int,
        val live: LiveWeather?,
        val radius: Float,
        val sizes: List<Size>,
    ) {
        val bytes: Int get() = sizes.sumOf { it.bitmap.allocationByteCount }
    }

    private val memory = object : LruCache<String, Page>(28 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Page) = value.bytes
    }

    /** The page for [month] of widget [widgetId], if one was drawn today with the same [stamp]. */
    fun load(context: Context, widgetId: Int, month: YearMonth, today: LocalDate, stamp: Int): Page? {
        val key = key(widgetId, month)
        memory.get(key)?.let { page -> if (page.today == today && page.stamp == stamp) return page }
        val file = file(context, widgetId, month)
        if (!file.exists()) return null
        val page = runCatching { read(file) }.getOrNull() ?: return null
        if (page.month != month || page.today != today || page.stamp != stamp) return null
        memory.put(key, page)
        return page
    }

    /** Whether the page for [month] drawn today with [stamp] shows what [shows] says: its header is enough to tell. */
    fun has(context: Context, widgetId: Int, month: YearMonth, today: LocalDate, stamp: Int, shows: Int): Boolean {
        memory.get(key(widgetId, month))?.let { page ->
            if (page.today == today && page.stamp == stamp && page.shows == shows) return true
        }
        val file = file(context, widgetId, month)
        if (!file.exists()) return false
        return runCatching {
            DataInputStream(file.inputStream().buffered(64)).use { input ->
                input.readInt() == MAGIC && input.readInt() == FORMAT &&
                    YearMonth.of(input.readInt(), input.readInt()) == month &&
                    input.readLong() == today.toEpochDay() && input.readInt() == stamp && input.readInt() == shows
            }
        }.getOrDefault(false)
    }

    fun save(context: Context, widgetId: Int, page: Page) {
        memory.put(key(widgetId, page.month), page)
        val file = file(context, widgetId, page.month)
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            DataOutputStream(temp.outputStream().buffered(1 shl 16)).use { out -> write(out, page) }
            if (!temp.renameTo(file)) temp.delete()
        }
    }

    /** Drops every page of [widgetIds] but those of the months in [keep]. */
    fun forget(context: Context, widgetIds: IntArray, keep: Collection<YearMonth> = emptyList()) {
        for (id in widgetIds) {
            val kept = keep.mapTo(HashSet()) { key(id, it) }
            memory.snapshot().keys.filter { it.startsWith("$id/") && it !in kept }.forEach { memory.remove(it) }
            val names = keep.mapTo(HashSet()) { name(id, it) }
            directory(context).listFiles { f -> f.name.startsWith("w$id-") }?.forEach { f -> if (f.name !in names) f.delete() }
        }
    }

    /** Forgets what is held in memory (the disk copies stay), as a process started afresh would. */
    fun clearMemory() = memory.evictAll()

    private fun key(widgetId: Int, month: YearMonth) = "$widgetId/$month"

    private fun directory(context: Context) = File(context.cacheDir, "calendar-pages").apply { mkdirs() }

    private fun name(widgetId: Int, month: YearMonth) = "w$widgetId-$month.page"

    private fun file(context: Context, widgetId: Int, month: YearMonth) = File(directory(context), name(widgetId, month))

    private fun write(out: DataOutputStream, page: Page) {
        out.writeInt(MAGIC)
        out.writeInt(FORMAT)
        out.writeInt(page.month.year)
        out.writeInt(page.month.monthValue)
        out.writeLong(page.today.toEpochDay())
        out.writeInt(page.stamp)
        out.writeInt(page.shows)
        out.writeInt(page.live?.ordinal ?: -1)
        out.writeFloat(page.radius)
        out.writeInt(page.sizes.size)
        for (s in page.sizes) {
            out.writeFloat(s.size.width)
            out.writeFloat(s.size.height)
            out.writeInt(s.bitmap.width)
            out.writeInt(s.bitmap.height)
            for (rect in listOf(s.targets.previous, s.targets.next, s.targets.title, s.targets.add)) writeRect(out, rect)
            out.writeInt(s.targets.days.size)
            for ((date, rect) in s.targets.days) {
                out.writeLong(date.toEpochDay())
                writeRect(out, rect)
            }
            // The pixels as they are in memory: nothing to encode now, nothing to decode on a tap.
            val pixels = ByteBuffer.allocate(s.bitmap.byteCount)
            s.bitmap.copyPixelsToBuffer(pixels)
            out.writeInt(pixels.capacity())
            out.write(pixels.array())
        }
    }

    /** One read of the whole file, then the pixels copied straight from it into each picture. */
    private fun read(file: File): Page? {
        val bytes = file.readBytes()
        val input = ByteBuffer.wrap(bytes)
        if (input.getInt() != MAGIC || input.getInt() != FORMAT) return null
        val month = YearMonth.of(input.getInt(), input.getInt())
        val today = LocalDate.ofEpochDay(input.getLong())
        val stamp = input.getInt()
        val shows = input.getInt()
        val live = LiveWeather.entries.getOrNull(input.getInt())
        val radius = input.getFloat()
        val sizes = List(input.getInt()) {
            val size = SizeF(input.getFloat(), input.getFloat())
            val w = input.getInt()
            val h = input.getInt()
            val previous = readRect(input)
            val next = readRect(input)
            val title = readRect(input)
            val add = readRect(input)
            val days = List(input.getInt()) { LocalDate.ofEpochDay(input.getLong()) to (readRect(input) ?: RectF()) }
            val count = input.getInt()
            val bitmap = createBitmap(w, h)
            if (count != bitmap.byteCount) return null
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes, input.position(), count).slice())
            input.position(input.position() + count)
            Size(size, bitmap, CalendarTargets(previous, next, title, add, days))
        }
        return Page(month, today, stamp, shows, live, radius, sizes)
    }

    private fun writeRect(out: DataOutputStream, rect: RectF?) {
        out.writeBoolean(rect != null)
        if (rect != null) {
            out.writeFloat(rect.left)
            out.writeFloat(rect.top)
            out.writeFloat(rect.right)
            out.writeFloat(rect.bottom)
        }
    }

    private fun readRect(input: ByteBuffer): RectF? =
        if (input.get() != 0.toByte()) RectF(input.getFloat(), input.getFloat(), input.getFloat(), input.getFloat()) else null
}
