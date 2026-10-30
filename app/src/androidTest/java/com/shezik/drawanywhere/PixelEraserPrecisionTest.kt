package com.shezik.drawanywhere

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.drawing.InkRenderer
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import com.shezik.drawanywhere.model.Stroke
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

class PixelEraserPrecisionTest {
    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private fun draw(c: DrawController, points: List<Offset>, type: PenType = PenType.Pen,
                     width: Float = 24f, color: Color = Color.Black, alpha: Float = 1f) {
        c.setPenConfig(PenConfig(type, color, width, alpha))
        c.createStroke(points.first())
        points.drop(1).forEach(c::updateLatestStroke)
        c.finishStroke()
    }
    private fun solid(): DrawController = DrawController(PenConfig()).also { c ->
        for (y in 40..472 step 12) draw(c, listOf(Offset(24f, y.toFloat()), Offset(488f, y.toFloat())), width = 36f)
    }
    private fun bitmap(strokes: List<Stroke>, zoom: Float = 1f, background: Int = 0): Bitmap =
        Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.eraseColor(background)
            val canvas = Canvas(bmp)
            canvas.scale(zoom, zoom)
            InkRenderer.render(canvas, strokes, paint())
        }
    private fun pixels(bitmap: Bitmap) = IntArray(512 * 512).also { bitmap.getPixels(it, 0, 512, 0, 0, 512, 512) }
    private fun save(name: String, bitmap: Bitmap) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        File(cache, "precision-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun assertMaskMatchesPen(c: DrawController, path: List<Offset>, width: Float, zoom: Float = 1f) {
        val before = bitmap(c.strokeList, zoom)
        val reference = DrawController(PenConfig())
        draw(reference, path, width = width)
        val pen = bitmap(reference.strokeList, zoom)
        draw(c, path, PenType.PixelEraser, width)
        val after = bitmap(c.strokeList, zoom)
        val b = pixels(before); val p = pixels(pen); val a = pixels(after)
        var wrong = 0
        for (i in a.indices) {
            val expected = ((b[i] ushr 24) * (255 - (p[i] ushr 24)) + 127) / 255
            if (abs((a[i] ushr 24) - expected) > 2) wrong++
        }
        try { assertEquals("Eraser coverage differs from pen at width=$width zoom=$zoom", 0, wrong) }
        finally { before.recycle(); pen.recycle(); after.recycle() }
    }

    @Test fun solidInkHasExactlyPenCoverageForStraightCurvedAndFinePaths() {
        val paths = listOf(
            listOf(Offset(90f, 200f), Offset(420f, 200f)),
            listOf(Offset(210f, 70f), Offset(210f, 440f)),
            listOf(Offset(85f, 85f), Offset(420f, 420f)),
            listOf(Offset(80f, 250f), Offset(130f, 100f), Offset(230f, 90f), Offset(290f, 390f), Offset(420f, 260f)),
            listOf(Offset(100f, 100f), Offset(100f, 100f), Offset(250f, 400f), Offset(400f, 100f))
        )
        for (width in listOf(3f, 12f, 48f)) for (path in paths) assertMaskMatchesPen(solid(), path, width)
    }

    @Test fun constantWidthDoesNotDependOnUnderlyingInkDirectionOrWidth() {
        for (vertical in listOf(false, true)) for (inkWidth in listOf(8f, 36f, 100f)) {
            val c = DrawController(PenConfig())
            for (i in 0..512 step 4) {
                val p = if (vertical) listOf(Offset(i.toFloat(), 0f), Offset(i.toFloat(), 512f))
                    else listOf(Offset(0f, i.toFloat()), Offset(512f, i.toFloat()))
                draw(c, p, width = inkWidth)
            }
            draw(c, listOf(Offset(80f, 256f), Offset(432f, 256f)), PenType.PixelEraser, 12f)
            val result = bitmap(c.strokeList)
            try {
                for (x in 110..400) for (y in 230..280) {
                    val distance = abs(y + 0.5f - 256f)
                    val alpha = result.getPixel(x, y) ushr 24
                    if (distance < 5f) assertEquals(0, alpha)
                    if (distance > 7f) assertEquals(255, alpha)
                }
            } finally { result.recycle() }
        }
    }

    @Test fun translucentOverlapsAndShapesAreClearedWithoutChangingOtherPixels() {
        val c = DrawController(PenConfig())
        draw(c, listOf(Offset(50f, 50f), Offset(460f, 460f)), width = 90f, alpha = 0.3f)
        draw(c, listOf(Offset(50f, 460f), Offset(460f, 50f)), width = 55f, alpha = 0.6f)
        draw(c, listOf(Offset(100f, 100f), Offset(400f, 400f)), PenType.Rectangle, 34f)
        draw(c, listOf(Offset(80f, 80f), Offset(430f, 430f)), PenType.Ellipse, 21f, alpha = 0.7f)
        assertMaskMatchesPen(c, listOf(Offset(256f, 30f), Offset(100f, 220f), Offset(400f, 320f), Offset(256f, 480f)), 9f)
    }

    @Test fun zoomAndTapUseTheSameGeometryAsPen() {
        for (zoom in listOf(0.75f, 2f)) {
            assertMaskMatchesPen(solid(), listOf(Offset(80f, 100f), Offset(180f, 200f), Offset(230f, 70f)), 7f, zoom)
            assertMaskMatchesPen(solid(), listOf(Offset(160f, 160f)), 20f, zoom)
        }
    }

    @Test fun undoRedoRestoresPixelsAndNewInkCanCoverAnErasedArea() {
        val c = solid()
        val before = bitmap(c.strokeList)
        val path = listOf(Offset(90f, 250f), Offset(180f, 120f), Offset(290f, 380f), Offset(420f, 250f))
        draw(c, path, PenType.PixelEraser, 24f)
        val after = bitmap(c.strokeList)
        save("solid-before", before); save("solid-after", after)
        c.undo()
        val undone = bitmap(c.strokeList)
        assertArrayEquals(pixels(before), pixels(undone))
        assertFalse(c.canUndo.value)
        c.redo()
        val redone = bitmap(c.strokeList)
        assertArrayEquals(pixels(after), pixels(redone))
        draw(c, path, width = 6f, color = Color.Blue)
        val over = bitmap(c.strokeList)
        assertEquals(android.graphics.Color.BLUE, over.getPixel(90, 250))
        save("new-ink", over)
        c.undo()
        val undoneNew = bitmap(c.strokeList)
        assertArrayEquals(pixels(after), pixels(undoneNew))
        listOf(before, after, undone, redone, over, undoneNew).forEach(Bitmap::recycle)
    }

    @Test fun clearIsConfinedToInkLayerAndDoesNotPunchThroughParentBackground() {
        val c = solid()
        draw(c, listOf(Offset(80f, 250f), Offset(430f, 250f)), PenType.PixelEraser, 30f)
        val result = bitmap(c.strokeList, background = android.graphics.Color.GREEN)
        try {
            assertEquals(android.graphics.Color.GREEN, result.getPixel(250, 250))
            assertEquals(android.graphics.Color.BLACK, result.getPixel(250, 200))
        } finally { result.recycle() }
    }

    @Test fun strokeEraserUndoKeepsTheOriginalMaskOrder() {
        val c = DrawController(PenConfig())
        draw(c, listOf(Offset(50f, 250f), Offset(460f, 250f)), width = 40f)
        draw(c, listOf(Offset(250f, 150f), Offset(250f, 350f)), PenType.PixelEraser, 12f)
        val before = bitmap(c.strokeList)
        draw(c, listOf(Offset(100f, 250f)), PenType.StrokeEraser, 10f)
        c.undo()
        val after = bitmap(c.strokeList)
        try { assertArrayEquals(pixels(before), pixels(after)) }
        finally { before.recycle(); after.recycle() }
    }
}
