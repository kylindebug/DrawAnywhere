package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.DrawController
import com.shezik.drawanywhere.model.*
import org.junit.Assert.*
import org.junit.Test

class PixelEraserToolTest {
    private fun ink() = Stroke(mutableListOf(Offset.Zero, Offset(100f, 100f)), Color.Red, 40f, 0.6f)
    private fun tool(strokes: MutableList<Stroke>, history: MutableList<DrawAction> = mutableListOf()) =
        PixelEraserTool(ToolContext(strokes, PenConfig(penType = PenType.PixelEraser, width = 8f), history::add, {}))

    @Test fun eraserKeepsOriginalInkAndRecordsItsOwnWidth() {
        val original = ink()
        val strokes = mutableListOf(original)
        val history = mutableListOf<DrawAction>()
        tool(strokes, history).apply { onStart(Offset(30f, 30f)); onMove(Offset(70f, 70f)); onFinish() }
        assertSame(original, strokes.first())
        assertEquals(2, strokes.size)
        assertEquals(PenType.PixelEraser, strokes.last().penType)
        assertEquals(8f, strokes.last().width)
        assertEquals(2, original.points.size)
        assertSame(strokes.last(), (history.single() as DrawAction.AddStroke).stroke)
    }

    @Test fun sparseSweepKeepsEntireTrajectoryWhenItEntersInk() {
        val strokes = mutableListOf(ink())
        val points = listOf(Offset(-200f, 50f), Offset(-100f, 50f), Offset(200f, 50f))
        tool(strokes).apply { onStart(points[0]); onMove(points[1]); onMove(points[2]); onFinish() }
        assertEquals(points, strokes.last().points)
    }

    @Test fun emptySpaceDoesNotReplaceUndo() {
        val strokes = mutableListOf(ink())
        val history = mutableListOf<DrawAction>()
        tool(strokes, history).apply { onStart(Offset(200f, 200f)); onMove(Offset(300f, 300f)); onFinish() }
        assertEquals(1, strokes.size)
        assertTrue(history.isEmpty())
        val empty = mutableListOf<Stroke>()
        tool(empty).apply { onStart(Offset.Zero); onFinish() }
        assertTrue(empty.isEmpty())
    }

    @Test fun repeatedErasuresNeverFragmentOrCopyInk() {
        val original = ink()
        val strokes = mutableListOf(original)
        repeat(100) { tool(strokes).apply { onStart(Offset.Zero); onMove(Offset(100f, 100f)); onFinish() } }
        assertSame(original, strokes.first())
        assertEquals(1, strokes.count { !it.penType.isEraser })
        assertEquals(2, original.points.size)
        assertEquals(202, strokes.sumOf { it.points.size })
    }

    @Test fun aStationaryEraserRemainsOneDot() {
        val strokes = mutableListOf(ink())
        tool(strokes).apply { onStart(Offset.Zero); repeat(10) { onMove(Offset.Zero) }; onFinish() }
        assertEquals(listOf(Offset.Zero), strokes.last().points)
    }

    @Test fun shapeAndLaserGeometryArePreserved() {
        val rectangle = ink().copy(penType = PenType.Rectangle)
        val laser = ink().copy(penType = PenType.Laser)
        val strokes = mutableListOf(rectangle, laser)
        tool(strokes).apply { onStart(Offset.Zero); onMove(Offset(100f, 100f)); onFinish() }
        assertSame(rectangle, strokes[0])
        assertSame(laser, strokes[1])
        assertEquals(PenType.PixelEraser, strokes[2].penType)
    }

    @Test fun undoRedoRestoresTheOperationOrderAndOnlyOneStep() {
        val c = DrawController(PenConfig(width = 40f))
        c.createStroke(Offset.Zero); c.updateLatestStroke(Offset(100f, 100f)); c.finishStroke()
        val before = c.strokeList.toList()
        c.setPenConfig(PenConfig(penType = PenType.PixelEraser, width = 8f))
        c.createStroke(Offset(50f, 0f)); c.updateLatestStroke(Offset(50f, 100f)); c.finishStroke()
        val after = c.strokeList.toList()
        c.undo(); assertEquals(before, c.strokeList); assertFalse(c.canUndo.value)
        c.redo(); assertEquals(after, c.strokeList); assertFalse(c.canRedo.value)
    }

    @Test fun strokeEraserSkipsMasksAndUndoRestoresInkBeforeItsMask() {
        val c = DrawController(PenConfig(width = 40f))
        c.createStroke(Offset.Zero); c.updateLatestStroke(Offset(100f, 100f)); c.finishStroke()
        c.setPenConfig(PenConfig(penType = PenType.PixelEraser, width = 8f))
        c.createStroke(Offset(50f, 50f)); c.finishStroke()
        val before = c.strokeList.toList()
        c.setPenConfig(PenConfig(penType = PenType.StrokeEraser, width = 10f))
        c.createStroke(Offset(50f, 50f)); c.finishStroke()
        assertEquals(PenType.PixelEraser, c.strokeList.single().penType)
        assertFalse(c.canClearStrokes.value)
        c.undo(); assertEquals(before, c.strokeList)
        c.redo(); assertEquals(PenType.PixelEraser, c.strokeList.single().penType)
    }
}
