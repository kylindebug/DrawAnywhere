package com.shezik.drawanywhere

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.view.canvas.NativeDrawCanvasView
import org.junit.Assert.*
import org.junit.Test

class HoverPreviewTest {
    @Test fun hoveringSideButtonShowsHollowEraserAtItsOwnWidth() {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.runOnMainSync {
            val c = DrawController(PenConfig())
            val vm = DrawViewModel(c, PreferencesManager(inst.targetContext), UiState(), ServiceState(), {})
            val view = NativeDrawCanvasView(inst.targetContext, c, vm)
            view.layout(0, 0, 200, 200)
            val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            fun preview(action: Int, button: Int) {
                val event = MotionEvent.obtain(100L, 200L, action, 1,
                    arrayOf(MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_STYLUS }),
                    arrayOf(MotionEvent.PointerCoords().apply { x=100f; y=100f }),
                    0, button, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
                try { view.onGenericMotionEvent(event) } finally { event.recycle() }
                bitmap.eraseColor(0)
                view.draw(Canvas(bitmap))
            }
            try {
                preview(MotionEvent.ACTION_HOVER_ENTER, 0)
                assertTrue(bitmap.getPixel(100, 100) ushr 24 > 0)
                assertEquals(0, bitmap.getPixel(113, 100))
                preview(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_STYLUS_PRIMARY)
                assertEquals("Eraser has no red fill", 0, bitmap.getPixel(100, 100))
                assertTrue("Eraser uses its 30px width", bitmap.getPixel(113, 100) ushr 24 > 0)
                preview(MotionEvent.ACTION_BUTTON_RELEASE, 0)
                assertTrue(bitmap.getPixel(100, 100) ushr 24 > 0)
                assertEquals(0, bitmap.getPixel(113, 100))
                assertTrue(c.strokeList.isEmpty())
            } finally { bitmap.recycle() }
        }
    }
}
