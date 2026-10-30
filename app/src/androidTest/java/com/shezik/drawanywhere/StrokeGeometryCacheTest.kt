package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.drawing.PenRenderer
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import org.junit.Assert.*
import org.junit.Test

class StrokeGeometryCacheTest {
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

    @Test fun erasureCopiesHaveFreshPathsAndUndoRestoresOriginalPath() {
        val controller = DrawController(PenConfig())
        controller.createStroke(Offset.Zero)
        controller.updateLatestStroke(Offset(100f, 0f))
        controller.finishStroke()
        val original = controller.strokeList.single()
        val path = PenRenderer.pathFor(original)
        controller.setPenConfig(PenConfig(penType = PenType.PixelEraser, width = 20f))
        controller.createStroke(Offset(50f, 0f)); controller.finishStroke()
        assertEquals(2, controller.strokeList.size)
        controller.strokeList.forEach { assertNotSame(path, PenRenderer.pathFor(it)) }
        controller.undo()
        assertSame(original, controller.strokeList.single())
        assertSame(path, PenRenderer.pathFor(controller.strokeList.single()))
        assertFalse(controller.canUndo.value)
        controller.redo()
        assertEquals(2, controller.strokeList.size)
        assertFalse(controller.canRedo.value)
    }
}
