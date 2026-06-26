package com.monthcalendar.widget.wallpaper

import java.util.Random

/**
 * Classic "Doom fire" cellular-automaton on a low-resolution grid, rendered as
 * chunky pixels. The bottom row is the ember source (max heat); every step the
 * heat propagates upward with random decay and a horizontal drift driven by
 * [step]'s `wind` (wired to the accelerometer in the wallpaper engine).
 */
class FireSimulation(val width: Int, val height: Int) {

    /** Intensity per cell, 0 (cold/black) … [MAX] (white-hot). */
    val cells = IntArray(width * height)
    private val random = Random()

    init {
        seedSource()
    }

    private fun seedSource() {
        val base = (height - 1) * width
        for (x in 0 until width) cells[base + x] = MAX
    }

    fun step(wind: Int) {
        // Keep the embers at the bottom burning.
        val base = (height - 1) * width
        for (x in 0 until width) cells[base + x] = MAX

        for (x in 0 until width) {
            for (y in 1 until height) {
                val src = y * width + x
                val intensity = cells[src]
                if (intensity == 0) {
                    cells[src - width] = 0
                } else {
                    val rand = random.nextInt(4) // 0..3
                    var dstX = x - rand + 1 + wind
                    if (dstX < 0) dstX = 0
                    if (dstX >= width) dstX = width - 1
                    val dst = (y - 1) * width + dstX
                    cells[dst] = intensity - (rand and 1)
                }
            }
        }
    }

    companion object {
        const val MAX = 36

        /** 37-step black → red → orange → yellow → white fire palette (ARGB). */
        val PALETTE = intArrayOf(
            0xFF070707.toInt(), 0xFF1F0707.toInt(), 0xFF2F0F07.toInt(), 0xFF470F07.toInt(),
            0xFF571707.toInt(), 0xFF671F07.toInt(), 0xFF771F07.toInt(), 0xFF8F2707.toInt(),
            0xFF9F2F07.toInt(), 0xFFAF3F07.toInt(), 0xFFBF4707.toInt(), 0xFFC74707.toInt(),
            0xFFDF4F07.toInt(), 0xFFDF5707.toInt(), 0xFFDF5707.toInt(), 0xFFD75F07.toInt(),
            0xFFD7670F.toInt(), 0xFFCF6F0F.toInt(), 0xFFCF770F.toInt(), 0xFFCF7F0F.toInt(),
            0xFFCF8717.toInt(), 0xFFC78717.toInt(), 0xFFC78F17.toInt(), 0xFFC7971F.toInt(),
            0xFFBF9F1F.toInt(), 0xFFBF9F1F.toInt(), 0xFFBFA727.toInt(), 0xFFBFA727.toInt(),
            0xFFBFAF2F.toInt(), 0xFFB7AF2F.toInt(), 0xFFB7B72F.toInt(), 0xFFB7B737.toInt(),
            0xFFCFCF6F.toInt(), 0xFFDFDF9F.toInt(), 0xFFEFEFC7.toInt(), 0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )
    }
}
