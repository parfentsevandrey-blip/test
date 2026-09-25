package app.rosa.weather.widget.render.calendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import androidx.core.graphics.createBitmap
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The paintings and the panes built on them, kept. A painting takes a few hundred milliseconds,
 * so each is made once per month and size: in memory for the widgets on screen, on disk for after
 * the process has gone. Panes — a painting with its frosted sheet and glass edge — are kept in
 * memory too, so a widget whose month is already painted redraws in a few milliseconds: only its
 * numbers are new. The months on either side are painted ahead (the updater's prefetch), so
 * the arrows turn a month without a wait.
 */
internal object CalendarArt {
    /** Bump whenever the paintings change, so no old picture is taken from disk. */
    const val VERSION = 2

    private const val MAX_FILES = 24

    private val paintings = object : LruCache<String, Bitmap>(18 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val panes = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val locks = ConcurrentHashMap<String, Any>()
    private val scene = SeasonScene()
    private val writer = Executors.newSingleThreadExecutor { job ->
        Thread(job, "calendar-art").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    /**
     * [month]'s painting at exactly [widthPx] × [heightPx], [pxPerDp] pixels per dp; without the
     * falling things when [live] tiles animate them. Shared: never recycle it.
     */
    fun painting(context: Context?, month: Int, widthPx: Int, heightPx: Int, pxPerDp: Float, live: Boolean): Bitmap {
        val key = "m$month-${widthPx}x$heightPx-${if (live) "live" else "still"}-v$VERSION"
        paintings.get(key)?.let { return it }
        synchronized(locks.getOrPut(key) { Any() }) {
            paintings.get(key)?.let { return it }
            val file = context?.let { File(directory(it), "$key.png") }
            val stored = file?.takeIf { it.exists() }?.let { f ->
                runCatching {
                    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
                }.getOrNull()?.takeIf { it.width == widthPx && it.height == heightPx }
            }
            val bitmap = stored ?: scene.paint(MonthArt.of(month), widthPx / pxPerDp, heightPx / pxPerDp, pxPerDp, live).also { painted ->
                if (file != null) writer.execute { save(painted, file) }
            }
            paintings.put(key, bitmap)
            return bitmap
        }
    }

    /**
     * A pane of [widthPx] × [heightPx] identified by [key] (everything it depends on), drawn by
     * [draw] on a canvas already scaled to [pxPerDp] the first time it's asked for. Shared: never
     * recycle it.
     */
    fun pane(key: String, widthPx: Int, heightPx: Int, pxPerDp: Float, draw: (Canvas) -> Unit): Bitmap {
        val full = "$key|${widthPx}x$heightPx|v$VERSION"
        panes.get(full)?.let { return it }
        synchronized(locks.getOrPut(full) { Any() }) {
            panes.get(full)?.let { return it }
            val bitmap = createBitmap(widthPx, heightPx)
            val canvas = Canvas(bitmap)
            canvas.scale(pxPerDp, pxPerDp)
            draw(canvas)
            panes.put(full, bitmap)
            return bitmap
        }
    }

    /** Forgets everything held in memory (the disk copies stay). For tests and low memory. */
    fun clear() {
        paintings.evictAll()
        panes.evictAll()
    }

    private fun directory(context: Context): File = File(context.cacheDir, "calendar-art").apply { mkdirs() }

    private fun save(bitmap: Bitmap, file: File) {
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            temp.renameTo(file)
            // Keep the newest few: the months on screen and either side, at a couple of sizes.
            val files = file.parentFile?.listFiles { f -> f.name.endsWith(".png") }.orEmpty()
            if (files.size > MAX_FILES) {
                files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
            }
            // Pictures from older versions of the paintings are never used again.
            files.filter { !it.name.endsWith("-v$VERSION.png") }.forEach { it.delete() }
        }
    }
}
