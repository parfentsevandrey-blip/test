package app.quire.engine.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A 4x4 transform: the one thing that moves geometry from model space to the screen.
 *
 * Storage is column-major in a flat 16-float array, matching OpenGL, so element (row, col)
 * lives at `m[col * 4 + row]` and a translation sits in the last four slots. That layout is
 * kept even though nothing here talks to GL, because every reference formula for perspective
 * and look-at is written for it and transcribing them into row-major is where sign errors
 * come from.
 *
 * [translate], [rotateX], [rotateY], [rotateZ] and [scale] post-multiply — `this = this * R`
 * — so a chain reads outermost-first and the last call in the chain is applied to the point
 * first, exactly as in a fixed-function GL matrix stack.
 *
 * Every method mutates the receiver and returns it; nothing here allocates.
 */
class Mat4 {

    /** The sixteen floats, column-major; exposed so renderers can hand it to native code. */
    val m: FloatArray = FloatArray(16)

    // Multiplication cannot write into m while still reading it, so each instance carries
    // its own destination buffer rather than allocating one per call.
    private val temp: FloatArray = FloatArray(16)

    init {
        identity()
    }

    /** Resets to the identity transform; the start of every matrix chain. */
    fun identity(): Mat4 {
        val a = m
        for (i in 0 until 16) a[i] = 0f
        a[0] = 1f
        a[5] = 1f
        a[10] = 1f
        a[15] = 1f
        return this
    }

    /** Copies [other] into this matrix; for reusing a preallocated matrix. */
    fun set(other: Mat4): Mat4 {
        System.arraycopy(other.m, 0, m, 0, 16)
        return this
    }

