package app.quire.engine.scene

import app.quire.engine.math.Mat4
import app.quire.engine.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The single eye the scene is drawn through: it owns the view-projection and converts world
 * points into the pixel coordinates a Canvas draws in.
 *
 * Screen space is Android's, with Y growing downwards, so the projection's Y is flipped on
 * the way out. Everything is preallocated; [update] and [project] run for every corner of
 * every quad on every frame.
 */
class Camera3D {

    /** Where the eye stands in world space; mutate in place, then call [update]. */
    val position: Vec3 = Vec3(0f, 0f, DEFAULT_DISTANCE)

    /** The world point the eye looks at; mutate in place, then call [update]. */
    val target: Vec3 = Vec3(0f, 0f, 0f)

    /** Vertical field of view in degrees; a long lens keeps the perspective gentle. */
    var fovDegrees: Float = 42f

    /** Nearest visible distance; anything closer is clipped away. */
    var near: Float = 0.1f

    /** Furthest visible distance; bounds the depth range. */
    var far: Float = 100f

    private val up = Vec3(0f, 1f, 0f)
    private val projection = Mat4.scratch()
    private val view = Mat4.scratch()
    private val viewProjection = Mat4.scratch()
    private val clip = FloatArray(4)
    private var viewportWidth = 1f
    private var viewportHeight = 1f

    /** Orbit around the target: yaw/pitch in radians, distance in world units. */
    fun orbit(yaw: Float, pitch: Float, distance: Float) {
        // A pitch of exactly a quarter turn puts the eye on the up axis, where look-at has
        // no side vector and the picture rolls unpredictably; stop just short.
        val safePitch = pitch.coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
        val safeDistance = if (distance > MIN_DISTANCE) distance else MIN_DISTANCE
        val horizontal = safeDistance * cos(safePitch)
        position.set(
            target.x + horizontal * sin(yaw),
            target.y + safeDistance * sin(safePitch),
            target.z + horizontal * cos(yaw),
        )
    }

    /** Recomputes the view-projection for a viewport. Call once per frame. */
    fun update(viewportWidth: Float, viewportHeight: Float) {
        this.viewportWidth = if (viewportWidth > 1f) viewportWidth else 1f
        this.viewportHeight = if (viewportHeight > 1f) viewportHeight else 1f
        val aspect = this.viewportWidth / this.viewportHeight
        projection.perspective(fovDegrees * DEG_TO_RAD, aspect, near, far)
        view.lookAt(position, target, up)
        viewProjection.set(projection).multiply(view)
    }

    /**
     * Projects a world point to screen pixels; returns false if behind the camera.
     *
     * [out] needs at least two slots, which receive x and y in pixels. A third slot, if
     * present, receives the clip w — the distance in front of the eye, which is what depth
     * sorting compares. Behind the camera the perspective divide is meaningless, so x and y
     * are filled with the viewport centre to keep them finite for callers that draw anyway.
     */
    fun project(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
        val w = viewProjection.transform(x, y, z, clip)
        if (out.size > 2) out[2] = w
        if (w <= NEAR_W) {
            out[0] = viewportWidth * 0.5f
            out[1] = viewportHeight * 0.5f
            return false
        }
        val inv = 1f / w
        out[0] = (clip[0] * inv * 0.5f + 0.5f) * viewportWidth
        out[1] = (0.5f - clip[1] * inv * 0.5f) * viewportHeight
        return true
    }

    private companion object {

        const val DEFAULT_DISTANCE = 6f
        const val DEG_TO_RAD = (PI / 180.0).toFloat()
        const val PITCH_LIMIT = (PI / 2.0 - 0.01).toFloat()
        const val MIN_DISTANCE = 0.01f

        // Below this the divide magnifies float error into coordinates in the millions.
        const val NEAR_W = 1e-4f
    }
}
