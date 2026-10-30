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
        erase(EraserSweep(point, point))
    }

    override fun onMove(point: Offset) {
        val from = previous ?: point
        if (point != from) erase(EraserSweep(from, point))
        previous = point
    }

    override fun onFinish() {
        previous = null
        val saved = snapshot ?: return
        snapshot = null
        ctx.pushUndo(DrawAction.CanvasSnapshot(saved, ctx.strokes.toList()))
    }

    private fun erase(sweep: EraserSweep) {
        var replacement: MutableList<Stroke>? = null
        for (i in ctx.strokes.indices) {
            val stroke = ctx.strokes[i]
            val radius = (ctx.penConfig.width + stroke.width) / 2f
            val bounds = stroke.geometryBounds
            val miss = stroke.penType.isEraser || bounds.right < sweep.left - radius ||
                bounds.left > sweep.right + radius || bounds.bottom < sweep.top - radius ||
                bounds.top > sweep.bottom + radius
            val segments = if (miss) null else clipOutsideSweep(outline(stroke), sweep, radius)
            if (segments == null) {
                replacement?.add(stroke)
                continue
            }
            if (snapshot == null) snapshot = ctx.strokes.toList()
            if (replacement == null) {
                replacement = ArrayList(ctx.strokes.size + segments.size)
                for (j in 0 until i) replacement.add(ctx.strokes[j])
            }
            for (points in segments) {
                replacement.add(stroke.copy(
                    _points = points,
                    penType = if (stroke.penType == PenType.Laser) PenType.Laser else PenType.Pen,
                ))
            }
        }
        replacement?.let {
            ctx.strokes.clear()
            ctx.strokes.addAll(it)
            ctx.notifyChanged()
        }
    }

    private fun outline(stroke: Stroke): List<Offset> {
        val pts = stroke.points
        if (pts.size < 2) return pts
        if (stroke.penType != PenType.Rectangle && stroke.penType != PenType.Ellipse) return pts
        stroke.cachedEraserOutline?.let { return it }
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
        }.also { stroke.cachedEraserOutline = it }
    }
}
