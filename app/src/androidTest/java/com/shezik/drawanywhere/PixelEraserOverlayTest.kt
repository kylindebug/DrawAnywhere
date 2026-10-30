package com.shezik.drawanywhere

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.*
import com.shezik.drawanywhere.view.canvas.NativeDrawCanvasView
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real GPU overlay, without ActivityScenario or changes to app preferences. */
class PixelEraserOverlayTest {
    @Test fun gpuOverlayCutsUniformWidthAndRevealsParentSurface() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wm = context.getSystemService(WindowManager::class.java)
        val controller = DrawController(PenConfig(width = 40f, color = Color.Black))
        for (y in 20..490 step 10) {
            controller.createStroke(Offset(10f, y.toFloat()))
            controller.updateLatestStroke(Offset(500f, y.toFloat()))
            controller.finishStroke()
        }
        controller.setPenConfig(PenConfig(penType = PenType.PixelEraser, width = 12f))
        controller.createStroke(Offset(80f, 250f))
        controller.updateLatestStroke(Offset(430f, 250f))
        controller.finishStroke()
        lateinit var root: FrameLayout
        lateinit var view: NativeDrawCanvasView
        var rootCreated = false
        val drawn = CountDownLatch(1)
        val location = IntArray(2)
        try {
            instrumentation.runOnMainSync {
                val vm = DrawViewModel(controller, PreferencesManager(context), UiState(), ServiceState(), {})
                view = NativeDrawCanvasView(context, controller, vm)
                root = FrameLayout(context).apply {
                    setBackgroundColor(android.graphics.Color.WHITE)
                    addView(view, FrameLayout.LayoutParams(512, 512))
                }
                rootCreated = true
                view.viewTreeObserver.addOnDrawListener { drawn.countDown() }
                val params = WindowManager.LayoutParams(512, 512,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT; x = 100; y = 300; alpha = 1f }
                wm.addView(root, params)
            }
            assertTrue("Overlay did not draw", drawn.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertTrue("Production canvas must use the GPU", view.isHardwareAccelerated)
                view.getLocationOnScreen(location)
            }
            var capture: Bitmap? = null
            for (attempt in 0 until 20) {
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                if (screenshot != null) {
                    if (attempt == 19) {
                        File(context.cacheDir, "precision-gpu-state.txt").writeText(
                            "location=${location.toList()} rootAlpha=${root.alpha} viewAlpha=${view.alpha} " +
                                "erase=${screenshot.getPixel(location[0] + 200, location[1] + 250).toUInt().toString(16)} " +
                                "ink=${screenshot.getPixel(location[0] + 200, location[1] + 220).toUInt().toString(16)}\n")
                        val fd = instrumentation.uiAutomation.executeShellCommand("dumpsys window windows")
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { reader ->
                            val own = reader.readText().split(Regex("(?=  Window #)")).filter {
                                it.contains("package=com.shezik.drawanywhere ") }
                            File(context.cacheDir, "precision-gpu-state.txt").appendText(own.joinToString("\n"))
                        }
                        val diagnostic = Bitmap.createBitmap(screenshot, location[0], location[1], 512, 512)
                        File(context.cacheDir, "precision-gpu-diagnostic.png").outputStream().use {
                            diagnostic.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        diagnostic.recycle()
                    }
                    if (screenshot.getPixel(location[0] + 200, location[1] + 250) == android.graphics.Color.WHITE &&
                        screenshot.getPixel(location[0] + 200, location[1] + 220) == android.graphics.Color.BLACK) {
                        capture = Bitmap.createBitmap(screenshot, location[0], location[1], 512, 512)
                        screenshot.recycle()
                        break
                    }
                    screenshot.recycle()
                }
                Thread.sleep(50)
            }
            val result = checkNotNull(capture) { "GPU overlay was not visible in the screenshot" }
            try {
                for (x in 110..400) {
                    for (y in 246..253) assertEquals(android.graphics.Color.WHITE, result.getPixel(x, y))
                    assertEquals(android.graphics.Color.BLACK, result.getPixel(x, 240))
                    assertEquals(android.graphics.Color.BLACK, result.getPixel(x, 260))
                }
                File(context.cacheDir, "precision-gpu-overlay.png").outputStream().use {
                    result.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { result.recycle() }
        } finally {
            instrumentation.runOnMainSync { if (rootCreated && root.isAttachedToWindow) wm.removeViewImmediate(root) }
        }
    }
}
