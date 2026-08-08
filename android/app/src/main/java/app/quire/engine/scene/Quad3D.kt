package app.quire.engine.scene

import android.graphics.Matrix
import android.graphics.RectF
import app.quire.engine.math.Mat4
import app.quire.engine.math.Vec3

/**
 * A flat rectangle placed in 3D. Its job is to hand back an android.graphics.Matrix that maps
 * a 2D drawing of size (contentWidth x contentHeight) onto its projected corners, so any
 * ordinary Canvas drawing can be pasted onto a plane in space.
 *
 * The quad is a rectangle in its own XY plane, centred on [position], facing +Z before any
 * rotation. Corners are held in content order — top-left, top-right, bottom-right,
 * bottom-left — which is the order `setPolyToPoly` needs and the order a bitmap's corners
 * come in, so no reshuffling happens between projection and drawing.
 */
class Quad3D {

    /** Width in world units; the horizontal size of the plane, not of the drawing on it. */
    var width: Float = 1f

    /** Height in world units; the vertical size of the plane, not of the drawing on it. */
    var height: Float = 1f

    /** Centre of the quad in world space; mutate in place, then re-[project]. */
    val position: Vec3 = Vec3()

    /** Rotation about world Y in radians; swings the quad left and right. */
    var yaw: Float = 0f

    /** Rotation about the yawed X axis in radians; tips the quad towards or away. */
    var pitch: Float = 0f

    /** Rotation in the quad's own plane in radians; spins the drawing on the card. */
    var roll: Float = 0f

    private var depthValue = 0f
    private var visibleFlag = false

    /** Average projected depth, for painter's-algorithm sorting. Larger = further away. */
    val depth: Float get() = depthValue

    /** True when every corner is in front of the camera and the quad faces the viewer. */
    val visible: Boolean get() = visibleFlag

    private val model = Mat4.scratch()
    private val worldPoint = FloatArray(4)
    private val projected = FloatArray(3)

    // Screen-space corners, x,y interleaved, in content order.
    private val corners = FloatArray(8)
    private val src = FloatArray(8)

    // Hit testing needs the content-to-screen mapping inverted. Building it costs a linear
    // solve, so it is deferred until something actually asks and then kept until the next
    // projection moves the corners.
    private val unitToQuad = Matrix()
    private val quadToUnit = Matrix()
    private val hitPoint = FloatArray(2)
    private var hitDirty = true
    private var hitValid = false

    /** Projects the four corners with [camera]; must be called before matrixFor/depth. */
    fun project(camera: Camera3D) {
        model.identity()
            .translate(position.x, position.y, position.z)
            .rotateY(yaw)
            .rotateX(pitch)
            .rotateZ(roll)
        val halfWidth = width * 0.5f
        val halfHeight = height * 0.5f
        var allInFront = true
        var depthSum = 0f
        for (i in 0 until 4) {
            // The model matrix is a translation and rotations only, so w is exactly 1 and
            // the transformed components are already the world position.
            model.transform(
                LOCAL_X[i] * halfWidth,
                LOCAL_Y[i] * halfHeight,
                0f,
                worldPoint,
            )
            val inFront = camera.project(worldPoint[0], worldPoint[1], worldPoint[2], projected)
            corners[i * 2] = projected[0]
            corners[i * 2 + 1] = projected[1]
            depthSum += projected[2]
            if (!inFront) allInFront = false
        }
        depthValue = depthSum * 0.25f
        // Screen Y grows downwards, so a front-facing quad walks its corners clockwise on
        // screen and the shoelace sum comes out positive. Turned past edge-on it goes
        // negative, which is the backface test; near zero the quad is a line and the
        // four-point mapping would be singular.
        visibleFlag = allInFront && signedArea() > MIN_AREA
        hitDirty = true
    }

    /** Fills [out] with the mapping from a contentWidth x contentHeight bitmap to the quad. */
    fun matrixFor(contentWidth: Float, contentHeight: Float, out: Matrix) {
        src[0] = 0f
        src[1] = 0f
        src[2] = contentWidth
        src[3] = 0f
        src[4] = contentWidth
        src[5] = contentHeight
        src[6] = 0f
        src[7] = contentHeight
        if (!out.setPolyToPoly(src, 0, corners, 0, 4)) out.reset()
    }

    /** Screen-space bounding box of the projected quad. */
    fun bounds(out: RectF) {
        var minX = corners[0]
        var maxX = corners[0]
        var minY = corners[1]
        var maxY = corners[1]
        for (i in 1 until 4) {
            val x = corners[i * 2]
            val y = corners[i * 2 + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        out.set(minX, minY, maxX, maxY)
    }

    /** Hit test in screen space; returns normalised u,v inside the quad or null. */
    fun hit(screenX: Float, screenY: Float, outUv: FloatArray): Boolean {
        if (!visibleFlag) return false
        if (hitDirty) {
            hitDirty = false
            // Mapping the unit square rather than the content rect means the inverse hands
            // back u,v directly, with no dependence on whatever size is drawn on the quad.
            hitValid = unitToQuad.setPolyToPoly(UNIT_SRC, 0, corners, 0, 4) &&
                unitToQuad.invert(quadToUnit)
        }
        if (!hitValid) return false
        hitPoint[0] = screenX
        hitPoint[1] = screenY
        quadToUnit.mapPoints(hitPoint)
        val u = hitPoint[0]
        val v = hitPoint[1]
        if (u < 0f || u > 1f || v < 0f || v > 1f) return false
        outUv[0] = u
        outUv[1] = v
        return true
    }

    private fun signedArea(): Float {
        var sum = 0f
        for (i in 0 until 4) {
            val j = (i + 1) and 3
            sum += corners[i * 2] * corners[j * 2 + 1] - corners[j * 2] * corners[i * 2 + 1]
        }
        return sum * 0.5f
    }

    private companion object {

        // Content order: top-left, top-right, bottom-right, bottom-left. World Y is up while
        // content Y runs down, so the top corners take the positive Y.
        val LOCAL_X = floatArrayOf(-1f, 1f, 1f, -1f)
        val LOCAL_Y = floatArrayOf(1f, 1f, -1f, -1f)
        val UNIT_SRC = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

        // One square pixel of projected area: below this the mapping is not worth solving.
        const val MIN_AREA = 1f
    }
}
