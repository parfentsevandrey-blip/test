package com.monthcalendar.widget.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.roundToInt

/**
 * Live wallpaper: a pixel "campfire" that burns behind the home screen, sways
 * with device tilt (accelerometer) and gains a 3D parallax depth by rendering
 * the fire as two layers that drift by different amounts as you move the phone.
 *
 * This is the only Android surface that supports continuous, sensor-driven
 * rendering on the home screen — app widgets (the calendar) cannot animate or
 * read sensors, so the "feels 3D / fire in the background" effect lives here.
 */
class PixelFireWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = FireEngine()

    inner class FireEngine : Engine(), SensorEventListener {

        private val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        private var visible = false
        private var thread: RenderThread? = null

        private var surfaceW = 0
        private var surfaceH = 0
        private var sim: FireSimulation? = null
        private var bitmap: Bitmap? = null
        private var colorBuffer = IntArray(0)

        // Smoothed accelerometer tilt (low-pass filtered in onSensorChanged).
        @Volatile private var tiltX = 0f
        @Volatile private var tiltY = 0f

        private val paint = Paint().apply {
            isFilterBitmap = false // keep pixels chunky when upscaling
            isDither = false
            isAntiAlias = false
        }
        private val src = Rect()
        private val backDst = RectF()
        private val frontDst = RectF()

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                accelerometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
                startThread()
            } else {
                sensorManager.unregisterListener(this)
                stopThread()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            surfaceW = width
            surfaceH = height
            // Low-res grid → chunky pixels once upscaled to the surface.
            val gw = 120
            val gh = (gw * height.toFloat() / width.toFloat()).roundToInt().coerceIn(80, 240)
            sim = FireSimulation(gw, gh)
            bitmap = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
            colorBuffer = IntArray(gw * gh)
            src.set(0, 0, gw, gh)
            startThread()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopThread()
            sensorManager.unregisterListener(this)
        }

        override fun onSensorChanged(e: SensorEvent) {
            tiltX += (e.values[0] - tiltX) * 0.12f
            tiltY += (e.values[1] - tiltY) * 0.12f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        private fun startThread() {
            if (!visible || surfaceW == 0 || sim == null) return
            if (thread?.isAlive == true) return
            thread = RenderThread().also { it.start() }
        }

        private fun stopThread() {
            thread?.let {
                it.running = false
                try {
                    it.join(500)
                } catch (_: InterruptedException) {
                }
            }
            thread = null
        }

        private inner class RenderThread : Thread() {
            @Volatile var running = true

            override fun run() {
                val holder = surfaceHolder
                while (running) {
                    val frameStart = System.nanoTime()
                    val s = sim ?: break
                    val wind = (tiltX * 0.6f).roundToInt().coerceIn(-3, 3)
                    s.step(wind)
                    renderFrame(holder, s)
                    val elapsedMs = (System.nanoTime() - frameStart) / 1_000_000L
                    val sleepMs = 33L - elapsedMs
                    if (sleepMs > 0) {
                        try {
                            sleep(sleepMs)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            }
        }

        private fun renderFrame(holder: SurfaceHolder, s: FireSimulation) {
            val bmp = bitmap ?: return
            val palette = FireSimulation.PALETTE
            val cells = s.cells
            for (i in cells.indices) colorBuffer[i] = palette[cells[i]]
            bmp.setPixels(colorBuffer, 0, s.width, 0, 0, s.width, s.height)

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                canvas.drawColor(Color.BLACK)

                val px = tiltX
                val py = tiltY

                // Back layer: dim, slightly zoomed, drifts opposite & less → depth.
                val backDx = -px * 6f
                val backDy = py * 4f
                val overX = surfaceW * 0.06f
                val overY = surfaceH * 0.06f
                backDst.set(
                    backDx - overX,
                    backDy - overY,
                    surfaceW + backDx + overX,
                    surfaceH + backDy + overY,
                )
                paint.alpha = 120
                canvas.drawBitmap(bmp, src, backDst, paint)

                // Front layer: full, drifts more in the tilt direction.
                val frontDx = px * 14f
                val frontDy = -py * 8f
                frontDst.set(frontDx, frontDy, surfaceW + frontDx, surfaceH + frontDy)
                paint.alpha = 255
                canvas.drawBitmap(bmp, src, frontDst, paint)
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
