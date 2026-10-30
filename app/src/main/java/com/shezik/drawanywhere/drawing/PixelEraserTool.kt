package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.Stroke
import com.shezik.drawanywhere.model.PenType
import kotlin.math.*

/** Local erasure with one canvas snapshot per eraser gesture. */
class PixelEraserTool(private val ctx: ToolContext) : StrokeTool {
    private var snapshot: List<Stroke>? = null
    private var previous: Offset? = null

    override fun onStart(point: Offset) {
        snapshot = null
        previous = point
        eraseAt(point)
    }

    override fun onMove(point: Offset) {
        val from = previous ?: point
        // Sweep between samples, including fast moves that cross a thin stroke.
        val steps = ceil((point - from).getDistance() / max(1f, ctx.penConfig.width / 4)).toInt().coerceAtLeast(1)
        for (step in 1..steps) eraseAt(from + (point - from) * (step.toFloat() / steps))
        previous = point
    }

    override fun onFinish() {
        previous = null
        val saved = snapshot ?: return
        snapshot = null
        ctx.pushUndo(DrawAction.CanvasSnapshot(saved, ctx.strokes.toList()))
    }

    private fun eraseAt(point: Offset) {
        var changed = false
        for (i in ctx.strokes.indices.reversed()) {
            val stroke = ctx.strokes[i]
            if (stroke.penType.isEraser) continue
            val radius = (ctx.penConfig.width + stroke.width) / 2f
            val segments = clipOutsideCircle(outline(stroke), point, radius) ?: continue
            if (snapshot == null) snapshot = ctx.strokes.toList()
            ctx.strokes.removeAt(i)
            for (points in segments.asReversed()) {
                ctx.strokes.add(i, stroke.copy(
                    _points = points.toMutableList(),
                    penType = if (stroke.penType == PenType.Laser) PenType.Laser else PenType.Pen,
                ))
            }
            changed = true
        }
        if (changed) ctx.notifyChanged()
    }

    private fun outline(stroke: Stroke): List<Offset> {
        val pts = stroke.points
        if (pts.size < 2) return pts
        val a = pts[0]; val b = pts[1]
        return when (stroke.penType) {
            PenType.Rectangle -> listOf(a, Offset(b.x, a.y), b, Offset(a.x, b.y), a)
            PenType.Ellipse -> {
                val center = (a + b) / 2f
                val rx = abs(b.x - a.x) / 2f; val ry = abs(b.y - a.y) / 2f
                val steps = ceil(2 * PI * max(rx, ry) / 2).toInt().coerceIn(32, 4096)
                (0..steps).map {
                    val angle = 2 * PI * it / steps
                    center + Offset(rx * cos(angle).toFloat(), ry * sin(angle).toFloat())
                }
            }
            else -> pts
        }
    }
}

/** Null means untouched. Intersections split sparse segments, not just sampled points. */
internal fun clipOutsideCircle(points: List<Offset>, center: Offset, radius: Float): List<List<Offset>>? {
    val r2 = radius * radius
    if (points.isEmpty()) return null
    if (points.size == 1) return if ((points[0] - center).getDistanceSquared() <= r2) emptyList() else null
    val result = mutableListOf<MutableList<Offset>>()
    var current: MutableList<Offset>? = null
    var changed = false
    fun append(a: Offset, b: Offset) {
        if (current == null || (current!!.last() - a).getDistanceSquared() > 0.0001f) {
            current = mutableListOf(a)
            result.add(current!!)
        }
        if ((current!!.last() - b).getDistanceSquared() > 0.0001f) current!!.add(b)
    }
    for ((a, b) in points.zipWithNext()) {
        val d = b - a; val f = a - center
        val aa = d.getDistanceSquared()
        if (aa < 0.000001f) {
            if (f.getDistanceSquared() <= r2) { changed = true; current = null }
            else append(a, b)
            continue
        }
        val bb = 2f * (f.x * d.x + f.y * d.y)
        val cc = f.getDistanceSquared() - r2
        val discriminant = bb * bb - 4f * aa * cc
        val cuts = mutableListOf(0f, 1f)
        if (discriminant > 0f) {
            val root = sqrt(discriminant)
            for (t in listOf((-bb - root) / (2f * aa), (-bb + root) / (2f * aa))) {
                if (t > 0f && t < 1f) cuts.add(t)
            }
        }
        cuts.sort()
        for ((start, end) in cuts.zipWithNext()) {
            val mid = a + d * ((start + end) / 2f)
            if ((mid - center).getDistanceSquared() < r2) {
                changed = true
                current = null
            } else append(a + d * start, a + d * end)
        }
    }
    return if (changed) result else null
}
