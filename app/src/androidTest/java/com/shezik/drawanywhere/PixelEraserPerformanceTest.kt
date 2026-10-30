package com.shezik.drawanywhere

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Debug
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import com.shezik.drawanywhere.drawing.InkRenderer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.math.sin

/** Offscreen workload: does not read or modify the user's canvas or preferences. */
class PixelEraserPerformanceTest {
    @Test fun repeatedLongSweepsAndRendering() {
        val controller = DrawController(PenConfig(width = 4f))
        repeat(100) { row ->
            repeat(128) { p ->
                val point = Offset(80f + p * 13f, 100f + row * 20f + sin(p * 0.21f) * 9f)
                if (p == 0) controller.createStroke(point) else controller.updateLatestStroke(point)
            }
            controller.finishStroke()
        }
        controller.setPenConfig(PenConfig(penType = PenType.PixelEraser, width = 24f))
        val bitmap = Bitmap.createBitmap(1848, 2400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val label = InstrumentationRegistry.getArguments().getString("bench_label") ?: "current"
        require(label.matches(Regex("[a-z]+")))
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "pixel-eraser-$label.json")
        val samples = JSONArray()
        val rounds = InstrumentationRegistry.getArguments().getString("bench_rounds")?.toInt() ?: 12
        val report = JSONObject().put("workload", "100 strokes x 128 points; $rounds long sweeps; 3 offscreen renders/sweep")
            .put("label", label).put("samples", samples)
        var notifications = 0
        controller.onStrokesChanged = { notifications++ }
        val totalStart = System.nanoTime()
        try {
            for (round in 0 until rounds) {
                val before = controller.strokeList.toList()
                val allocatedBefore = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0L
                val notifyBefore = notifications
                val start = System.nanoTime()
                val x = 120f + (round % 6) * 220f
                controller.createStroke(Offset(x, 30f))
                controller.updateLatestStroke(Offset(x + 450f, 2250f))
                controller.finishStroke()
                val erased = System.nanoTime()
                val renders = JSONArray()
                repeat(3) {
                    bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                    val renderStart = System.nanoTime()
                    InkRenderer.render(canvas, controller.strokeList, paint)
                    renders.put((System.nanoTime() - renderStart) / 1_000_000.0)
                }
                val allocatedAfter = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0L
                val after = controller.strokeList.toList()
                samples.put(JSONObject().put("round", round).put("erase_ms", (erased - start) / 1_000_000.0)
                    .put("render_ms", renders).put("strokes", after.size)
                    .put("points", after.sumOf { it.points.size })
                    .put("ink_strokes", after.count { !it.penType.isEraser })
                    .put("eraser_paths", after.count { it.penType == PenType.PixelEraser })
                    .put("notifications", notifications - notifyBefore)
                    .put("allocated_bytes", allocatedAfter - allocatedBefore))
                output.writeText(report.toString(2), Charsets.UTF_8)
                if (before != after) {
                    controller.undo()
                    assertEquals(before, controller.strokeList)
                    controller.redo()
                    assertEquals(after, controller.strokeList)
                }
                // Bound the baseline run if the old implementation stalls badly.
                if (System.nanoTime() - totalStart > 30_000_000_000L) break
            }
        } finally {
            bitmap.recycle()
        }
    }
}
