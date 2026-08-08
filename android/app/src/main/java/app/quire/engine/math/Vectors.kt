package app.quire.engine.math

import kotlin.math.sqrt

/**
 * The size below which a length is treated as zero.
 *
 * Single-precision cross products of nearly parallel unit vectors land around
 * 1e-7, so anything under this is noise rather than a direction.
 */
internal const val EPSILON: Float = 1e-6f

/**
 * A point or direction in world space, and the unit of every 3D coordinate in the engine.
 *
 * Every operation mutates the receiver and returns it so that a chain of them costs no
 * allocations: these run inside per-frame projection loops where a temporary object per
 * corner per quad would be the whole frame budget.
 *
 * @property x the world X component, positive to the right.
 * @property y the world Y component, positive upwards.
 * @property z the world Z component, positive towards the default camera.
 */
class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {

    /** Overwrites all three components; for reusing a preallocated vector. */
    fun set(x: Float, y: Float, z: Float): Vec3 {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    /** Copies [other] into this vector; for reusing a preallocated vector. */
    fun set(other: Vec3): Vec3 {
        x = other.x
        y = other.y
        z = other.z
        return this
    }

    /** Adds [other] into this vector; for accumulating offsets in place. */
    fun add(other: Vec3): Vec3 {
        x += other.x
        y += other.y
        z += other.z
        return this
    }

    /** Subtracts [other] from this vector; for building the direction between two points. */
    fun sub(other: Vec3): Vec3 {
        x -= other.x
        y -= other.y
        z -= other.z
        return this
    }

    /** Multiplies every component by [k]; for lengthening or flipping a direction. */
    fun scale(k: Float): Vec3 {
        x *= k
        y *= k
        z *= k
        return this
    }

    /** The euclidean length; for distances and for normalising. */
    fun length(): Float = sqrt(x * x + y * y + z * z)

    /** Rescales this vector to unit length; for turning an offset into a direction. */
    fun normalise(): Vec3 {
        val len = length()
        // A zero vector has no direction to preserve, so it is left alone rather
        // than turned into NaN by the divide.
        if (len < EPSILON) return this
        val inv = 1f / len
        x *= inv
        y *= inv
        z *= inv
        return this
    }

    /** The dot product with [other]; for angles, projections and facing tests. */
    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    /**
     * Writes the cross product of this and [other] into [out]; for building perpendicular
     * axes such as a camera's right and up vectors.
     *
     * [out] may alias this or [other]: the result is computed into locals first.
     */
    fun cross(other: Vec3, out: Vec3): Vec3 {
        val cx = y * other.z - z * other.y
        val cy = z * other.x - x * other.z
        val cz = x * other.y - y * other.x
        return out.set(cx, cy, cz)
    }
}
