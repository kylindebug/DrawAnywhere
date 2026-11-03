package com.shezik.drawanywhere

import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.StrokeModifier
import com.shezik.drawanywhere.view.ToolbarLifecycleOwner
import com.shezik.drawanywhere.view.canvas.NativeDrawCanvasView
import com.shezik.drawanywhere.view.canvas.CanvasInputWindow
import com.shezik.drawanywhere.view.canvas.canvasInputState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.shezik.drawanywhere.view.toolbar.DrawToolbar
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real WindowManager/InputDispatcher routing, rather than direct button callbacks. */
class PalmToolbarWindowTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun fingerCanClearWhilePalmRemainsDown() = checkClear(MotionEvent.TOOL_TYPE_FINGER)
    @Test fun penCanClearWhilePalmRemainsDown() = checkClear(MotionEvent.TOOL_TYPE_STYLUS)
    @Test fun fingerCanToggleAndClearRepeatedlyWithPalmHeld() = checkClear(MotionEvent.TOOL_TYPE_FINGER, 20)
    @Test fun penCanToggleAndClearWhenPalmStartsInPassthrough() = checkClear(MotionEvent.TOOL_TYPE_STYLUS, 20, true)

    private fun checkClear(buttonTool: Int, cycles: Int = 0, initiallyPassthrough: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertFalse("Unlock the device before window-input tests",
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val wm = context.getSystemService(WindowManager::class.java)
        val c = DrawController(PenConfig())
        lateinit var vm: DrawViewModel
        lateinit var canvas: NativeDrawCanvasView
        lateinit var toolbar: ComposeView
        lateinit var underlying: View
        val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        val lifecycle = ToolbarLifecycleOwner()
        val t = SystemClock.uptimeMillis()
        fun send(action: Int, withButton: Boolean, button: Offset = Offset.Zero) {
            val tools = if (withButton) listOf(MotionEvent.TOOL_TYPE_FINGER, buttonTool)
                else listOf(MotionEvent.TOOL_TYPE_FINGER)
            val positions = listOf(Offset(1000f, 1000f), button)
            val event = MotionEvent.obtain(t, SystemClock.uptimeMillis(), action, tools.size,
                tools.mapIndexed { i, tool -> MotionEvent.PointerProperties().apply { id=i; toolType=tool } }.toTypedArray(),
                tools.indices.map { i -> MotionEvent.PointerCoords().apply {
                    x=positions[i].x; y=positions[i].y; pressure=1f
                } }.toTypedArray(), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        instrumentation.runOnMainSync {
            val initial = UiState(fingerDrawingEnabled=false, canvasPassthrough=initiallyPassthrough)
            vm = DrawViewModel(c, PreferencesManager(context), initial, ServiceState(), {})
            canvas = NativeDrawCanvasView(context, c, vm)
            lifecycle.start()
            toolbar = ComposeView(context)
            lifecycle.attachTo(toolbar)
            toolbar.setContent { DrawToolbar(vm) }
            fun params(width: Int, height: Int) = WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, MainService.OVERLAY_INPUT_FLAGS,
                PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.LEFT }
            underlying = View(context).apply { setOnTouchListener { _, _ -> true } }
            wm.addView(underlying, params(-1, -1))
            val canvasParams = params(-1, -1)
            val inputWindow = CanvasInputWindow(wm, canvas, canvasParams, initial.canvasInputState)
            wm.addView(canvas, canvasParams)
            wm.addView(toolbar, params(-2, -2).apply { x=300; y=300 })
            scope.launch { vm.uiState.map { it.canvasInputState }.distinctUntilChanged().collect(inputWindow::apply) }
            vm.startStroke(Offset(700f, 700f), StrokeModifier.None)
            vm.updateStroke(Offset(800f, 800f))
            vm.finishStroke()
        }
        try {
            val node = rule.onNodeWithContentDescription(context.getString(R.string.clear_canvas))
                .assertIsEnabled().fetchSemanticsNode()
            val position = IntArray(2)
            instrumentation.runOnMainSync { toolbar.getLocationOnScreen(position) }
            val button = node.boundsInRoot.center + Offset(position[0].toFloat(), position[1].toFloat())
            val mode = rule.onNodeWithContentDescription(context.getString(
                if (initiallyPassthrough) R.string.pen_disabled else R.string.pen_enabled))
                .fetchSemanticsNode().boundsInRoot.center + Offset(position[0].toFloat(), position[1].toFloat())
            fun tap(point: Offset) {
                send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), true, point)
                send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), true, point)
            }
            send(MotionEvent.ACTION_DOWN, false)
            repeat(cycles) {
                val before = vm.uiState.value.canvasPassthrough
                tap(mode)
                rule.runOnIdle { assertEquals(!before, vm.uiState.value.canvasPassthrough) }
                tap(mode)
                rule.runOnIdle { assertEquals(before, vm.uiState.value.canvasPassthrough) }
                tap(button)
                rule.runOnIdle {
                    assertTrue("Clear while palm held, cycle $it", c.strokeList.isEmpty())
                    vm.startStroke(Offset(700f, 700f), StrokeModifier.None)
                    vm.updateStroke(Offset(800f, 800f)); vm.finishStroke()
                }
            }
            tap(button)
            rule.runOnIdle { assertTrue("Clear must run before palm lifts", c.strokeList.isEmpty()) }
        } finally {
            send(MotionEvent.ACTION_UP, false)
            instrumentation.runOnMainSync {
                scope.cancel()
                wm.removeViewImmediate(toolbar)
                wm.removeViewImmediate(canvas)
                wm.removeViewImmediate(underlying)
                lifecycle.stop()
            }
        }
    }
}
