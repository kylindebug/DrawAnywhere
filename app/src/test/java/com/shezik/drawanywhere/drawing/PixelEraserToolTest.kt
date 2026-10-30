package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.DrawController
import com.shezik.drawanywhere.model.*
import org.junit.Assert.*
import org.junit.Test

class PixelEraserToolTest {
    private fun stroke(first: Offset, second: Offset? = null, type: PenType = PenType.Pen) =
        Stroke(listOfNotNull(first, second).toMutableList(), Color.Red, 4f, 0.6f, type)

    private fun eraser(strokes: MutableList<Stroke>, undo: MutableList<DrawAction> = mutableListOf()) =
        PixelEraserTool(ToolContext(strokes,
            PenConfig(penType = PenType.PixelEraser, width = 20f),
            onUndoPush = { undo.add(it) }, onChanged = {}))

    @Test fun dotIsErasedAndSavedForUndo() {
        val original = stroke(Offset(10f, 10f))
        val strokes = mutableListOf(original)
        val undo = mutableListOf<DrawAction>()
        eraser(strokes, undo).apply { onStart(Offset(12f, 12f)); onFinish() }
        assertTrue(strokes.isEmpty())
        assertEquals(listOf(original), (undo.single() as DrawAction.CanvasSnapshot).before)
    }

    @Test fun sparseLineIsCutWithoutDeletingItsEnds() {
        val original = stroke(Offset.Zero, Offset(100f, 0f))
        val strokes = mutableListOf(original)
        eraser(strokes).apply { onStart(Offset(50f, 0f)); onFinish() }
        assertEquals(2, strokes.size)
        assertEquals(Offset.Zero, strokes.first().points.first())
        assertEquals(Offset(100f, 0f), strokes.last().points.last())
        assertTrue(strokes.first().points.last().x < 45f)
        assertTrue(strokes.last().points.first().x > 55f)
        assertTrue(strokes.all { it.color == original.color && it.alpha == original.alpha && it.width == original.width })
        assertEquals(2, original.points.size) // snapshot data remains immutable
    }

    @Test fun fastSweepCutsStrokeBetweenEraserSamples() {
        val strokes = mutableListOf(stroke(Offset.Zero, Offset(100f, 0f)))
        val undo = mutableListOf<DrawAction>()
        eraser(strokes, undo).apply {
            onStart(Offset(50f, -50f))
            onMove(Offset(50f, 50f))
            onFinish()
        }
        assertEquals(2, strokes.size)
        assertEquals(1, undo.size)
        assertEquals(Offset.Zero, strokes.first().points.first())
        assertEquals(Offset(100f, 0f), strokes.last().points.last())
    }

    @Test fun noHitDoesNotCreateUndoEntry() {
        val original = stroke(Offset.Zero, Offset(100f, 0f))
        val strokes = mutableListOf(original)
        val undo = mutableListOf<DrawAction>()
        eraser(strokes, undo).apply { onStart(Offset(200f, 200f)); onFinish() }
        assertSame(original, strokes.single())
        assertTrue(undo.isEmpty())
    }

    @Test fun shapeInteriorIsNotErased() {
        val original = stroke(Offset.Zero, Offset(100f, 100f), type = PenType.Rectangle)
        val strokes = mutableListOf(original)
        eraser(strokes).apply { onStart(Offset(50f, 50f)); onFinish() }
        assertSame(original, strokes.single())
    }

    @Test fun shapeEdgeIsLocallyCutAndConvertedToInk() {
        val strokes = mutableListOf(stroke(Offset.Zero, Offset(100f, 100f), type = PenType.Rectangle))
        eraser(strokes).apply { onStart(Offset(50f, 0f)); onFinish() }
        assertTrue(strokes.isNotEmpty())
        assertTrue(strokes.all { it.penType == PenType.Pen })
        assertTrue(strokes.flatMap { it.points }.contains(Offset(100f, 100f)))
    }

    @Test fun laserKeepsItsLifetimeAfterErasure() {
        val original = stroke(Offset.Zero, Offset(100f, 0f), type = PenType.Laser)
        val strokes = mutableListOf(original)
        eraser(strokes).apply { onStart(Offset(50f, 0f)); onFinish() }
        assertEquals(2, strokes.size)
        assertTrue(strokes.all { it.penType == PenType.Laser && it.createdAt == original.createdAt && it.modifiedAt == original.modifiedAt })
    }

    @Test fun undoRedoRestoresExactBeforeAndAfterGeometry() {
        val controller = DrawController(PenConfig())
        controller.createStroke(Offset.Zero)
        controller.updateLatestStroke(Offset(100f, 0f))
        controller.finishStroke()
        val before = controller.strokeList.toList()
        controller.setPenConfig(PenConfig(penType = PenType.PixelEraser, width = 20f))
        controller.createStroke(Offset(50f, -40f))
        controller.updateLatestStroke(Offset(50f, 40f))
        controller.finishStroke()
        val after = controller.strokeList.toList()
        assertEquals(2, after.size)
        controller.undo()
        assertEquals(before, controller.strokeList)
        controller.redo()
        assertEquals(after, controller.strokeList)
    }

    @Test fun emptyAndRepeatedPointsAreSafe() {
        assertNull(clipOutsideCircle(emptyList(), Offset.Zero, 10f))
        val strokes = mutableListOf(stroke(Offset.Zero, Offset.Zero))
        eraser(strokes).apply { onStart(Offset.Zero); onMove(Offset.Zero); onFinish() }
        assertTrue(strokes.isEmpty())
    }
}