    /**
     * Post-multiplies by [rhs] so that `this = this * rhs`; for composing transforms.
     *
     * [rhs] may be this matrix.
     */
    fun multiply(rhs: Mat4): Mat4 {
        val a = m
        val b = rhs.m
        val t = temp
        for (c in 0 until 4) {
            val c4 = c * 4
            val b0 = b[c4]
            val b1 = b[c4 + 1]
            val b2 = b[c4 + 2]
            val b3 = b[c4 + 3]
            t[c4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
            t[c4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
            t[c4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
            t[c4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
        }
        System.arraycopy(t, 0, a, 0, 16)
        return this
    }

    /** Post-multiplies by a translation; for placing an object in the world. */
    fun translate(x: Float, y: Float, z: Float): Mat4 {
        val a = m
        // Only the fourth column changes: it becomes this matrix applied to (x, y, z, 1).
        a[12] += a[0] * x + a[4] * y + a[8] * z
        a[13] += a[1] * x + a[5] * y + a[9] * z
        a[14] += a[2] * x + a[6] * y + a[10] * z
        a[15] += a[3] * x + a[7] * y + a[11] * z
        return this
    }

    /** Post-multiplies by a rotation about X; for pitching an object forward or back. */
    fun rotateX(radians: Float): Mat4 {
        val a = m
        val c = cos(radians)
        val s = sin(radians)
        for (i in 0 until 4) {
            val col1 = a[4 + i]
            val col2 = a[8 + i]
            a[4 + i] = col1 * c + col2 * s
            a[8 + i] = col2 * c - col1 * s
        }
        return this
    }

    /** Post-multiplies by a rotation about Y; for yawing an object left or right. */
    fun rotateY(radians: Float): Mat4 {
        val a = m
        val c = cos(radians)
        val s = sin(radians)
        for (i in 0 until 4) {
            val col0 = a[i]
            val col2 = a[8 + i]
            a[i] = col0 * c - col2 * s
            a[8 + i] = col0 * s + col2 * c
        }
        return this
    }

    /** Post-multiplies by a rotation about Z; for rolling an object in its own plane. */
    fun rotateZ(radians: Float): Mat4 {
        val a = m
        val c = cos(radians)
        val s = sin(radians)
        for (i in 0 until 4) {
            val col0 = a[i]
            val col1 = a[4 + i]
            a[i] = col0 * c + col1 * s
            a[4 + i] = col1 * c - col0 * s
        }
        return this
    }

    /** Post-multiplies by a scale; for sizing an object without touching its geometry. */
    fun scale(x: Float, y: Float, z: Float): Mat4 {
        val a = m
        for (i in 0 until 4) {
            a[i] *= x
            a[4 + i] *= y
            a[8 + i] *= z
        }
        return this
    }

    /**
     * Replaces this matrix with a perspective projection; the lens of the camera.
     *
     * Produces the GL convention where clip `w` comes out as the distance in front of the
     * camera, which is what the depth sort and the behind-the-camera test both read.
     */
    fun perspective(fovYRadians: Float, aspect: Float, near: Float, far: Float): Mat4 {
        val safeAspect = if (aspect > EPSILON) aspect else 1f
        val safeFov = fovYRadians.coerceIn(MIN_FOV, MAX_FOV)
        val safeNear = if (near > EPSILON) near else EPSILON
        val safeFar = if (far > safeNear + EPSILON) far else safeNear + 1f
        val f = 1f / tan(safeFov * 0.5f)
        val rangeInv = 1f / (safeNear - safeFar)
        val a = m
        for (i in 0 until 16) a[i] = 0f
        a[0] = f / safeAspect
        a[5] = f
        a[10] = (safeFar + safeNear) * rangeInv
        a[11] = -1f
        a[14] = 2f * safeFar * safeNear * rangeInv
        return this
    }

    /**
     * Replaces this matrix with a view transform looking from [eye] at [target]; where the
     * camera stands.
     *
     * Computed in scalar floats rather than through [Vec3] so that it stays allocation-free
     * without the matrix having to own scratch vectors.
     */
    fun lookAt(eye: Vec3, target: Vec3, up: Vec3): Mat4 {
        var fx = target.x - eye.x
        var fy = target.y - eye.y
        var fz = target.z - eye.z
        var flen = sqrt(fx * fx + fy * fy + fz * fz)
        if (flen < EPSILON) {
            // Eye sitting on the target has no forward direction; look down -Z.
            fx = 0f
            fy = 0f
            fz = -1f
            flen = 1f
        }
        val finv = 1f / flen
        fx *= finv
        fy *= finv
        fz *= finv

        var sx = fy * up.z - fz * up.y
        var sy = fz * up.x - fx * up.z
        var sz = fx * up.y - fy * up.x
        var slen = sqrt(sx * sx + sy * sy + sz * sz)
        if (slen < EPSILON) {
            // Up is parallel to the view direction, so it picks out no side axis. Retry
            // against world Z, and against world X if the view runs along Z as well.
            sx = fy
            sy = -fx
            sz = 0f
            slen = sqrt(sx * sx + sy * sy)
            if (slen < EPSILON) {
                sx = 1f
                sy = 0f
                sz = 0f
                slen = 1f
            }
        }
        val sinv = 1f / slen
        sx *= sinv
        sy *= sinv
        sz *= sinv

        // s and f are unit and perpendicular, so their cross is already unit.
        val ux = sy * fz - sz * fy
        val uy = sz * fx - sx * fz
        val uz = sx * fy - sy * fx

        val a = m
        a[0] = sx
        a[4] = sy
        a[8] = sz
        a[12] = -(sx * eye.x + sy * eye.y + sz * eye.z)
        a[1] = ux
        a[5] = uy
        a[9] = uz
        a[13] = -(ux * eye.x + uy * eye.y + uz * eye.z)
        a[2] = -fx
        a[6] = -fy
        a[10] = -fz
        a[14] = fx * eye.x + fy * eye.y + fz * eye.z
        a[3] = 0f
        a[7] = 0f
        a[11] = 0f
        a[15] = 1f
        return this
    }

    /** Transforms a point, writing x,y,z,w into out. Returns w. */
    fun transform(x: Float, y: Float, z: Float, out: FloatArray): Float {
        val a = m
        val ox = a[0] * x + a[4] * y + a[8] * z + a[12]
        val oy = a[1] * x + a[5] * y + a[9] * z + a[13]
        val oz = a[2] * x + a[6] * y + a[10] * z + a[14]
        val ow = a[3] * x + a[7] * y + a[11] * z + a[15]
        out[0] = ox
        out[1] = oy
        out[2] = oz
        out[3] = ow
        return ow
    }

    /** Holds the factory for preallocated matrices. */
    companion object {

        // A field of view of zero or a full turn has no usable projection; clamp rather
        // than divide by a tangent of zero or infinity.
        private const val MIN_FOV = 0.01f
        private const val MAX_FOV = 3.1f

        /**
         * A fresh identity matrix for use as a preallocated working field.
         *
         * Call it once when building an object, never inside a frame: it allocates, and the
         * point of holding one is that the frame path then does not.
         */
        fun scratch(): Mat4 = Mat4()
    }
}
