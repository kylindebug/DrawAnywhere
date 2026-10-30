package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.PenType
import com.shezik.drawanywhere.model.Stroke

/** Ordered transparent paint, using exactly the same path as the freehand pen. */
class PixelEraserTool(private val ctx: ToolContext) : StrokeTool {
    private var stroke: Stroke? = null
    private var attached = false

    override fun onStart(point: Offset) {
        stroke = Stroke(mutableListOf(point), Color.Black, ctx.penConfig.width, 1f, PenType.PixelEraser)
        attached = false
        attachIfNeeded(point, point, point)
    }

    override fun onMove(point: Offset) {
        val current = stroke ?: return
        val last = current.points.last()
        if (last == point) return
        val previous = current.points.getOrElse(current.points.lastIndex - 1) { last }
        current._points.add(point)
        current.invalidateGeometry()
        if (!attached) attachIfNeeded(previous, last, point)
        else ctx.notifyChanged()
    }

    private fun attachIfNeeded(a: Offset, b: Offset, c: Offset) {
        val current = stroke ?: return
        // Include the changing quadratic tail. Bounds are only an empty-space
        // rejection; actual coverage is determined by the shared pen renderer.
        val left = minOf(a.x, b.x, c.x); val right = maxOf(a.x, b.x, c.x)
        val top = minOf(a.y, b.y, c.y); val bottom = maxOf(a.y, b.y, c.y)
        val hit = ctx.strokes.any { ink ->
            if (ink.penType.isEraser) false else {
                val bounds = ink.geometryBounds
                val inkWidth = ink.width * if (ink.penType == PenType.Laser) 2.5f else 1f
                val radius = (current.width + inkWidth) / 2f
                bounds.right >= left - radius && bounds.left <= right + radius &&
                    bounds.bottom >= top - radius && bounds.top <= bottom + radius
            }
        }
        if (hit) {
            ctx.strokes.add(current)
            attached = true
            ctx.notifyChanged()
        }
    }

    override fun onFinish() {
        val current = stroke ?: return
        if (attached) ctx.pushUndo(DrawAction.AddStroke(current))
        stroke = null
        attached = false
    }
}
