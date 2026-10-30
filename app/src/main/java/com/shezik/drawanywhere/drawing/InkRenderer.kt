package com.shezik.drawanywhere.drawing

import android.graphics.Canvas
import android.graphics.Paint
import com.shezik.drawanywhere.model.PenType
import com.shezik.drawanywhere.model.Stroke

/** Isolate CLEAR operations from the parent surface, then replay ink in order. */
object InkRenderer {
    fun render(canvas: Canvas, strokes: List<Stroke>, paint: Paint) {
        val layer = if (strokes.any { it.penType == PenType.PixelEraser })
            canvas.saveLayer(null, null) else canvas.save()
        try {
            for (stroke in strokes) stroke.render(canvas, paint)
        } finally {
            canvas.restoreToCount(layer)
        }
    }
}
