package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.drawing.PenRenderer
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import org.junit.Assert.*
import org.junit.Test

class StrokeGeometryCacheTest {
    @Test fun incrementalCurveMatchesOriginalAtEveryRenderedFrame() {
        val c = DrawController(PenConfig())
        c.createStroke(Offset(20f, 50f))
        val stroke = c.strokeList.single()
        repeat(128) { i ->
            c.updateLatestStroke(Offset(20f + i, 100f + kotlin.math.sin(i * .3f) * 70f))
            val incremental = PenRenderer.pathFor(stroke).approximate(.01f)
            val reference = PenRenderer.buildPath(stroke.points).approximate(.01f)
            assertArrayEquals(reference, incremental, 0f)
        }
        stroke._points[0] = Offset.Zero
        stroke.invalidateGeometry()
        assertArrayEquals(PenRenderer.buildPath(stroke.points).approximate(.01f),
            PenRenderer.pathFor(stroke).approximate(.01f), 0f)
    }
    @Test fun drawingInvalidatesPathAndBoundsButIdleRenderingReusesThem() {
        val controller = DrawController(PenConfig())
        controller.createStroke(Offset.Zero)
        controller.updateLatestStroke(Offset(10f, 10f))
        val stroke = controller.strokeList.single()
        val first = PenRenderer.pathFor(stroke)
        assertSame(first, PenRenderer.pathFor(stroke))
        assertEquals(10f, stroke.geometryBounds.right, 0f)
        controller.updateLatestStroke(Offset(100f, 100f))
        val second = PenRenderer.pathFor(stroke)
        assertNotSame(first, second)
        assertSame(second, PenRenderer.pathFor(stroke))
        assertEquals(100f, stroke.geometryBounds.right, 0f)
    }

    @Test fun erasureRetainsInkPathAndCachesItsIndependentMask() {
        val controller = DrawController(PenConfig())
        controller.createStroke(Offset.Zero)
        controller.updateLatestStroke(Offset(100f, 0f))
        controller.finishStroke()
        val original = controller.strokeList.single()
        val path = PenRenderer.pathFor(original)
        controller.setPenConfig(PenConfig(penType = PenType.PixelEraser, width = 20f))
        controller.createStroke(Offset(50f, 0f)); controller.finishStroke()
        assertEquals(2, controller.strokeList.size)
        assertSame(original, controller.strokeList.first())
        assertSame(path, PenRenderer.pathFor(original))
        val mask = controller.strokeList.last()
        assertEquals(PenType.PixelEraser, mask.penType)
        val maskPath = PenRenderer.pathFor(mask)
        assertNotSame(path, maskPath)
        assertSame(maskPath, PenRenderer.pathFor(mask))
        controller.undo()
        assertSame(original, controller.strokeList.single())
        assertSame(path, PenRenderer.pathFor(controller.strokeList.single()))
        assertFalse(controller.canUndo.value)
        controller.redo()
        assertEquals(2, controller.strokeList.size)
        assertFalse(controller.canRedo.value)
    }
}
