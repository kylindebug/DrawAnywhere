package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class CapsuleClipperTest {
    private fun clip(a: Offset, b: Offset, from: Offset, to: Offset, r: Float) =
        clipOutsideSweep(listOf(a, b), EraserSweep(from, to), r)

    @Test fun sparseLineIsCutByTheEntireSweepExactly() {
        val result = clip(Offset.Zero, Offset(100f, 0f), Offset(50f, -50f), Offset(50f, 50f), 12f)!!
        assertEquals(2, result.size)
        assertEquals(38f, result[0].last().x, 0.0001f)
        assertEquals(62f, result[1].first().x, 0.0001f)
    }

    @Test fun parallelSweepIncludesBothRoundEndCaps() {
        val result = clip(Offset(-20f, 0f), Offset(120f, 0f), Offset(20f, 0f), Offset(80f, 0f), 10f)!!
        assertEquals(10f, result[0].last().x, 0.0001f)
        assertEquals(90f, result[1].first().x, 0.0001f)
        val cap = clip(Offset(-6f, -20f), Offset(-6f, 20f), Offset.Zero, Offset(100f, 0f), 10f)!!
        assertEquals(-8f, cap[0].last().y, 0.0001f)
        assertEquals(8f, cap[1].first().y, 0.0001f)
    }

    @Test fun diagonalSweepHasCorrectWidthAndIsDirectionIndependent() {
        val a = Offset(0f, 50f); val b = Offset(100f, 50f)
        val from = Offset(20f, 20f); val to = Offset(80f, 80f)
        val result = clip(a, b, from, to, 10f)!!
        assertEquals(50f - sqrt(200f), result[0].last().x, 0.0001f)
        assertEquals(50f + sqrt(200f), result[1].first().x, 0.0001f)
        assertEquals(result, clip(a, b, to, from, 10f))
    }

    @Test fun tangentAndMissDoNotReplaceGeometry() {
        assertNull(clip(Offset(-20f, 10f), Offset(120f, 10f), Offset.Zero, Offset(100f, 0f), 10f))
        assertNull(clip(Offset(-20f, 11f), Offset(120f, 11f), Offset.Zero, Offset(100f, 0f), 10f))
    }

    @Test fun repeatedSweepsDoNotGrowFragmentsOrHistory() {
        val strokes = mutableListOf(Stroke(mutableListOf(Offset.Zero, Offset(10000.3f, 7333.7f)),
            Color.Red, 4f, 1f))
        val history = mutableListOf<DrawAction>()
        var notifications = 0
        val ctx = ToolContext(strokes, PenConfig(penType = PenType.PixelEraser, width = 18f),
            { history.add(it) }, { notifications++ })
        fun sweep() = PixelEraserTool(ctx).apply {
            onStart(Offset(6000.1f, -100f)); onMove(Offset(6000.1f, 10000f)); onFinish()
        }
        sweep()
        val after = strokes.toList()
        val events = notifications
        repeat(100) { sweep() }
        assertEquals(2, strokes.size)
        assertEquals(4, strokes.sumOf { it.points.size })
        assertSame(after[0], strokes[0])
        assertSame(after[1], strokes[1])
        assertEquals(1, history.size)
        assertEquals(events, notifications)
    }

    @Test fun oneLongMovePublishesOneChangeAndKeepsUnhitStrokes() {
        val original = List(100) { row -> Stroke(mutableListOf(Offset(0f, row * 10f), Offset(100f, row * 10f)),
            Color.Red, 4f, 1f) }
        val distant = Stroke(mutableListOf(Offset(1000f, 0f), Offset(1100f, 900f)), Color.Blue, 4f, 1f)
        val strokes = (original + distant).toMutableList()
        var changes = 0
        val ctx = ToolContext(strokes, PenConfig(penType = PenType.PixelEraser, width = 20f), {}, { changes++ })
        PixelEraserTool(ctx).apply { onStart(Offset(50f, -100f)); onMove(Offset(50f, 1100f)); onFinish() }
        assertEquals(1, changes)
        assertEquals(201, strokes.size)
        assertSame(distant, strokes.last())
    }

    @Test fun randomizedClipsAgreeWithIndependentPointDistanceChecks() {
        fun distance(p: Offset, a: Offset, b: Offset): Float {
            val d = b - a
            val length = d.getDistanceSquared()
            val t = if (length == 0f) 0f else (((p.x - a.x) * d.x + (p.y - a.y) * d.y) / length).coerceIn(0f, 1f)
            return (p - (a + d * t)).getDistance()
        }
        val random = Random(20261030)
        fun point() = Offset(random.nextFloat() * 200 - 100, random.nextFloat() * 200 - 100)
        repeat(200) { round ->
            val a = point(); val b = point(); val from = point()
            val to = if (round % 10 == 0) from else point()
            val radius = 1f + random.nextFloat() * 30
            val kept = clip(a, b, from, to, radius) ?: listOf(listOf(a, b))
            for (sample in 1 until 100) {
                val p = a + (b - a) * (sample / 100f)
                val delta = distance(p, from, to) - radius
                if (abs(delta) < 0.02f) continue
                val retained = kept.any { fragment -> fragment.zipWithNext().any { (u, v) -> distance(p, u, v) < 0.002f } }
                assertEquals("round=$round sample=$sample distance=$delta", delta > 0f, retained)
            }
        }
    }
}
