package com.shezik.drawanywhere

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.*
import com.shezik.drawanywhere.view.canvas.CanvasTouchHandler
import org.junit.Assert.*
import org.junit.Test

class StylusInputTest {
    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
    private fun setup(fingers: Boolean = false): Triple<DrawController, DrawViewModel, CanvasTouchHandler> {
        val controller = DrawController(PenConfig())
        val vm = DrawViewModel(controller,
            PreferencesManager(InstrumentationRegistry.getInstrumentation().targetContext),
            UiState(fingerDrawingEnabled = fingers), ServiceState(), {})
        val handler = CanvasTouchHandler(vm, {}, fingerDrawingEnabled = { vm.uiState.value.fingerDrawingEnabled })
        return Triple(controller, vm, handler)
    }

    private fun send(handler: CanvasTouchHandler, action: Int, x: Float, y: Float,
                     button: Int = 0, tool: Int = MotionEvent.TOOL_TYPE_STYLUS) {
        val event = MotionEvent.obtain(100L, 200L, action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool }),
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f }),
            0, button, 1f, 1f, 0, 0,
            if (tool == MotionEvent.TOOL_TYPE_FINGER) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS, 0)
        try { handler.handleEvent(event) } finally { event.recycle() }
    }

    @Test fun sideButtonCutsLocallyAndReleaseResumesWithoutLifting() = onMain {
        val (controller, vm, handler) = setup()
        vm.startStroke(Offset.Zero, StrokeModifier.None)
        vm.updateStroke(Offset(100f, 0f))
        vm.finishStroke()
        send(handler, MotionEvent.ACTION_DOWN, 50f, -50f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(handler, MotionEvent.ACTION_MOVE, 50f, 50f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        assertEquals(PenType.PixelEraser, vm.uiState.value.currentPenType)
        assertEquals(2, controller.strokeList.size)
        val cut = controller.strokeList.toList()
        send(handler, MotionEvent.ACTION_BUTTON_RELEASE, 50f, 50f)
        assertEquals(PenType.Pen, vm.uiState.value.currentPenType)
        send(handler, MotionEvent.ACTION_MOVE, 80f, 80f)
        send(handler, MotionEvent.ACTION_UP, 80f, 80f)
        assertEquals(3, controller.strokeList.size)
        vm.undo() // resumed ink
        assertEquals(2, controller.strokeList.size)
        vm.undo() // Only the latest operation is retained.
        assertEquals(cut, controller.strokeList)
        vm.redo()
        assertEquals(3, controller.strokeList.size)
    }

    @Test fun sideButtonSweepCanBeUndoneAndRedoneAsOneStep() = onMain {
        val (controller, vm, handler) = setup()
        vm.startStroke(Offset.Zero, StrokeModifier.None)
        vm.updateStroke(Offset(100f, 0f))
        vm.finishStroke()
        val original = controller.strokeList.toList()
        send(handler, MotionEvent.ACTION_DOWN, 50f, -50f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(handler, MotionEvent.ACTION_MOVE, 50f, 50f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(handler, MotionEvent.ACTION_UP, 50f, 50f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        val cut = controller.strokeList.toList()
        assertEquals(2, cut.size)
        vm.undo()
        assertEquals(original, controller.strokeList)
        vm.undo()
        assertEquals(original, controller.strokeList)
        vm.redo()
        assertEquals(cut, controller.strokeList)
    }

    @Test fun buttonStateOnMoveSwitchesToolsInBothDirections() = onMain {
        val (_, vm, handler) = setup()
        send(handler, MotionEvent.ACTION_DOWN, 0f, 0f)
        send(handler, MotionEvent.ACTION_MOVE, 20f, 0f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        assertEquals(PenType.PixelEraser, vm.uiState.value.currentPenType)
        send(handler, MotionEvent.ACTION_MOVE, 40f, 0f)
        assertEquals(PenType.Pen, vm.uiState.value.currentPenType)
        send(handler, MotionEvent.ACTION_UP, 40f, 0f)
    }

    @Test fun fingersAreIgnoredButStylusStillDraws() = onMain {
        val (controller, _, handler) = setup()
        send(handler, MotionEvent.ACTION_DOWN, 10f, 10f, tool = MotionEvent.TOOL_TYPE_FINGER)
        send(handler, MotionEvent.ACTION_UP, 10f, 10f, tool = MotionEvent.TOOL_TYPE_FINGER)
        assertTrue(controller.strokeList.isEmpty())
        send(handler, MotionEvent.ACTION_DOWN, 10f, 10f)
        send(handler, MotionEvent.ACTION_MOVE, 20f, 20f)
        send(handler, MotionEvent.ACTION_UP, 20f, 20f)
        assertEquals(1, controller.strokeList.size)
    }

    @Test fun eyeAndCollapsePreserveCanvasAndInputMode() = onMain {
        val (_, vm, _) = setup()
        vm.toggleFirstDrawer()
        assertFalse(vm.uiState.value.firstDrawerOpen)
        assertTrue(vm.uiState.value.canvasVisible)
        assertFalse(vm.uiState.value.fingerDrawingEnabled)
        vm.toggleFirstDrawer()
        vm.toggleToolbarCollapsed()
        assertTrue(vm.uiState.value.toolbarCollapsed)
        assertTrue(vm.uiState.value.canvasVisible)
        vm.toggleToolbarCollapsed()
        assertFalse(vm.uiState.value.toolbarCollapsed)
    }
}
