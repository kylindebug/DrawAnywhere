package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

/** Exact swept disk: two end circles plus their connecting strip, not resampled circles. */
internal class EraserSweep(val from: Offset, val to: Offset) {
    val left = min(from.x, to.x)
    val top = min(from.y, to.y)
    val right = max(from.x, to.x)
    val bottom = max(from.y, to.y)
    private val vx = to.x.toDouble() - from.x
    private val vy = to.y.toDouble() - from.y
    private val length = hypot(vx, vy)
    private val ux = if (length > 0.0) vx / length else 1.0
    private val uy = if (length > 0.0) vy / length else 0.0

    // Scratch interval reused for every segment; clipping runs on the input thread.
    var enter = 0.0
        private set
    var exit = 0.0
        private set

    fun contains(point: Offset, radius: Float): Boolean {
        val px = point.x.toDouble() - from.x
        val py = point.y.toDouble() - from.y
        val along = px * ux + py * uy
        val across = py * ux - px * uy
        val beyond = along - along.coerceIn(0.0, length)
        return beyond * beyond + across * across <= radius.toDouble() * radius
    }

    fun intersects(a: Offset, b: Offset, radius: Float): Boolean {
        if (radius <= 0f || max(a.x, b.x) < left - radius || min(a.x, b.x) > right + radius ||
            max(a.y, b.y) < top - radius || min(a.y, b.y) > bottom + radius) return false
        val ax = a.x.toDouble() - from.x
        val ay = a.y.toDouble() - from.y
        val bx = b.x.toDouble() - from.x
        val by = b.y.toDouble() - from.y
        val x = ax * ux + ay * uy
        val y = ay * ux - ax * uy
        val dx = (bx - ax) * ux + (by - ay) * uy
        val dy = (by - ay) * ux - (bx - ax) * uy
        val squaredLength = dx * dx + dy * dy
        if (squaredLength < 1e-20) {
            enter = 0.0; exit = 1.0
            return contains(a, radius)
        }
        enter = Double.POSITIVE_INFINITY
        exit = Double.NEGATIVE_INFINITY
        val r = radius.toDouble()
        circle(x, y, dx, dy, squaredLength, r)
        if (length > 0.0) {
            circle(x - length, y, dx, dy, squaredLength, r)
            strip(x, y, dx, dy, r)
        }
        if (exit <= enter) return false
        // Float endpoints can round a cut a fraction of a pixel back inside.
        // Ignore sub-millipixel boundary shavings so repeated erasure is idempotent.
        return (enter == 0.0 && exit == 1.0) || (exit - enter) * sqrt(squaredLength) > 0.001
    }

    private fun include(start: Double, end: Double) {
        val lo = max(0.0, start)
        val hi = min(1.0, end)
        if (hi > lo) {
            enter = min(enter, lo)
            exit = max(exit, hi)
        }
    }

    private fun circle(x: Double, y: Double, dx: Double, dy: Double, aa: Double, r: Double) {
        val cross = x * dy - y * dx
        val remaining = r * r - cross * cross / aa
        if (remaining <= 0.0) return // A tangent removes no length.
        val middle = -(x * dx + y * dy) / aa
        val half = sqrt(remaining / aa)
        include(middle - half, middle + half)
    }

    private fun strip(x: Double, y: Double, dx: Double, dy: Double, r: Double) {
        var lo = 0.0
        var hi = 1.0
        if (abs(dx) < 1e-12) {
            if (x < 0.0 || x > length) return
        } else {
            val a = -x / dx; val b = (length - x) / dx
            lo = max(lo, min(a, b)); hi = min(hi, max(a, b))
        }
        if (abs(dy) < 1e-12) {
            if (abs(y) >= r) return
        } else {
            val a = (-r - y) / dy; val b = (r - y) / dy
            lo = max(lo, min(a, b)); hi = min(hi, max(a, b))
        }
        include(lo, hi)
    }
}

/** Null means untouched: do not allocate replacement paths for a miss. */
internal fun clipOutsideSweep(points: List<Offset>, sweep: EraserSweep, radius: Float): List<MutableList<Offset>>? {
    if (points.isEmpty()) return null
    if (points.size == 1) return if (sweep.contains(points[0], radius)) emptyList() else null
    var result: MutableList<MutableList<Offset>>? = null
    var current: MutableList<Offset>? = null
    fun append(a: Offset, b: Offset) {
        if (current == null || (current!!.last() - a).getDistanceSquared() > 0.000001f) {
            current = arrayListOf(a)
            result!!.add(current!!)
        }
        if ((current!!.last() - b).getDistanceSquared() > 0.000001f) current!!.add(b)
    }
    fun interpolate(a: Offset, b: Offset, t: Double) = Offset(
        (a.x.toDouble() + (b.x.toDouble() - a.x) * t).toFloat(),
        (a.y.toDouble() + (b.y.toDouble() - a.y) * t).toFloat())
    for (i in 0 until points.lastIndex) {
        val a = points[i]; val b = points[i + 1]
        if (!sweep.intersects(a, b, radius)) {
            if (result != null) append(a, b)
            continue
        }
        if (result == null) {
            result = arrayListOf()
            if (i > 0) {
                current = ArrayList<Offset>(i + 1).apply { for (j in 0..i) add(points[j]) }
                result.add(current!!)
            }
        }
        if (sweep.enter > 0.0) append(a, interpolate(a, b, sweep.enter))
        current = null
        if (sweep.exit < 1.0) append(interpolate(a, b, sweep.exit), b)
    }
    return result
}

internal fun clipOutsideCircle(points: List<Offset>, center: Offset, radius: Float): List<List<Offset>>? =
    clipOutsideSweep(points, EraserSweep(center, center), radius)
