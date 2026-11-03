package com.shezik.drawanywhere

import android.os.Debug
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.drawing.PenRenderer
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import kotlin.math.sin

/** Same input samples/frame cadence on both revisions; excludes GPU raster time. */
class LongStrokePerformanceTest {
    @Test fun longPenAndEraserPaths() {
        val samples = JSONArray()
        for (type in listOf(PenType.Pen, PenType.PixelEraser)) repeat(5) { round ->
            val c = DrawController(PenConfig(width = 80f))
            c.createStroke(Offset.Zero)
            c.updateLatestStroke(Offset(2000f, 1000f))
            c.finishStroke()
            c.setPenConfig(PenConfig(type, width = 20f))
            val allocations = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
            val start = System.nanoTime()
            c.createStroke(Offset.Zero)
            repeat(6000) { i ->
                c.updateLatestStroke(Offset(i / 3f, 500f + sin(i * .03f) * 100f))
                if (i % 4 == 0) PenRenderer.pathFor(c.strokeList.last())
            }
            c.finishStroke()
            val elapsed = (System.nanoTime() - start) / 1e6
            val bytes = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong() - allocations
            val clearStart = System.nanoTime()
            c.clearStrokes()
            samples.put(JSONObject().put("type", type.name).put("round", round)
                .put("input_and_path_ms", elapsed).put("allocated_bytes", bytes)
                .put("clear_ms", (System.nanoTime() - clearStart) / 1e6))
        }
        val label = InstrumentationRegistry.getArguments().getString("bench_label") ?: "current"
        require(label.matches(Regex("[a-z]+")))
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "long-stroke-$label.json").writeText(samples.toString(2), Charsets.UTF_8)
    }
}
